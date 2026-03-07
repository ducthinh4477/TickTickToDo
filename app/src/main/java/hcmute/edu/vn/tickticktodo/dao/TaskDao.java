package hcmute.edu.vn.tickticktodo.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hcmute.edu.vn.tickticktodo.model.Task;

/**
 * Data Access Object cho bảng tasks.
 * Cung cấp các query cơ bản: lấy danh sách, thêm, sửa, xóa.
 *
 * Các query lấy task theo ngày sử dụng khoảng thời gian [startOfDay, endOfDay)
 * để lọc chính xác các task có due_date trong ngày hôm nay.
 */
@Dao
public interface TaskDao {

    // ─── Lấy task chưa hoàn thành trong ngày (sắp xếp theo priority giảm dần) ───
    @Query("SELECT * FROM tasks " +
           "WHERE is_completed = 0 " +
           "AND due_date >= :startOfDay AND due_date < :endOfDay " +
           "ORDER BY priority DESC, due_date ASC")
    LiveData<List<Task>> getIncompleteTasks(long startOfDay, long endOfDay);

    // ─── Lấy task đã hoàn thành trong ngày ──────────────────────────────────────
    @Query("SELECT * FROM tasks " +
           "WHERE is_completed = 1 " +
           "AND due_date >= :startOfDay AND due_date < :endOfDay " +
           "ORDER BY due_date ASC")
    LiveData<List<Task>> getCompletedTasks(long startOfDay, long endOfDay);

    // ─── Lấy tất cả task (không lọc ngày) ───────────────────────────────────────
    @Query("SELECT * FROM tasks ORDER BY due_date ASC")
    LiveData<List<Task>> getAllTasks();

    // ─── Lấy một task theo id ────────────────────────────────────────────────────
    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    LiveData<Task> getTaskById(long taskId);

    // ─── Thêm task ──────────────────────────────────────────────────────────────
    @Insert
    long insert(Task task);

    // ─── Cập nhật task ──────────────────────────────────────────────────────────
    @Update
    void update(Task task);

    // ─── Xóa task ───────────────────────────────────────────────────────────────
    @Delete
    void delete(Task task);

    // ─── Đánh dấu trạng thái hoàn thành của task ──────────────────────────────
    @Query("UPDATE tasks SET is_completed = :isCompleted WHERE id = :taskId")
    void markTaskAsCompleted(long taskId, boolean isCompleted);

    // ─── Lấy tất cả task (hoàn thành + chưa hoàn thành) trong một ngày cụ thể ──
    // Dùng cho CalendarActivity: lọc theo khoảng [startOfDay, endOfDay)
    // Sắp xếp: chưa hoàn thành trước, sau đó theo priority giảm dần, cuối cùng theo due_date
    @Query("SELECT * FROM tasks " +
           "WHERE due_date >= :startOfDay AND due_date < :endOfDay " +
           "ORDER BY is_completed ASC, priority DESC, due_date ASC")
    LiveData<List<Task>> getTasksByDate(long startOfDay, long endOfDay);

    // ─── Xóa tất cả task đã hoàn thành ─────────────────────────────────────────
    @Query("DELETE FROM tasks WHERE is_completed = 1")
    void deleteAllCompleted();
}
