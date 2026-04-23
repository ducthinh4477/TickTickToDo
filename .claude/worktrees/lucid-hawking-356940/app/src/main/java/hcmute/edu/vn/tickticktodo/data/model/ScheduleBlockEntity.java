package hcmute.edu.vn.doinbot.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "schedule_blocks",
        foreignKeys = @ForeignKey(
                entity = ScheduleProposalEntity.class,
                parentColumns = "id",
                childColumns = "proposal_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index(value = {"proposal_id"}),
                @Index(value = {"option_id"}),
                @Index(value = {"start_millis"})
        }
)
public class ScheduleBlockEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @ColumnInfo(name = "proposal_id")
    public String proposalId;

    @ColumnInfo(name = "option_id")
    public String optionId;

    @ColumnInfo(name = "task_id")
    public Long taskId;

    @ColumnInfo(name = "task_title")
    public String taskTitle;

    @ColumnInfo(name = "start_millis")
    public long startMillis;

    @ColumnInfo(name = "end_millis")
    public long endMillis;

    @ColumnInfo(name = "block_type")
    public String blockType;

    @ColumnInfo(name = "note")
    public String note;
}
