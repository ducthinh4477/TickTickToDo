package hcmute.edu.vn.tickticktodo.agent.integration;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import hcmute.edu.vn.tickticktodo.agent.integration.model.ExternalDeadline;
import hcmute.edu.vn.tickticktodo.agent.integration.model.ExternalEvent;
import hcmute.edu.vn.tickticktodo.agent.integration.model.HealthSummary;
import hcmute.edu.vn.tickticktodo.agent.integration.model.SchedulingConstraints;
import hcmute.edu.vn.tickticktodo.agent.integration.providers.AndroidCalendarProvider;
import hcmute.edu.vn.tickticktodo.agent.integration.providers.HealthConnectIntegrationProvider;
import hcmute.edu.vn.tickticktodo.agent.integration.providers.HealthFallbackProvider;
import hcmute.edu.vn.tickticktodo.agent.integration.providers.MoodleIntegrationProvider;
import hcmute.edu.vn.tickticktodo.ui.moodle.SchoolSyncWorker;

public class IntegrationFacade {

    private static final long WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    private static final String PREFS_NAME = "TickTickPrefs";
    private static final String KEY_CALENDAR_ENABLED = "integration_calendar_enabled";
    private static final String KEY_MOODLE_ENABLED = "integration_moodle_enabled";
    private static final String KEY_HEALTH_ENABLED = "integration_health_enabled";

    private static final String PROVIDER_DEVICE_CALENDAR = "DEVICE_CALENDAR";
    private static final String PROVIDER_MOODLE_ICAL = "MOODLE_ICAL";
    private static final String PROVIDER_HEALTH_CONNECT = "HEALTH_CONNECT";
    private static final String PROVIDER_LOCAL_HEURISTIC = "LOCAL_HEURISTIC";

    private static final String HEALTH_CONNECT_SERVICE_NAME = "healthconnect";
    private static final String HEALTH_PERMISSION_READ_STEPS = "android.permission.health.READ_STEPS";
    private static final String HEALTH_PERMISSION_READ_SLEEP = "android.permission.health.READ_SLEEP";
    private static final String HEALTH_PERMISSION_READ_ACTIVE_CALORIES = "android.permission.health.READ_ACTIVE_CALORIES_BURNED";

    private static volatile IntegrationFacade INSTANCE;

    private final Context appContext;
    private final List<IntegrationProvider> providers;
    private final SharedPreferences preferences;

    private IntegrationFacade(Context context) {
        this.appContext = context.getApplicationContext();
        this.preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.providers = new ArrayList<>();
        providers.add(new AndroidCalendarProvider(appContext));
        providers.add(new MoodleIntegrationProvider(appContext));
        providers.add(new HealthConnectIntegrationProvider(appContext));
        providers.add(new HealthFallbackProvider(appContext));
    }

    public static IntegrationFacade getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (IntegrationFacade.class) {
                if (INSTANCE == null) {
                    INSTANCE = new IntegrationFacade(context);
                }
            }
        }
        return INSTANCE;
    }

    public List<ExternalEvent> getAllEvents(long fromMillis, long toMillis) {
        Map<String, ExternalEvent> merged = new LinkedHashMap<>();
        for (IntegrationProvider provider : providers) {
            if (!isProviderEnabled(provider)) {
                continue;
            }

            List<ExternalEvent> events;
            try {
                events = provider.getEvents(fromMillis, toMillis);
            } catch (Exception ignored) {
                continue;
            }

            if (events == null) {
                continue;
            }

            for (ExternalEvent event : events) {
                if (event == null) {
                    continue;
                }
                String key = event.getId().isEmpty()
                        ? event.getSource() + ":" + event.getTitle() + ":" + event.getStartMillis()
                        : event.getId();
                merged.put(key, event);
            }
        }

        List<ExternalEvent> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingLong(ExternalEvent::getStartMillis));
        return result;
    }

    public List<ExternalDeadline> getAllDeadlines(long fromMillis, long toMillis) {
        Map<String, ExternalDeadline> merged = new LinkedHashMap<>();
        for (IntegrationProvider provider : providers) {
            if (!isProviderEnabled(provider)) {
                continue;
            }

            List<ExternalDeadline> deadlines;
            try {
                deadlines = provider.getDeadlines(fromMillis, toMillis);
            } catch (Exception ignored) {
                continue;
            }

            if (deadlines == null) {
                continue;
            }

            for (ExternalDeadline deadline : deadlines) {
                if (deadline == null) {
                    continue;
                }
                String key = deadline.getId().isEmpty()
                        ? deadline.getSource() + ":" + deadline.getTitle() + ":" + deadline.getDueMillis()
                        : deadline.getId();
                merged.put(key, deadline);
            }
        }

        List<ExternalDeadline> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingLong(ExternalDeadline::getDueMillis));
        return result;
    }

    public HealthSummary getHealthSummary(long fromMillis, long toMillis) {
        HealthSummary fallback = HealthSummary.unavailable("NONE");
        for (IntegrationProvider provider : providers) {
            if (!isProviderEnabled(provider)) {
                continue;
            }
            try {
                HealthSummary summary = provider.getHealthSummary(fromMillis, toMillis);
                if (summary != null) {
                    fallback = summary;
                    if (summary.isAvailable()) {
                        return summary;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    public SchedulingConstraints getSchedulingConstraints(long fromMillis, long toMillis) {
        return new SchedulingConstraints(
                getAllEvents(fromMillis, toMillis),
                getAllDeadlines(fromMillis, toMillis),
                getHealthSummary(fromMillis, toMillis)
        );
    }

    public String triggerDeadlineSync() {
        if (!isMoodleIntegrationEnabled()) {
            return "";
        }
        UUID workId = SchoolSyncWorker.triggerManualSync(appContext);
        return workId == null ? "" : workId.toString();
    }

    public boolean isCalendarIntegrationEnabled() {
        return preferences.getBoolean(KEY_CALENDAR_ENABLED, true);
    }

    public boolean isMoodleIntegrationEnabled() {
        return preferences.getBoolean(KEY_MOODLE_ENABLED, true);
    }

    public boolean isHealthIntegrationEnabled() {
        return preferences.getBoolean(KEY_HEALTH_ENABLED, true);
    }

    public void setCalendarIntegrationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_CALENDAR_ENABLED, enabled).apply();
    }

    public void setMoodleIntegrationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_MOODLE_ENABLED, enabled).apply();
    }

    public void setHealthIntegrationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_HEALTH_ENABLED, enabled).apply();
    }

    public boolean isHealthConnectSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    public boolean isHealthConnectAvailable() {
        if (!isHealthConnectSupported()) {
            return false;
        }
        try {
            return appContext.getSystemService(HEALTH_CONNECT_SERVICE_NAME) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean hasHealthConnectReadPermissions() {
        if (!isHealthConnectSupported() || !isHealthConnectAvailable()) {
            return true;
        }
        return hasHealthPermission(HEALTH_PERMISSION_READ_STEPS)
                && hasHealthPermission(HEALTH_PERMISSION_READ_SLEEP)
                && hasHealthPermission(HEALTH_PERMISSION_READ_ACTIVE_CALORIES);
    }

    public void resetSourceSettingsToDefault() {
        preferences.edit()
                .putBoolean(KEY_CALENDAR_ENABLED, true)
                .putBoolean(KEY_MOODLE_ENABLED, true)
                .putBoolean(KEY_HEALTH_ENABLED, true)
                .apply();
    }

    public JSONObject buildIntegrationStatusJson() {
        JSONObject status = new JSONObject();
        safePut(status, "calendarEnabled", isCalendarIntegrationEnabled());
        safePut(status, "moodleEnabled", isMoodleIntegrationEnabled());
        safePut(status, "healthEnabled", isHealthIntegrationEnabled());
        safePut(status, "calendarPermissionGranted", hasCalendarReadPermission());
        safePut(status, "activityRecognitionPermissionGranted", hasActivityRecognitionPermission());
        safePut(status, "healthConnectSupported", isHealthConnectSupported());
        safePut(status, "healthConnectAvailable", isHealthConnectAvailable());
        safePut(status, "healthConnectPermissionsGranted", hasHealthConnectReadPermissions());
        return status;
    }

    public JSONObject buildQuickSummaryJson(long nowMillis) {
        long weekEnd = nowMillis + WEEK_MILLIS;
        List<ExternalEvent> events = getAllEvents(nowMillis, weekEnd);
        List<ExternalDeadline> deadlines = getAllDeadlines(nowMillis, weekEnd);
        HealthSummary healthSummary = getHealthSummary(nowMillis, weekEnd);
        JSONObject status = buildIntegrationStatusJson();

        JSONObject json = new JSONObject();
        safePut(json, "eventCount7d", events.size());
        safePut(json, "deadlineCount7d", deadlines.size());
        safePut(json, "health", healthSummary.toJson());
        safePut(json, "calendarEnabled", status.optBoolean("calendarEnabled", true));
        safePut(json, "moodleEnabled", status.optBoolean("moodleEnabled", true));
        safePut(json, "healthEnabled", status.optBoolean("healthEnabled", true));
        safePut(json, "calendarPermissionGranted", status.optBoolean("calendarPermissionGranted", true));
        safePut(json, "activityRecognitionPermissionGranted", status.optBoolean("activityRecognitionPermissionGranted", true));
        safePut(json, "status", status);
        return json;
    }

    private boolean isProviderEnabled(IntegrationProvider provider) {
        if (provider == null) {
            return false;
        }

        String providerName = provider.getProviderName();
        if (PROVIDER_DEVICE_CALENDAR.equals(providerName)) {
            return isCalendarIntegrationEnabled();
        }
        if (PROVIDER_MOODLE_ICAL.equals(providerName)) {
            return isMoodleIntegrationEnabled();
        }
        if (PROVIDER_HEALTH_CONNECT.equals(providerName)) {
            return isHealthIntegrationEnabled();
        }
        if (PROVIDER_LOCAL_HEURISTIC.equals(providerName)) {
            return isHealthIntegrationEnabled();
        }
        return true;
    }

    private boolean hasCalendarReadPermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true;
        }
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasHealthPermission(String permission) {
        return ContextCompat.checkSelfPermission(appContext, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}