package hcmute.edu.vn.doinbot.agent.core;

import org.json.JSONException;
import org.json.JSONObject;

public class AgentEvent {

    public static final String TYPE_TASK_CREATED = "TASK_CREATED";
    public static final String TYPE_TASK_UPDATED = "TASK_UPDATED";
    public static final String TYPE_TASK_COMPLETED = "TASK_COMPLETED";
    public static final String TYPE_TASK_DELETED = "TASK_DELETED";
    public static final String TYPE_TASK_RESCHEDULED = "TASK_RESCHEDULED";
    public static final String TYPE_EXTERNAL_DEADLINES_SYNCED = "EXTERNAL_DEADLINES_SYNCED";
    public static final String TYPE_CONTEXT_REFRESHED = "CONTEXT_REFRESHED";
    public static final String TYPE_PROACTIVE_TICK = "PROACTIVE_TICK";
    public static final String TYPE_DAILY_REVIEW_TRIGGERED = "DAILY_REVIEW_TRIGGERED";
    public static final String TYPE_SUGGESTION_CREATED = "SUGGESTION_CREATED";

    private final String type;
    private final String source;
    private final long timestampMillis;
    private final JSONObject payload;

    public AgentEvent(String type, String source, long timestampMillis, JSONObject payload) {
        this.type = type == null ? "UNKNOWN" : type;
        this.source = source == null ? "UNKNOWN" : source;
        this.timestampMillis = timestampMillis;
        this.payload = payload == null ? new JSONObject() : payload;
    }

    public static AgentEvent now(String type, String source, JSONObject payload) {
        return new AgentEvent(type, source, System.currentTimeMillis(), payload);
    }

    public String getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    public JSONObject getPayload() {
        return payload;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        safePut(json, "type", type);
        safePut(json, "source", source);
        safePut(json, "timestampMillis", timestampMillis);
        safePut(json, "payload", payload);
        return json;
    }

    private static void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
