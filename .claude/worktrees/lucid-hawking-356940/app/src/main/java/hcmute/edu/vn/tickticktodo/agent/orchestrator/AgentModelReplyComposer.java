package hcmute.edu.vn.doinbot.agent.orchestrator;

import android.text.TextUtils;

import hcmute.edu.vn.doinbot.agent.AgentResponseEnvelope;
import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;

class AgentModelReplyComposer {

    private static final int SHORT_REPLY_THRESHOLD = 24;

    String composeChatReply(AgentResponseEnvelope envelope) {
        return TextUtils.isEmpty(envelope.getReply())
                ? envelope.getRawText()
                : envelope.getReply();
    }

    String resolveToolFlowReplyOrNull(AgentResponseEnvelope envelope) {
        if (TextUtils.isEmpty(envelope.getReply())) {
            return null;
        }
        return envelope.getReply().trim();
    }

    boolean shouldAppendToolResultSummary(String reply, ToolResult result) {
        return result != null
                && result.isSuccess()
                && reply != null
                && reply.length() < SHORT_REPLY_THRESHOLD;
    }

    String appendToolResultSummary(String reply, String toolResultSummary) {
        return reply + "\n" + toolResultSummary;
    }
}