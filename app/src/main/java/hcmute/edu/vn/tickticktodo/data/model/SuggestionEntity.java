package hcmute.edu.vn.tickticktodo.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "suggestions",
        indices = {
                @Index(value = {"type", "status"}),
                @Index(value = {"status"}),
                @Index(value = {"expires_at_millis"})
        }
)
public class SuggestionEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @ColumnInfo(name = "type")
    public String type;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "reason")
    public String reason;

    @ColumnInfo(name = "confidence")
    public float confidence;

    @ColumnInfo(name = "priority_score")
    public float priorityScore;

    @ColumnInfo(name = "created_at_millis")
    public long createdAtMillis;

    @ColumnInfo(name = "expires_at_millis")
    public long expiresAtMillis;

    @ColumnInfo(name = "requires_confirmation")
    public boolean requiresConfirmation;

    @ColumnInfo(name = "status")
    public String status;
}
