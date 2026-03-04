package hcmute.edu.vn.tickticktodo.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hcmute.edu.vn.tickticktodo.model.TodoList;

/**
 * Data Access Object cho bảng todo_lists.
 * Cung cấp CRUD cơ bản và query lấy tất cả danh sách.
 */
@Dao
public interface TodoListDao {

    @Query("SELECT * FROM todo_lists ORDER BY name ASC")
    LiveData<List<TodoList>> getAllLists();

    @Query("SELECT * FROM todo_lists WHERE id = :listId LIMIT 1")
    LiveData<TodoList> getListById(long listId);

    @Insert
    long insert(TodoList todoList);

    @Update
    void update(TodoList todoList);

    @Delete
    void delete(TodoList todoList);
}
