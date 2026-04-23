package hcmute.edu.vn.doinbot.agent.orchestrator;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.doinbot.ai.agent.AgentAction;
import hcmute.edu.vn.doinbot.agent.AgentContextAssembler;
import hcmute.edu.vn.doinbot.agent.AgentResponseEnvelope;
import hcmute.edu.vn.doinbot.agent.AgentResponseParser;
import hcmute.edu.vn.doinbot.agent.AgentToolRegistry;
import hcmute.edu.vn.doinbot.core.ai.LlmProvider;
import hcmute.edu.vn.doinbot.core.ai.model.ToolCall;
import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;
import hcmute.edu.vn.doinbot.helper.GeminiManager;

public class AgentOrchestrator {

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
    private final AgentTraceFormatter traceFormatter;
    private final AgentCallbackDispatcher callbackDispatcher;
    private final AgentRequestAssemblyHelper requestAssemblyHelper;
    private final AgentModelReplyComposer modelReplyComposer;
    private final ExecutorService workerExecutor;

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
        this.traceFormatter = new AgentTraceFormatter();
        this.callbackDispatcher = new AgentCallbackDispatcher(new Handler(Looper.getMainLooper()));
        this.requestAssemblyHelper = new AgentRequestAssemblyHelper(
            contextAssembler,
            toolExecutionBridge,
            promptTemplateManager
        );
        this.modelReplyComposer = new AgentModelReplyComposer();
        this.workerExecutor = Executors.newSingleThreadExecutor();
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
                AgentRequestAssemblyHelper.PreparedRequest preparedRequest =
                        requestAssemblyHelper.assemble(userMessage);

                postDebugTrace(callback, "CONTEXT", preparedRequest.getContextBlock());
                postDebugTrace(callback, "TOOL_SCHEMAS", preparedRequest.getToolSchemas());
                postDebugTrace(callback, "PROMPT", preparedRequest.getPrompt());

                llmProvider.generateResponse(preparedRequest.getPrompt(), new LlmProvider.ResponseCallback() {
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
        postDebugTrace(callback, "PARSED_ENVELOPE", traceFormatter.envelopeToTraceJson(envelope).toString());

        if (AgentAction.CHAT.equals(envelope.getAction())) {
            postAssistantReply(callback, modelReplyComposer.composeChatReply(envelope));
            return;
        }

        ToolCall call = ToolCall.fromEnvelope(envelope);
        postDebugTrace(callback, "TOOL_CALL", call.toJson().toString());
        ToolResult result = dispatchToolCall(call);
        postDebugTrace(callback, "TOOL_RESULT", result.toJson().toString());
        postToolResult(callback, result);

        String reply = modelReplyComposer.resolveToolFlowReplyOrNull(envelope);
        if (reply != null) {
            if (modelReplyComposer.shouldAppendToolResultSummary(reply, result)) {
                postAssistantReply(callback,
                        modelReplyComposer.appendToolResultSummary(reply, renderToolResultSummary(result)));
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

    private String renderToolResultSummary(ToolResult result) {
        return toolExecutionBridge.renderToolResultSummary(result);
    }

    private void postAssistantReply(Callback callback, String replyText) {
        callbackDispatcher.postAssistantReply(callback, replyText);
    }

    private void postToolResult(Callback callback, ToolResult toolResult) {
        callbackDispatcher.postToolResult(callback, toolResult);
    }

    private void postError(Callback callback, String errorMessage) {
        callbackDispatcher.postError(callback, errorMessage);
    }

    private void postDebugTrace(Callback callback, String stage, String payload) {
        callbackDispatcher.postDebugTrace(callback, stage, traceFormatter.trimTrace(payload));
    }
}
