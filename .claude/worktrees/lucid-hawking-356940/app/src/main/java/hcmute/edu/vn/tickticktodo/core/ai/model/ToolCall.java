package hcmute.edu.vn.doinbot.core.ai.model;

import org.json.JSONObject;

import hcmute.edu.vn.doinbot.agent.AgentResponseEnvelope;

@Deprecated
public class ToolCall extends hcmute.edu.vn.doinbot.ai.llm.model.ToolCall {

    public ToolCall(String callId, String toolName, JSONObject arguments, String replyHint) {
        super(callId, toolName, arguments, replyHint);
    }

    public static ToolCall fromEnvelope(AgentResponseEnvelope envelope) {
        hcmute.edu.vn.doinbot.ai.llm.model.ToolCall delegated =
                hcmute.edu.vn.doinbot.ai.llm.model.ToolCall.fromEnvelope(envelope);
        return new ToolCall(
                delegated.getCallId(),
                delegated.getToolName(),
                delegated.getArguments(),
                delegated.getReplyHint()
        );
    }
}
