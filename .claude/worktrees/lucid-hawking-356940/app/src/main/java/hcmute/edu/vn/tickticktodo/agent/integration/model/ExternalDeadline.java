package hcmute.edu.vn.doinbot.agent.integration.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ExternalDeadline {

    private final String id;
    private final String title;
    private final long dueMillis;
    private final String source;
    private final String metadata;

    public ExternalDeadline(String id,
                            String title,
                            long dueMillis,
                            String source,
                            String metadata) {
        this.id = id == null ? "" : id;
        this.title = title == null ? "" : title;
        this.dueMillis = dueMillis;
        this.source = source == null ? "" : source;
        this.metadata = metadata == null ? "" : metadata;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getDueMillis() {
        return dueMillis;
    }

    public String getSource() {
        return source;
    }

    public String getMetadata() {
        return metadata;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        safePut(json, "id", id);
        safePut(json, "title", title);
        safePut(json, "dueMillis", dueMillis);
        safePut(json, "source", source);
        safePut(json, "metadata", metadata);
        return json;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}