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

    // ─── Lấy tất cả task trong một khoảng thời gian tuỳ ý (cho Calendar Month Grid) ─
    // startDate và endDate có thể trải dài 42 ngày (6 tuần) bao trọn lưới lịch tháng.
    // Dùng để nạp 1 lần cho cả tháng, sau đó phân bổ vào từng ô ngày trong Adapter.
    @Query("SELECT * FROM tasks " +
           "WHERE due_date >= :startDate AND due_date < :endDate " +
           "ORDER BY due_date ASC")
    LiveData<List<Task>> getTasksByDateRange(long startDate, long endDate);

    // ─── Xóa tất cả task đã hoàn thành ─────────────────────────────────────────
    @Query("DELETE FROM tasks WHERE is_completed = 1")
    void deleteAllCompleted();

    // ─── Lấy tất cả task (đồng bộ, không LiveData) — dùng cho BootReceiver ────
    @Query("SELECT * FROM tasks")
    List<Task> getAllTasksSync();

    // ══════════════════════════════════════════════════════════════════════════════
    // SORT QUERIES — Sắp xếp danh sách task theo các tiêu chí khác nhau
    // ══════════════════════════════════════════════════════════════════════════════

    // ─── Sort theo ngày (due_date tăng dần, NULL cuối cùng) ────────────────────
    @Query("SELECT * FROM tasks ORDER BY " +
           "CASE WHEN due_date IS NULL THEN 1 ELSE 0 END, due_date ASC")
    LiveData<List<Task>> getAllTasksSortByDateAsc();

    // ─── Sort theo ngày (due_date giảm dần, NULL cuối cùng) ────────────────────
    @Query("SELECT * FROM tasks ORDER BY " +
           "CASE WHEN due_date IS NULL THEN 1 ELSE 0 END, due_date DESC")
    LiveData<List<Task>> getAllTasksSortByDateDesc();

    // ─── Sort theo độ ưu tiên (cao → thấp) ─────────────────────────────────────
    @Query("SELECT * FROM tasks ORDER BY priority DESC, due_date ASC")
    LiveData<List<Task>> getAllTasksSortByPriority();

    // ─── Sort theo tên (A-Z) ────────────────────────────────────────────────────
    @Query("SELECT * FROM tasks ORDER BY title COLLATE NOCASE ASC")
    LiveData<List<Task>> getAllTasksSortByTitle();

    // ─── Sort tùy chỉnh (theo order_index, dùng cho kéo thả) ───────────────────
    @Query("SELECT * FROM tasks ORDER BY order_index ASC, due_date ASC")
    LiveData<List<Task>> getAllTasksSortByCustom();

    // ─── Cập nhật thứ tự kéo thả (order_index) ────────────────────────────────
    @Query("UPDATE tasks SET order_index = :orderIndex WHERE id = :taskId")
    void updateOrderIndex(long taskId, int orderIndex);

    // ══════════════════════════════════════════════════════════════════════════════
    // STATISTICS QUERIES — Thống kê
    // ══════════════════════════════════════════════════════════════════════════════

    // ─── Đếm tổng số task đã hoàn thành ────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 1")
    LiveData<Integer> countTotalCompleted();

    // ─── Đếm task hoàn thành hôm nay (theo completed_date) ────────────────────
    @Query("SELECT COUNT(*) FROM tasks " +
           "WHERE is_completed = 1 " +
           "AND completed_date >= :startOfDay AND completed_date < :endOfDay")
    LiveData<Integer> countCompletedToday(long startOfDay, long endOfDay);

    // ─── Đếm task hoàn thành trong 7 ngày qua ─────────────────────────────────
    @Query("SELECT COUNT(*) FROM tasks " +
           "WHERE is_completed = 1 " +
           "AND completed_date >= :sevenDaysAgo AND completed_date < :endOfToday")
    LiveData<Integer> countCompletedLast7Days(long sevenDaysAgo, long endOfToday);

    // ─── Đếm tổng số task hôm nay (cho tính Completion Rate) ──────────────────
    @Query("SELECT COUNT(*) FROM tasks " +
           "WHERE due_date >= :startOfDay AND due_date < :endOfDay")
    LiveData<Integer> countTotalTasksToday(long startOfDay, long endOfDay);

    // ─── Cập nhật completed_date khi đánh dấu hoàn thành ──────────────────────
    @Query("UPDATE tasks SET is_completed = :isCompleted, " +
           "completed_date = CASE WHEN :isCompleted = 1 THEN :completedDate ELSE NULL END " +
           "WHERE id = :taskId")
    void markTaskAsCompletedWithDate(long taskId, boolean isCompleted, Long completedDate);

    // Mới: Tìm unique task đã sync từ trường
    @Query("SELECT * FROM tasks WHERE title = :title AND due_date = :dueDate LIMIT 1")
    Task findTaskByTitleAndDate(String title, Long dueDate);

    @Query("SELECT * FROM tasks WHERE due_date >= :now AND due_date < :next7Days ORDER BY due_date ASC")
    LiveData<List<Task>> getTasksForNext7Days(long now, long next7Days);

    @Query("SELECT * FROM tasks WHERE due_date < :now AND is_completed = 0 AND due_date IS NOT NULL ORDER BY due_date ASC")
    LiveData<List<Task>> getOverdueTasks(long now);

    // Xóa các task đã hoàn thành cũ hơn thời gian cho trước (Auto-archive)
    @Query("DELETE FROM tasks WHERE is_completed = 1 AND completed_date < :threshold")
    int deleteOldCompletedTasks(long threshold);

    // ─── Sync queries dành cho Worker (không LiveData) ──────────────────────────

    // Lấy task chưa hoàn thành trong ngày hôm nay (đồng bộ) — cho DailyDigestWorker
    @Query("SELECT * FROM tasks " +
           "WHERE is_completed = 0 " +
           "AND due_date >= :startOfDay AND due_date < :endOfDay " +
           "ORDER BY priority DESC, due_date ASC")
    List<Task> getIncompleteTasksForDaySync(long startOfDay, long endOfDay);

    // Lấy task quá hạn chưa hoàn thành (đồng bộ) — cho OverdueCheckWorker
    @Query("SELECT * FROM tasks " +
           "WHERE is_completed = 0 " +
           "AND due_date IS NOT NULL " +
           "AND due_date < :now " +
           "ORDER BY due_date ASC")
    List<Task> getOverdueIncompleteTasksSync(long now);

    // ─── Moodle Queries ─────────────────────────────────────────────────────────

    // Lấy bài tập Moodle sắp đến hạn (chưa hoàn thành)
    @Query("SELECT * FROM tasks WHERE source = 'Moodle' AND is_completed = 0 AND due_date >= :currentTime ORDER BY due_date ASC")
    LiveData<List<Task>> getUpcomingMoodleTasks(long currentTime);

    // Đếm tổng số bài Moodle chưa hoàn thành làm cảnh báo
    @Query("SELECT COUNT(*) FROM tasks WHERE source = 'Moodle' AND is_completed = 0")
    LiveData<Integer> getUnreadMoodleTasksCount();
}
