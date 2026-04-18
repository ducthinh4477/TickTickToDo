package hcmute.edu.vn.tickticktodo.agent.orchestrator;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.agent.AgentAction;
import hcmute.edu.vn.tickticktodo.agent.AgentContextAssembler;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseParser;
import hcmute.edu.vn.tickticktodo.agent.AgentToolRegistry;
import hcmute.edu.vn.tickticktodo.core.ai.LlmProvider;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;
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

    private final LlmProvider llmProvider;
    private final AgentContextAssembler contextAssembler;
    private final AgentResponseParser responseParser;
    private final PromptTemplateManager promptTemplateManager;
    private final ToolExecutionBridge toolExecutionBridge;
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
                             LlmProvider llmProvider,
                             AgentContextAssembler contextAssembler,
                             AgentToolRegistry toolRegistry,
                             AgentResponseParser responseParser) {
        this.llmProvider = llmProvider;
        this.contextAssembler = contextAssembler;
        this.responseParser = responseParser;
        this.promptTemplateManager = new PromptTemplateManager();
        this.toolExecutionBridge = new ToolExecutionBridge(application, toolRegistry);
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
        postDebugTrace(callback, "USER_MESSAGE", userMessage);

        workerExecutor.execute(() -> {
            try {
                String contextBlock = contextAssembler.buildTieredContextBlock(userMessage);
                String toolSchemas = toolExecutionBridge.getToolSchemas().toString();
                String prompt = buildPrompt(userMessage, contextBlock, toolSchemas);

                postDebugTrace(callback, "CONTEXT", contextBlock);
                postDebugTrace(callback, "TOOL_SCHEMAS", toolSchemas);
                postDebugTrace(callback, "PROMPT", prompt);

                llmProvider.generateResponse(prompt, new LlmProvider.ResponseCallback() {
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
            } catch (Exception e) {
                postDebugTrace(callback, "ORCHESTRATOR_PREP_ERROR", e.getMessage());
                postError(callback, "Không thể chuẩn bị ngữ cảnh AI lúc này.");
            }
        });
    }

    public JSONArray getToolSchemas() {
        return toolExecutionBridge.getToolSchemas();
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
            String reply = envelope.getReply().trim();
            if (result != null && result.isSuccess() && reply.length() < 24) {
                postAssistantReply(callback, reply + "\n" + renderToolResultSummary(result));
            } else {
                postAssistantReply(callback, reply);
            }
        } else {
            postAssistantReply(callback, renderToolResultSummary(result));
        }
    }

    private ToolResult dispatchToolCall(ToolCall call) {
        return toolExecutionBridge.dispatchToolCall(call);
    }

    private String buildPrompt(String userMessage, String contextBlock, String toolsBlock) {
        return promptTemplateManager.buildPrompt(userMessage, contextBlock, toolsBlock);
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
        return toolExecutionBridge.renderToolResultSummary(result);
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
