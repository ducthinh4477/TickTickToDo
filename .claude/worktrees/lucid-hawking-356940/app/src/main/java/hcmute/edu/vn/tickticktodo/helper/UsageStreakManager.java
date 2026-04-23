package hcmute.edu.vn.doinbot.helper;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

public final class UsageStreakManager {

    private static final String PREFS_NAME = "usage_streak_prefs";
    private static final String KEY_LAST_ACTIVE_DAY_START = "last_active_day_start";
    private static final String KEY_CURRENT_STREAK_DAYS = "current_streak_days";

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private UsageStreakManager() {
    }

    public static synchronized int markUsageAndGetCurrentStreak(Context context) {
        SharedPreferences preferences = getPreferences(context);

        long todayStart = getDayStart(System.currentTimeMillis());
        long lastActiveDayStart = preferences.getLong(KEY_LAST_ACTIVE_DAY_START, -1L);
        int currentStreak = Math.max(0, preferences.getInt(KEY_CURRENT_STREAK_DAYS, 0));

        if (lastActiveDayStart == todayStart) {
            return Math.max(1, currentStreak);
        }

        if (lastActiveDayStart == todayStart - DAY_MILLIS) {
            currentStreak = Math.max(1, currentStreak + 1);
        } else {
            currentStreak = 1;
        }

        preferences.edit()
                .putLong(KEY_LAST_ACTIVE_DAY_START, todayStart)
                .putInt(KEY_CURRENT_STREAK_DAYS, currentStreak)
                .apply();

        return currentStreak;
    }

    public static synchronized int getCurrentStreakDays(Context context) {
        return Math.max(0, getPreferences(context).getInt(KEY_CURRENT_STREAK_DAYS, 0));
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static long getDayStart(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}