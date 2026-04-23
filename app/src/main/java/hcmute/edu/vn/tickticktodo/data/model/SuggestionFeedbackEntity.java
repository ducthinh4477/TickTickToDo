package hcmute.edu.vn.tickticktodo.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "suggestion_feedback",
        indices = {
                @Index(value = {"suggestion_id"}),
                @Index(value = {"feedback_type"}),
                @Index(value = {"created_at_millis"}),
                @Index(value = {"suggestion_type", "feedback_type", "created_at_millis"})
        }
)
public class SuggestionFeedbackEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @ColumnInfo(name = "suggestion_id")
    public String suggestionId;

    @ColumnInfo(name = "suggestion_type")
    public String suggestionType;

    @ColumnInfo(name = "feedback_type")
    public String feedbackType;

    @ColumnInfo(name = "channel")
    public String channel;

    @ColumnInfo(name = "priority_score")
    public float priorityScore;

    @ColumnInfo(name = "confidence")
    public float confidence;

    @ColumnInfo(name = "created_at_millis")
    public long createdAtMillis;
}
