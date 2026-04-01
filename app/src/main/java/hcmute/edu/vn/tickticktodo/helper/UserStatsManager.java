package hcmute.edu.vn.tickticktodo.helper;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

public class UserStatsManager {

    private static final String PREFS_NAME = "user_stats_prefs";
    private static final String KEY_CURRENT_XP = "current_xp";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_CURRENT_STREAK = "current_streak";
    private static final String KEY_LAST_COMPLETED_DAY_START = "last_completed_day_start";

    private static volatile UserStatsManager instance;

    public static class Stats {
        public final int currentXP;
        public final int level;
        public final int currentStreak;

        public Stats(int currentXP, int level, int currentStreak) {
            this.currentXP = currentXP;
            this.level = level;
            this.currentStreak = currentStreak;
        }

        public int getXpInCurrentLevel() {
            return currentXP % 100;
        }
    }

    public static class XpResult {
        public final Stats stats;
        public final boolean levelUp;

        public XpResult(Stats stats, boolean levelUp) {
            this.stats = stats;
            this.levelUp = levelUp;
        }
    }

    private final SharedPreferences preferences;

    private UserStatsManager(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static UserStatsManager getInstance(Context context) {
        if (instance == null) {
            synchronized (UserStatsManager.class) {
                if (instance == null) {
                    instance = new UserStatsManager(context);
                }
            }
        }
        return instance;
    }

    public synchronized Stats getStats() {
        int xp = preferences.getInt(KEY_CURRENT_XP, 0);
        int level = preferences.getInt(KEY_LEVEL, Math.max(1, xp / 100 + 1));
        int streak = preferences.getInt(KEY_CURRENT_STREAK, 0);
        return new Stats(xp, level, streak);
    }

    public synchronized XpResult addXp(int delta) {
        return addXp(delta, false);
    }

    public synchronized XpResult addXp(int delta, boolean updateStreakOnPositiveDelta) {
        Stats before = getStats();

        int newXp = Math.max(0, before.currentXP + delta);
        int newLevel = Math.max(1, newXp / 100 + 1);
        int newStreak = before.currentStreak;

        long lastCompletedDayStart = preferences.getLong(KEY_LAST_COMPLETED_DAY_START, -1L);
        long todayStart = getTodayStartMillis();

        if (updateStreakOnPositiveDelta && delta > 0) {
            if (lastCompletedDayStart < 0) {
                newStreak = 1;
            } else if (isSameDay(lastCompletedDayStart, todayStart)) {
                newStreak = before.currentStreak;
            } else if (isYesterday(lastCompletedDayStart, todayStart)) {
                newStreak = before.currentStreak + 1;
            } else {
                newStreak = 1;
            }
        }

        SharedPreferences.Editor editor = preferences.edit()
                .putInt(KEY_CURRENT_XP, newXp)
                .putInt(KEY_LEVEL, newLevel)
                .putInt(KEY_CURRENT_STREAK, Math.max(0, newStreak));

        if (updateStreakOnPositiveDelta && delta > 0 && !isSameDay(lastCompletedDayStart, todayStart)) {
            editor.putLong(KEY_LAST_COMPLETED_DAY_START, todayStart);
        }

        editor.apply();

        Stats after = new Stats(newXp, newLevel, Math.max(0, newStreak));
        return new XpResult(after, newLevel > before.level);
    }

    private boolean isYesterday(long candidateDayStart, long todayStart) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(todayStart);
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        return isSameDay(candidateDayStart, calendar.getTimeInMillis());
    }

    private boolean isSameDay(long a, long b) {
        if (a < 0 || b < 0) {
            return false;
        }
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private long getTodayStartMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}