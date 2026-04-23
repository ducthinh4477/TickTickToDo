package hcmute.edu.vn.doinbot.agent.context;

import org.json.JSONException;
import org.json.JSONObject;

public class ContextSnapshot {

    private final String timeOfDay;
    private final int dayOfWeek;
    private final int batteryLevel;
    private final boolean charging;
    private final String connectivity;
    private final boolean appInForeground;
    private final long capturedAtMillis;

    public ContextSnapshot(String timeOfDay,
                           int dayOfWeek,
                           int batteryLevel,
                           boolean charging,
                           String connectivity,
                           boolean appInForeground,
                           long capturedAtMillis) {
        this.timeOfDay = timeOfDay == null ? "UNKNOWN" : timeOfDay;
        this.dayOfWeek = dayOfWeek;
        this.batteryLevel = batteryLevel;
        this.charging = charging;
        this.connectivity = connectivity == null ? "UNKNOWN" : connectivity;
        this.appInForeground = appInForeground;
        this.capturedAtMillis = capturedAtMillis;
    }

    public String getTimeOfDay() {
        return timeOfDay;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public boolean isCharging() {
        return charging;
    }

    public String getConnectivity() {
        return connectivity;
    }

    public boolean isAppInForeground() {
        return appInForeground;
    }

    public long getCapturedAtMillis() {
        return capturedAtMillis;
    }

    public JSONObject toCompactJson() {
        JSONObject json = new JSONObject();
        safePut(json, "timeOfDay", timeOfDay);
        safePut(json, "dayOfWeek", dayOfWeek);
        safePut(json, "batteryLevel", batteryLevel);
        safePut(json, "charging", charging);
        safePut(json, "connectivity", connectivity);
        safePut(json, "appInForeground", appInForeground);
        safePut(json, "capturedAtMillis", capturedAtMillis);
        return json;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
