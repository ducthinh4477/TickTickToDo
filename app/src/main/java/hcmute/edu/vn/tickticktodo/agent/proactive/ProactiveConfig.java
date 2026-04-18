package hcmute.edu.vn.tickticktodo.agent.proactive;

public final class ProactiveConfig {

    private ProactiveConfig() {
    }

    // Time windows
    public static final int EVENING_START_HOUR = 18;
    public static final int EVENING_END_HOUR = 22;
    public static final int MIDDAY_START_HOUR = 12;
    public static final int MIDDAY_END_HOUR = 14;

    // Rule thresholds
    public static final int MIN_OVERDUE_FOR_REPLAN = 3;
    public static final int MIDDAY_OVERLOAD_TASK_COUNT = 6;
    public static final int MIDDAY_OVERLOAD_TASK_COUNT_WITH_OVERDUE = 4;
    public static final int MIDDAY_OVERLOAD_OVERDUE_COUNT = 2;

    // Suggestion lifecycle
    public static final long OVERDUE_REPLAN_TTL_MILLIS = 3L * 60L * 60L * 1000L;
    public static final long MOODLE_DEADLINE_TTL_MILLIS = 12L * 60L * 60L * 1000L;
    public static final long MIDDAY_OVERLOAD_TTL_MILLIS = 4L * 60L * 60L * 1000L;
    public static final long SUGGESTION_DEDUPE_WINDOW_MILLIS = 45L * 60L * 1000L;

    // Overlay display policy
    public static final float OVERLAY_MIN_PRIORITY_SCORE = 0.85f;
    public static final int OVERLAY_MAX_SUGGESTIONS = 2;
    public static final long OVERLAY_SURFACE_COOLDOWN_MILLIS = 2L * 60L * 1000L;

    public static boolean isHourInRangeInclusive(int hourOfDay, int startHour, int endHour) {
        return hourOfDay >= startHour && hourOfDay <= endHour;
    }
}
