package hcmute.edu.vn.tickticktodo.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
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

    // ─── Calendar Event fields (thêm v4 → v5) ───────────────────────────────────

    @ColumnInfo(name = "location")
    private String location; // Địa điểm sự kiện (null nếu không có)

    @ColumnInfo(name = "duration", defaultValue = "0")
    private int duration; // Thời lượng (phút). 0 = không xác định

    /**
     * Lặp lại sự kiện:
     *   0 = RECURRENCE_NONE    – Không lặp
     *   1 = RECURRENCE_WEEKLY  – Hàng tuần
     *   2 = RECURRENCE_MONTHLY – Hàng tháng
     */
    @ColumnInfo(name = "recurrence", defaultValue = "0")
    private int recurrence;

    @ColumnInfo(name = "source")
    private String source; // Nguồn task, VD: 'Moodle'

    public static final int RECURRENCE_NONE    = 0;
    public static final int RECURRENCE_WEEKLY  = 1;
    public static final int RECURRENCE_MONTHLY = 2;

    // Constructor
    public Task() {
    }

    @Ignore
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

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

    // ─── Calendar Event getters/setters ──────────────────────────────────────────

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    
    // --- Attachments ---
    @ColumnInfo(name = "image_attachment")
    private String imageAttachment;
    
    @ColumnInfo(name = "voice_attachment")
    private String voiceAttachment;
    
    @ColumnInfo(name = "file_attachment")
    private String fileAttachment;
    
    public String getImageAttachment() { return imageAttachment; }
    public void setImageAttachment(String imageAttachment) { this.imageAttachment = imageAttachment; }
    
    public String getVoiceAttachment() { return voiceAttachment; }
    public void setVoiceAttachment(String voiceAttachment) { this.voiceAttachment = voiceAttachment; }
    
    public String getFileAttachment() { return fileAttachment; }
    public void setFileAttachment(String fileAttachment) { this.fileAttachment = fileAttachment; }

    public int getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(int recurrence) {
        this.recurrence = recurrence;
    }
}
