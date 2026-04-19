package hcmute.edu.vn.tickticktodo.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "agent_decision_logs",
        indices = {
                @Index(value = {"created_at_millis"}),
                @Index(value = {"source", "stage", "created_at_millis"}),
                @Index(value = {"suggestion_id"})
        }
)
public class AgentDecisionLogEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @ColumnInfo(name = "source")
    public String source;

    @ColumnInfo(name = "event_type")
    public String eventType;

    @ColumnInfo(name = "stage")
    public String stage;

    @ColumnInfo(name = "decision")
    public String decision;

    @ColumnInfo(name = "detail")
    public String detail;

    @ColumnInfo(name = "suggestion_id")
    public String suggestionId;

    @ColumnInfo(name = "created_at_millis")
    public long createdAtMillis;
}
