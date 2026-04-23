package hcmute.edu.vn.doinbot.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "subtasks",
        foreignKeys = @ForeignKey(
                entity = Task.class,
                parentColumns = "id",
                childColumns = "task_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index(value = "task_id")
)
public class Subtask {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "task_id")
    private long taskId;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "is_completed", defaultValue = "0")
    private boolean completed;

    @ColumnInfo(name = "is_approved", defaultValue = "0")
    private boolean approved;

    @ColumnInfo(name = "priority", defaultValue = "0")
    private int priority;

    @ColumnInfo(name = "order_index", defaultValue = "0")
    private int orderIndex;

    public Subtask() {
    }

    @Ignore
    public Subtask(long taskId, String title, boolean completed, boolean approved, int priority, int orderIndex) {
        this.taskId = taskId;
        this.title = title;
        this.completed = completed;
        this.approved = approved;
        this.priority = priority;
        this.orderIndex = orderIndex;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTaskId() {
        return taskId;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}