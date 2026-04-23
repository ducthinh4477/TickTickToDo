package hcmute.edu.vn.tickticktodo.agent.orchestrator;

import org.json.JSONException;
import org.json.JSONObject;

import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;

class AgentTraceFormatter {

    private static final int MAX_TRACE_CHARS = 2500;

    JSONObject envelopeToTraceJson(AgentResponseEnvelope envelope) {
        JSONObject json = new JSONObject();
        safePut(json, "action", envelope == null ? "" : envelope.getAction());
        safePut(json, "payload", envelope == null ? new JSONObject() : envelope.getPayload());
        safePut(json, "reply", envelope == null ? "" : envelope.getReply());
        safePut(json, "structured", envelope != null && envelope.isStructured());
        safePut(json, "rawText", envelope == null ? "" : envelope.getRawText());
        return json;
    }

    String trimTrace(String text) {
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