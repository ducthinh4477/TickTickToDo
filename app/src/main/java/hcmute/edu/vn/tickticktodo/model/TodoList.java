package hcmute.edu.vn.tickticktodo.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity đại diện cho một danh sách (list) trong ứng dụng.
 * Bảng "todo_lists" trong Room Database.
 *
 * Mỗi Task có thể thuộc về một TodoList thông qua trường listId.
 */
@Entity(tableName = "todo_lists")
public class TodoList {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "color_res")
    private int colorRes; // resource id hoặc color int

    // Constructor
    public TodoList(String name, int colorRes) {
        this.name = name;
        this.colorRes = colorRes;
    }

    // Getters & Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getColorRes() {
        return colorRes;
    }

    public void setColorRes(int colorRes) {
        this.colorRes = colorRes;
    }
}
