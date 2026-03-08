package hcmute.edu.vn.tickticktodo.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Entity đại diện cho một công việc (task) trong ứng dụng.
 * Bảng "tasks" trong Room Database.
 *
 * Priority levels (giống TickTick):
 *   0 = None (không ưu tiên)
 *   1 = Low
 *   2 = Medium
 *   3 = High
 */
@Entity(
    tableName = "tasks",
    foreignKeys = @ForeignKey(
        entity = TodoList.class,
        parentColumns = "id",
        childColumns = "list_id",
        onDelete = ForeignKey.SET_NULL
    ),
    indices = @Index(value = "list_id")
)
public class Task {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "title")
    private String title;

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "due_date")
    private Long dueDate; // timestamp in milliseconds

    @ColumnInfo(name = "is_completed")
    private boolean isCompleted;

    @ColumnInfo(name = "priority")
    private int priority; // 0 = None, 1 = Low, 2 = Medium, 3 = High

    @ColumnInfo(name = "list_id")
    private Long listId; // khóa ngoại tới TodoList (null = Inbox / không thuộc list nào)

    @ColumnInfo(name = "order_index", defaultValue = "0")
    private int orderIndex; // Thứ tự kéo thả (drag & drop), dùng cho Sort Custom

    @ColumnInfo(name = "completed_date")
    private Long completedDate; // Timestamp khi task được hoàn thành (null nếu chưa hoàn thành)

    // Constructor
    public Task(String title, String description, Long dueDate, boolean isCompleted, int priority) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.isCompleted = isCompleted;
        this.priority = priority;
        this.listId = null;
        this.orderIndex = 0;
        this.completedDate = null;
    }

    // Getters & Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getDueDate() {
        return dueDate;
    }

    public void setDueDate(Long dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Long getListId() {
        return listId;
    }

    public void setListId(Long listId) {
        this.listId = listId;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Long getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Long completedDate) {
        this.completedDate = completedDate;
    }
}
