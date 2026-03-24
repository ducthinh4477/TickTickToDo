package hcmute.edu.vn.tickticktodo.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "activity_logs")
public class ActivityLog {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String action; // CREATE, UPDATE, DELETE

    public long timestamp;

    @ColumnInfo(name = "task_title")
    public String taskTitle;

    public ActivityLog(String action, long timestamp, String taskTitle) {
        this.action = action;
        this.timestamp = timestamp;
        this.taskTitle = taskTitle;
    }
}
