package hcmute.edu.vn.tickticktodo.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

public final class AppRuntimeState {

    private static final String PREFS_NAME = "agent_runtime_state_prefs";
    private static final String KEY_CURRENT_SCREEN = "current_screen";
    private static final String KEY_LAST_RESUMED_AT = "last_resumed_at";
    private static final String KEY_APP_IN_FOREGROUND = "app_in_foreground";
    private static final String KEY_STARTED_ACTIVITIES = "started_activities";
    private static final String UNKNOWN_SCREEN = "unknown";

    public static final class Snapshot {
        public final String currentScreen;
        public final long lastResumedAt;
        public final boolean appInForeground;
        public final int startedActivities;

        public Snapshot(String currentScreen, long lastResumedAt, boolean appInForeground, int startedActivities) {
            this.currentScreen = currentScreen;
            this.lastResumedAt = lastResumedAt;
            this.appInForeground = appInForeground;
            this.startedActivities = startedActivities;
        }
    }

    private AppRuntimeState() {
    }

    public static void initialize(Context context) {
        SharedPreferences preferences = getPreferences(context);
        if (preferences.contains(KEY_CURRENT_SCREEN)) {
            return;
        }

        preferences.edit()
                .putString(KEY_CURRENT_SCREEN, UNKNOWN_SCREEN)
                .putLong(KEY_LAST_RESUMED_AT, 0L)
                .putBoolean(KEY_APP_IN_FOREGROUND, false)
                .putInt(KEY_STARTED_ACTIVITIES, 0)
                .apply();
    }

    public static void updateOnActivityStarted(Context context, String screenName, int startedActivities) {
        getPreferences(context).edit()
                .putString(KEY_CURRENT_SCREEN, sanitizeScreenName(screenName))
                .putBoolean(KEY_APP_IN_FOREGROUND, true)
                .putInt(KEY_STARTED_ACTIVITIES, Math.max(0, startedActivities))
                .apply();
    }

    public static void updateOnActivityResumed(Context context, String screenName) {
        getPreferences(context).edit()
                .putString(KEY_CURRENT_SCREEN, sanitizeScreenName(screenName))
                .putLong(KEY_LAST_RESUMED_AT, System.currentTimeMillis())
                .putBoolean(KEY_APP_IN_FOREGROUND, true)
                .apply();
    }

    public static void updateOnActivityStopped(Context context, int startedActivities) {
        boolean appInForeground = startedActivities > 0;
        getPreferences(context).edit()
                .putBoolean(KEY_APP_IN_FOREGROUND, appInForeground)
                .putInt(KEY_STARTED_ACTIVITIES, Math.max(0, startedActivities))
                .apply();
    }

    public static Snapshot getSnapshot(Context context) {
        SharedPreferences preferences = getPreferences(context);
        return new Snapshot(
                sanitizeScreenName(preferences.getString(KEY_CURRENT_SCREEN, UNKNOWN_SCREEN)),
                preferences.getLong(KEY_LAST_RESUMED_AT, 0L),
                preferences.getBoolean(KEY_APP_IN_FOREGROUND, false),
                Math.max(0, preferences.getInt(KEY_STARTED_ACTIVITIES, 0))
        );
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String sanitizeScreenName(String rawScreenName) {
        if (TextUtils.isEmpty(rawScreenName)) {
            return UNKNOWN_SCREEN;
        }
        return rawScreenName.trim();
    }
}