package hcmute.edu.vn.tickticktodo.agent.integration.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SchedulingConstraints {

    private final List<ExternalEvent> busyEvents;
    private final List<ExternalDeadline> externalDeadlines;
    private final HealthSummary healthSummary;

    public SchedulingConstraints(List<ExternalEvent> busyEvents,
                                 List<ExternalDeadline> externalDeadlines,
                                 HealthSummary healthSummary) {
        this.busyEvents = busyEvents == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(busyEvents));
        this.externalDeadlines = externalDeadlines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(externalDeadlines));
        this.healthSummary = healthSummary == null ? HealthSummary.unavailable("UNKNOWN") : healthSummary;
    }

    public List<ExternalEvent> getBusyEvents() {
        return busyEvents;
    }

    public List<ExternalDeadline> getExternalDeadlines() {
        return externalDeadlines;
    }

    public HealthSummary getHealthSummary() {
        return healthSummary;
    }

    public boolean isEmpty() {
        return busyEvents.isEmpty() && externalDeadlines.isEmpty();
    }

    public JSONObject toJson(int maxEvents, int maxDeadlines) {
        JSONObject json = new JSONObject();
        safePut(json, "busyEventCount", busyEvents.size());
        safePut(json, "deadlineCount", externalDeadlines.size());
        safePut(json, "healthSummary", healthSummary.toJson());

        JSONArray events = new JSONArray();
        int eventLimit = Math.max(0, Math.min(maxEvents, busyEvents.size()));
        for (int i = 0; i < eventLimit; i++) {
            events.put(busyEvents.get(i).toJson());
        }

        JSONArray deadlines = new JSONArray();
        int deadlineLimit = Math.max(0, Math.min(maxDeadlines, externalDeadlines.size()));
        for (int i = 0; i < deadlineLimit; i++) {
            deadlines.put(externalDeadlines.get(i).toJson());
        }

        safePut(json, "busyEvents", events);
        safePut(json, "deadlines", deadlines);
        return json;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}