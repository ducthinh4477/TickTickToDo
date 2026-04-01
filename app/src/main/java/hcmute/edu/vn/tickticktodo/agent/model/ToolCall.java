package hcmute.edu.vn.tickticktodo.agent.model;

import org.json.JSONObject;
import org.json.JSONException;

import java.util.Locale;
import java.util.UUID;

import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;

public class ToolCall {

    private final String callId;
    private final String toolName;
    private final JSONObject arguments;
    private final String replyHint;

    public ToolCall(String callId, String toolName, JSONObject arguments, String replyHint) {
        this.callId = callId == null || callId.trim().isEmpty()
                ? UUID.randomUUID().toString()
                : callId.trim();
        this.toolName = normalize(toolName);
        this.arguments = arguments == null ? new JSONObject() : arguments;
        this.replyHint = replyHint == null ? "" : replyHint;
    }

    public static ToolCall fromEnvelope(AgentResponseEnvelope envelope) {
        return new ToolCall(
                UUID.randomUUID().toString(),
                envelope == null ? "" : envelope.getAction(),
                envelope == null ? null : envelope.getPayload(),
                envelope == null ? "" : envelope.getReply()
        );
    }

    public String getCallId() {
        return callId;
    }

    public String getToolName() {
        return toolName;
    }

    public JSONObject getArguments() {
        return arguments;
    }

    public String getReplyHint() {
        return replyHint;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        safePut(json, "callId", callId);
        safePut(json, "toolName", toolName);
        safePut(json, "arguments", arguments);
        safePut(json, "replyHint", replyHint);
        return json;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }

    private static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
