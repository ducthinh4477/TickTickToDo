package hcmute.edu.vn.tickticktodo.agent.integration.model;

import org.json.JSONException;
import org.json.JSONObject;

public class HealthSummary {

    public static final String ENERGY_LOW = "LOW";
    public static final String ENERGY_MEDIUM = "MEDIUM";
    public static final String ENERGY_HIGH = "HIGH";

    private final boolean available;
    private final float sleepHours;
    private final int steps;
    private final int activeMinutes;
    private final String inferredEnergy;
    private final String source;

    public HealthSummary(boolean available,
                         float sleepHours,
                         int steps,
                         int activeMinutes,
                         String inferredEnergy,
                         String source) {
        this.available = available;
        this.sleepHours = sleepHours;
        this.steps = steps;
        this.activeMinutes = activeMinutes;
        this.inferredEnergy = inferredEnergy == null ? ENERGY_MEDIUM : inferredEnergy;
        this.source = source == null ? "" : source;
    }

    public static HealthSummary unavailable(String source) {
        return new HealthSummary(false, 0f, 0, 0, ENERGY_MEDIUM, source);
    }

    public boolean isAvailable() {
        return available;
    }

    public float getSleepHours() {
        return sleepHours;
    }

    public int getSteps() {
        return steps;
    }

    public int getActiveMinutes() {
        return activeMinutes;
    }

    public String getInferredEnergy() {
        return inferredEnergy;
    }

    public String getSource() {
        return source;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        safePut(json, "available", available);
        safePut(json, "sleepHours", sleepHours);
        safePut(json, "steps", steps);
        safePut(json, "activeMinutes", activeMinutes);
        safePut(json, "inferredEnergy", inferredEnergy);
        safePut(json, "source", source);
        return json;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}