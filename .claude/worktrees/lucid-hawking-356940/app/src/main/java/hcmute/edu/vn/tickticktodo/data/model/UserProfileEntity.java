package hcmute.edu.vn.doinbot.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class UserProfileEntity {

    @PrimaryKey
    @ColumnInfo(name = "id")
    public long id = 1L;

    @ColumnInfo(name = "focus_start_hour")
    public int focusStartHour = 9;

    @ColumnInfo(name = "focus_end_hour")
    public int focusEndHour = 12;

    @ColumnInfo(name = "preferred_session_minutes")
    public int preferredSessionMinutes = 45;

    @ColumnInfo(name = "chronotype_score")
    public float chronotypeScore = 0.5f;

    @ColumnInfo(name = "avg_daily_completion_rate")
    public float avgDailyCompletionRate = 0.0f;

    @ColumnInfo(name = "suggestion_acceptance_rate")
    public float suggestionAcceptanceRate = 0.0f;

    @ColumnInfo(name = "suggestion_dismiss_rate")
    public float suggestionDismissRate = 0.0f;

    @ColumnInfo(name = "suggestion_apply_rate")
    public float suggestionApplyRate = 0.0f;

    @ColumnInfo(name = "total_feedback_count")
    public int totalFeedbackCount = 0;

    @ColumnInfo(name = "last_daily_reflection_millis")
    public long lastDailyReflectionMillis = 0L;

    @ColumnInfo(name = "last_weekly_reflection_millis")
    public long lastWeeklyReflectionMillis = 0L;

    @ColumnInfo(name = "updated_at_millis")
    public long updatedAtMillis = 0L;
}
