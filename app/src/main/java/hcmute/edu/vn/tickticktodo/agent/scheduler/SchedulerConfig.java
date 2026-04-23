package hcmute.edu.vn.tickticktodo.agent.scheduler;

public final class SchedulerConfig {

    private SchedulerConfig() {
    }

    public static final int DAILY_WINDOW_DAYS = 1;
    public static final int WEEKLY_WINDOW_DAYS = 7;

    public static final String OPTION_ID_AGGRESSIVE = "AGGRESSIVE";
    public static final String OPTION_LABEL_AGGRESSIVE = "Aggressive";
    public static final String OPTION_DESCRIPTION_AGGRESSIVE = "Ưu tiên xử lý tối đa task gấp, chấp nhận lịch dày.";

    public static final String OPTION_ID_BALANCED = "BALANCED";
    public static final String OPTION_LABEL_BALANCED = "Balanced";
    public static final String OPTION_DESCRIPTION_BALANCED = "Cân bằng giữa độ gấp và sức bền trong ngày.";

    public static final String OPTION_ID_LOW_STRESS = "LOW_STRESS";
    public static final String OPTION_LABEL_LOW_STRESS = "Low-stress";
    public static final String OPTION_DESCRIPTION_LOW_STRESS = "Giữ nhịp làm việc nhẹ, ưu tiên giảm quá tải.";

    public static final float WEIGHT_IMPORTANCE = 1.0f;
    public static final float WEIGHT_URGENCY = 1.0f;
    public static final float WEIGHT_PROFILE_AFFINITY = 1.0f;
    public static final float WEIGHT_ENERGY_FIT = 1.0f;

    public static final float AGGRESSIVE_FILL_RATIO = 0.92f;
    public static final float AGGRESSIVE_IMPORTANCE_MULTIPLIER = 1.2f;
    public static final float AGGRESSIVE_URGENCY_MULTIPLIER = 1.3f;
    public static final float AGGRESSIVE_PROFILE_AFFINITY_MULTIPLIER = 0.8f;
    public static final float AGGRESSIVE_ENERGY_FIT_MULTIPLIER = 0.7f;
    public static final int AGGRESSIVE_DEFAULT_BLOCK_MINUTES = 50;
    public static final int AGGRESSIVE_BREAK_MINUTES = 3;

    public static final float BALANCED_FILL_RATIO = 0.78f;
    public static final float BALANCED_IMPORTANCE_MULTIPLIER = 1.0f;
    public static final float BALANCED_URGENCY_MULTIPLIER = 1.0f;
    public static final float BALANCED_PROFILE_AFFINITY_MULTIPLIER = 1.0f;
    public static final float BALANCED_ENERGY_FIT_MULTIPLIER = 0.9f;
    public static final int BALANCED_DEFAULT_BLOCK_MINUTES = 40;
    public static final int BALANCED_BREAK_MINUTES = 5;

    public static final float LOW_STRESS_FILL_RATIO = 0.62f;
    public static final float LOW_STRESS_IMPORTANCE_MULTIPLIER = 0.85f;
    public static final float LOW_STRESS_URGENCY_MULTIPLIER = 0.8f;
    public static final float LOW_STRESS_PROFILE_AFFINITY_MULTIPLIER = 1.2f;
    public static final float LOW_STRESS_ENERGY_FIT_MULTIPLIER = 1.0f;
    public static final int LOW_STRESS_DEFAULT_BLOCK_MINUTES = 30;
    public static final int LOW_STRESS_BREAK_MINUTES = 8;

    public static final float SCORE_SCHEDULED_MINUTES_FACTOR = 1.0f;
    public static final float PENALTY_UNSCHEDULED_MINUTES = 1.35f;
    public static final float SCORE_QUALITY_FACTOR = 8.0f;
    public static final float PENALTY_FRAGMENTATION = 5.0f;
    public static final float PENALTY_CONTEXT_MISMATCH = 0.6f;
    public static final float BONUS_CONTEXT_MATCH = 0.2f;

    public static final float URGENCY_NO_DEADLINE = 0.35f;
    public static final float URGENCY_OVERDUE = 1.0f;
    public static final float URGENCY_BUCKET_1_DAY = 1f;
    public static final float URGENCY_BUCKET_2_DAYS = 2f;
    public static final float URGENCY_BUCKET_4_DAYS = 4f;
    public static final float URGENCY_BUCKET_7_DAYS = 7f;
    public static final float URGENCY_LE_1_DAY = 0.92f;
    public static final float URGENCY_LE_2_DAYS = 0.78f;
    public static final float URGENCY_LE_4_DAYS = 0.60f;
    public static final float URGENCY_LE_7_DAYS = 0.45f;
    public static final float URGENCY_DEFAULT = 0.25f;

    public static final float PROFILE_DEFAULT_AFFINITY = 0.5f;
    public static final int PROFILE_PREFERRED_SESSION_MIN = 20;
    public static final int PROFILE_PREFERRED_SESSION_MAX = 120;
    public static final float PROFILE_SESSION_GAP_DIVISOR = 80f;
    public static final float PROFILE_SESSION_WEIGHT = 0.6f;
    public static final float PROFILE_FOCUS_WEIGHT = 0.4f;
    public static final float PROFILE_FOCUS_BASE = 0.5f;
    public static final float PROFILE_FOCUS_STRONG = 1.0f;
    public static final float PROFILE_FOCUS_MEDIUM = 0.65f;
    public static final float PROFILE_FOCUS_RELAXED = 0.9f;
    public static final int PROFILE_FOCUS_STRONG_START_HOUR_MAX = 11;
    public static final int PROFILE_FOCUS_RELAXED_END_HOUR_MIN = 15;

    public static final float PRIORITY_NORMALIZATION_FACTOR = 3f;

    public static final float ENERGY_POTENTIAL_DEFAULT = 0.5f;
    public static final float ENERGY_POTENTIAL_HIGH_FOCUS = 0.95f;
    public static final float ENERGY_POTENTIAL_LOW = 0.7f;
    public static final float ENERGY_POTENTIAL_MEDIUM = 0.82f;

    public static final int SLOT_HIGH_FOCUS_START_HOUR = 8;
    public static final int SLOT_HIGH_FOCUS_END_HOUR = 11;
    public static final int SLOT_MEDIUM_START_HOUR = 12;
    public static final int SLOT_MEDIUM_END_HOUR = 17;

    public static final float SLOT_FIT_EXACT = 1.0f;
    public static final float SLOT_FIT_MEDIUM_TASK = 0.82f;
    public static final float SLOT_FIT_MISMATCH = 0.55f;
}