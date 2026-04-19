package hcmute.edu.vn.tickticktodo.agent.proactive;

public final class ProactiveConfig {

    private ProactiveConfig() {
    }

    // Time windows
    public static final int MORNING_START_HOUR = 6;
    public static final int MORNING_END_HOUR = 10;
    public static final int EVENING_START_HOUR = 18;
    public static final int EVENING_END_HOUR = 22;
    public static final int MIDDAY_START_HOUR = 12;
    public static final int MIDDAY_END_HOUR = 14;
    public static final int COMMUTE_MORNING_START_HOUR = 7;
    public static final int COMMUTE_MORNING_END_HOUR = 9;
    public static final int COMMUTE_EVENING_START_HOUR = 17;
    public static final int COMMUTE_EVENING_END_HOUR = 19;
    public static final int WEEKLY_REVIEW_MORNING_START_HOUR = 7;
    public static final int WEEKLY_REVIEW_MORNING_END_HOUR = 11;
    public static final int WEEKLY_REVIEW_EVENING_START_HOUR = 18;
    public static final int WEEKLY_REVIEW_EVENING_END_HOUR = 21;
    public static final int QUIET_HOURS_START = 22;
    public static final int QUIET_HOURS_END = 7;

    // Rule thresholds
    public static final int MORNING_PLANNING_MIN_TASKS = 3;
    public static final int MIN_OVERDUE_FOR_REPLAN = 3;
    public static final int MIDDAY_OVERLOAD_TASK_COUNT = 6;
    public static final int MIDDAY_OVERLOAD_TASK_COUNT_WITH_OVERDUE = 4;
    public static final int MIDDAY_OVERLOAD_OVERDUE_COUNT = 2;
    public static final int WEEKLY_REVIEW_MIN_ITEMS = 5;
    public static final int OVERLOAD_RESCUE_TASK_COUNT = 8;
    public static final int OVERLOAD_RESCUE_OVERDUE_COUNT = 4;
    public static final int OVERLOAD_RESCUE_BATTERY_MIN = 15;

    // Suggestion lifecycle
    public static final long MORNING_PLANNING_TTL_MILLIS = 5L * 60L * 60L * 1000L;
    public static final long OVERDUE_REPLAN_TTL_MILLIS = 3L * 60L * 60L * 1000L;
    public static final long MOODLE_DEADLINE_TTL_MILLIS = 12L * 60L * 60L * 1000L;
    public static final long MIDDAY_OVERLOAD_TTL_MILLIS = 4L * 60L * 60L * 1000L;
    public static final long WEEKLY_REVIEW_TTL_MILLIS = 18L * 60L * 60L * 1000L;
    public static final long OVERLOAD_RESCUE_TTL_MILLIS = 2L * 60L * 60L * 1000L;
    public static final long COMMUTE_MICRO_TASK_TTL_MILLIS = 2L * 60L * 60L * 1000L;
    public static final long SUGGESTION_DEDUPE_WINDOW_MILLIS = 45L * 60L * 1000L;
    public static final long SUGGESTION_DEDUPE_WINDOW_MIN_MILLIS = 20L * 60L * 1000L;
    public static final long SUGGESTION_DEDUPE_WINDOW_MAX_MILLIS = 3L * 60L * 60L * 1000L;

    // Overlay display policy
    public static final float OVERLAY_MIN_PRIORITY_SCORE = 0.85f;
    public static final float OVERLAY_MIN_PRIORITY_SCORE_FLOOR = 0.72f;
    public static final float OVERLAY_MIN_PRIORITY_SCORE_CEILING = 0.95f;
    public static final int OVERLAY_MAX_SUGGESTIONS = 2;
    public static final long OVERLAY_SURFACE_COOLDOWN_MILLIS = 2L * 60L * 1000L;
    public static final long OVERLAY_SURFACE_COOLDOWN_MIN_MILLIS = 60L * 1000L;
    public static final long OVERLAY_SURFACE_COOLDOWN_MAX_MILLIS = 8L * 60L * 1000L;

    public static boolean isHourInRangeInclusive(int hourOfDay, int startHour, int endHour) {
        return hourOfDay >= startHour && hourOfDay <= endHour;
    }

    public static boolean isQuietHours(int hourOfDay) {
        if (QUIET_HOURS_START == QUIET_HOURS_END) {
            return false;
        }
        if (QUIET_HOURS_START < QUIET_HOURS_END) {
            return hourOfDay >= QUIET_HOURS_START && hourOfDay < QUIET_HOURS_END;
        }
        return hourOfDay >= QUIET_HOURS_START || hourOfDay < QUIET_HOURS_END;
    }
}
