package hcmute.edu.vn.doinbot.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "schedule_proposals",
        indices = {
                @Index(value = {"proposal_type", "generated_at_millis"})
        }
)
public class ScheduleProposalEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPLIED = "APPLIED";

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @ColumnInfo(name = "proposal_type")
    public String proposalType;

    @ColumnInfo(name = "anchor_date")
    public String anchorDate;

    @ColumnInfo(name = "generated_at_millis")
    public long generatedAtMillis;

    @ColumnInfo(name = "window_start_millis")
    public long windowStartMillis;

    @ColumnInfo(name = "window_end_millis")
    public long windowEndMillis;

    @ColumnInfo(name = "conflict_report_json")
    public String conflictReportJson;

    @ColumnInfo(name = "options_json")
    public String optionsJson;

    @ColumnInfo(name = "status")
    public String status = STATUS_PENDING;

    @ColumnInfo(name = "applied_option_id")
    public String appliedOptionId;

    @ColumnInfo(name = "applied_at_millis")
    public long appliedAtMillis;
}
