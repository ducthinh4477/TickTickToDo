package hcmute.edu.vn.doinbot.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Entity đại diện cho một danh sách (list) trong ứng dụng.
 * Bảng "todo_lists" trong Room Database.
 *
 * Mỗi Task có thể thuộc về một TodoList thông qua trường listId.
 *
 * v3: thêm cột icon_res_id để lưu R.drawable.* của icon người dùng chọn.
 */
@Entity(tableName = "todo_lists")
public class TodoList {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "color_res")
    private int colorRes;

    /** R.drawable resource id của icon được chọn (ví dụ R.drawable.ic_work). */
    @ColumnInfo(name = "icon_res_id", defaultValue = "0")
    private int iconResId;

    // Constructor cũ — @Ignore để Room không bị nhầm lẫn khi có nhiều constructor
    @Ignore
    public TodoList(String name, int colorRes) {
        this.name = name;
        this.colorRes = colorRes;
        this.iconResId = 0;
    }

    // Constructor chính — Room sẽ dùng constructor này để tái tạo object từ DB
    public TodoList(String name, int colorRes, int iconResId) {
        this.name = name;
        this.colorRes = colorRes;
        this.iconResId = iconResId;
    }

    // Getters & Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getColorRes() { return colorRes; }
    public void setColorRes(int colorRes) { this.colorRes = colorRes; }

    public int getIconResId() { return iconResId; }
    public void setIconResId(int iconResId) { this.iconResId = iconResId; }
}
