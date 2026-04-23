package hcmute.edu.vn.doinbot.agent.integration.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ExternalEvent {

    private final String id;
    private final String title;
    private final long startMillis;
    private final long endMillis;
    private final String source;
    private final boolean hardConstraint;
    private final String location;

    public ExternalEvent(String id,
                         String title,
                         long startMillis,
                         long endMillis,
                         String source,
                         boolean hardConstraint,
                         String location) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.startMillis = startMillis;
        this.endMillis = Math.max(startMillis, endMillis);
        this.source = source == null ? "" : source;
        this.hardConstraint = hardConstraint;
        this.location = location == null ? "" : location;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public String getSource() {
        return source;
    }

    public boolean isHardConstraint() {
        return hardConstraint;
    }

    public String getLocation() {
        return location;
    }

    public boolean overlaps(long fromMillis, long toMillis) {
        return startMillis < toMillis && endMillis > fromMillis;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        safePut(json, "id", id);
        safePut(json, "title", title);
        safePut(json, "startMillis", startMillis);
        safePut(json, "endMillis", endMillis);
        safePut(json, "source", source);
        safePut(json, "hardConstraint", hardConstraint);
        safePut(json, "location", location);
        return json;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}