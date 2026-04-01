package hcmute.edu.vn.tickticktodo.agent;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.agent.model.ToolCall;
import hcmute.edu.vn.tickticktodo.agent.model.ToolResult;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;

public class AgentOrchestrator {

    private static final int MAX_TRACE_CHARS = 2500;

    public interface Callback {
        void onAssistantReply(String replyText);

        void onToolResult(ToolResult toolResult);

        void onError(String errorMessage);

        default void onDebugTrace(String stage, String payload) {
        }
    }

    private static final String ORCHESTRATOR_SYSTEM_PROMPT =
            "Bạn là Agent của TickTickToDo. Trả về JSON thuần. " +
            "Nếu cần chạy công cụ, trả schema: {\"action\":\"<TOOL_NAME>\",\"payload\":{...},\"reply\":\"...\"}. " +
            "Nếu chỉ trò chuyện, dùng action=CHAT.";

    private final Application application;
    private final GeminiManager geminiManager;
    private final AgentContextAssembler contextAssembler;
    private final AgentToolRegistry toolRegistry;
    private final AgentResponseParser responseParser;
    private final ExecutorService workerExecutor;
    private final Handler mainHandler;

    public AgentOrchestrator(Application application) {
        this(
                application,
                GeminiManager.getInstance(),
                new AgentContextAssembler(application),
                AgentToolRegistry.withDefaultQueryTools(),
                new AgentResponseParser()
        );
    }

    public AgentOrchestrator(Application application,
                             GeminiManager geminiManager,
                             AgentContextAssembler contextAssembler,
                             AgentToolRegistry toolRegistry,
                             AgentResponseParser responseParser) {
        this.application = application;
        this.geminiManager = geminiManager;
        this.contextAssembler = contextAssembler;
        this.toolRegistry = toolRegistry;
        this.responseParser = responseParser;
        this.workerExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void handleUserMessage(String userMessage, Callback callback) {
        if (callback == null) {
            return;
        }

        if (TextUtils.isEmpty(userMessage)) {
            postError(callback, "Tin nhắn không hợp lệ.");
            return;
        }

        String contextBlock = contextAssembler.buildTieredContextBlock(userMessage);
        String toolSchemas = toolRegistry.getToolSchemas().toString();
        String prompt = buildPrompt(userMessage, contextBlock, toolSchemas);

        postDebugTrace(callback, "USER_MESSAGE", userMessage);
        postDebugTrace(callback, "CONTEXT", contextBlock);
        postDebugTrace(callback, "TOOL_SCHEMAS", toolSchemas);
        postDebugTrace(callback, "PROMPT", prompt);

        geminiManager.generateResponse(prompt, new GeminiManager.ResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                postDebugTrace(callback, "MODEL_RAW", responseText);
                workerExecutor.execute(() -> processModelResponse(responseText, callback));
            }

            @Override
            public void onError(String errorMessage) {
                postDebugTrace(callback, "MODEL_ERROR", errorMessage);
                postError(callback, errorMessage);
            }
        });
    }

    public JSONArray getToolSchemas() {
        return toolRegistry.getToolSchemas();
    }

    private void processModelResponse(String modelText, Callback callback) {
        AgentResponseEnvelope envelope = responseParser.parse(modelText);
        postDebugTrace(callback, "PARSED_ENVELOPE", envelopeToTraceJson(envelope).toString());

        if (AgentAction.CHAT.equals(envelope.getAction())) {
            String reply = TextUtils.isEmpty(envelope.getReply())
                    ? envelope.getRawText()
                    : envelope.getReply();
            postAssistantReply(callback, reply);
            return;
        }

        ToolCall call = ToolCall.fromEnvelope(envelope);
    postDebugTrace(callback, "TOOL_CALL", call.toJson().toString());
        ToolResult result = dispatchToolCall(call);
    postDebugTrace(callback, "TOOL_RESULT", result.toJson().toString());
        postToolResult(callback, result);

        if (!TextUtils.isEmpty(envelope.getReply())) {
            postAssistantReply(callback, envelope.getReply());
        } else {
            postAssistantReply(callback, renderToolResultSummary(result));
        }
    }

    private ToolResult dispatchToolCall(ToolCall call) {
        AgentTool tool = toolRegistry.get(call.getToolName());
        if (tool == null) {
            return ToolResult.failure(
                    call.getCallId(),
                    call.getToolName(),
                    "TOOL_NOT_FOUND",
                    "Không tìm thấy công cụ: " + call.getToolName()
            );
        }

        if (tool.requiresConfirmation(call)) {
            return ToolResult.failure(
                    call.getCallId(),
                    call.getToolName(),
                    "CONFIRMATION_REQUIRED",
                    "Công cụ yêu cầu xác nhận người dùng trước khi chạy."
            );
        }

        try {
            return tool.execute(call, AgentExecutionContext.create(application));
        } catch (Exception e) {
            return ToolResult.failure(
                    call.getCallId(),
                    call.getToolName(),
                    "TOOL_EXECUTION_FAILED",
                    e.getMessage() == null ? "Tool execution failed" : e.getMessage()
            );
        }
    }

    private String buildPrompt(String userMessage, String contextBlock, String toolsBlock) {
        String systemPrompt = ORCHESTRATOR_SYSTEM_PROMPT + "\n[TOOLS]\n" + toolsBlock;
        return AgentPromptContract.buildPrompt(
                systemPrompt,
                contextBlock,
                "(managed-by-orchestrator)",
                userMessage
        );
    }

    private JSONObject envelopeToTraceJson(AgentResponseEnvelope envelope) {
        JSONObject json = new JSONObject();
        safePut(json, "action", envelope == null ? "" : envelope.getAction());
        safePut(json, "payload", envelope == null ? new JSONObject() : envelope.getPayload());
        safePut(json, "reply", envelope == null ? "" : envelope.getReply());
        safePut(json, "structured", envelope != null && envelope.isStructured());
        safePut(json, "rawText", envelope == null ? "" : envelope.getRawText());
        return json;
    }

    private String renderToolResultSummary(ToolResult result) {
        if (result == null) {
            return "Mình chưa có kết quả từ công cụ.";
        }

        if (result.isSuccess()) {
            return "Đã chạy công cụ " + result.getToolName() + " thành công.";
        }

        return "Công cụ " + result.getToolName() + " lỗi: " + result.getErrorMessage();
    }

    private void postAssistantReply(Callback callback, String replyText) {
        mainHandler.post(() -> callback.onAssistantReply(replyText));
    }

    private void postToolResult(Callback callback, ToolResult toolResult) {
        mainHandler.post(() -> callback.onToolResult(toolResult));
    }

    private void postError(Callback callback, String errorMessage) {
        mainHandler.post(() -> callback.onError(errorMessage));
    }

    private void postDebugTrace(Callback callback, String stage, String payload) {
        mainHandler.post(() -> callback.onDebugTrace(stage, trimTrace(payload)));
    }

    private String trimTrace(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_TRACE_CHARS) {
            return text;
        }
        return text.substring(0, MAX_TRACE_CHARS) + "\n...(trace truncated)";
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
