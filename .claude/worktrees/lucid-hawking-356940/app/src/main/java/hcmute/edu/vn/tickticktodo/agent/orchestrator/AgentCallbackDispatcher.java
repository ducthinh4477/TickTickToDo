package hcmute.edu.vn.doinbot.agent.orchestrator;

import android.os.Handler;

import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;

class AgentCallbackDispatcher {

    private final Handler mainHandler;

    AgentCallbackDispatcher(Handler mainHandler) {
        this.mainHandler = mainHandler;
    }

    void postAssistantReply(AgentOrchestrator.Callback callback, String replyText) {
        mainHandler.post(() -> callback.onAssistantReply(replyText));
    }

    void postToolResult(AgentOrchestrator.Callback callback, ToolResult toolResult) {
        mainHandler.post(() -> callback.onToolResult(toolResult));
    }

    void postError(AgentOrchestrator.Callback callback, String errorMessage) {
        mainHandler.post(() -> callback.onError(errorMessage));
    }

    void postDebugTrace(AgentOrchestrator.Callback callback, String stage, String payload) {
        mainHandler.post(() -> callback.onDebugTrace(stage, payload));
    }
}