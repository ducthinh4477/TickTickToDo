package hcmute.edu.vn.tickticktodo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.repository.TaskRepository;

/**
 * ViewModel cho màn hình "Today".
 * Cung cấp dữ liệu (LiveData) cho UI và xử lý logic nghiệp vụ cơ bản.
 */
public class TaskViewModel extends AndroidViewModel {

    private final TaskRepository repository;
    private final LiveData<List<Task>> todayIncompleteTasks;
    private final LiveData<List<Task>> todayCompletedTasks;

    public TaskViewModel(@NonNull Application application) {
        super(application);
        repository = new TaskRepository(application);

        // Tính khoảng thời gian ngày hôm nay: [00:00:00.000 .. 00:00:00.000 ngày tiếp theo)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startOfDay = calendar.getTimeInMillis();

        calendar.add(Calendar.DAY_OF_MONTH, 1);
        long endOfDay = calendar.getTimeInMillis();

        todayIncompleteTasks = repository.getIncompleteTasks(startOfDay, endOfDay);
        todayCompletedTasks = repository.getCompletedTasks(startOfDay, endOfDay);
    }

    // ─── Observe ─────────────────────────────────────────────────────────────────

    public LiveData<List<Task>> getTodayIncompleteTasks() {
        return todayIncompleteTasks;
    }

    public LiveData<List<Task>> getTodayCompletedTasks() {
        return todayCompletedTasks;
    }

    public LiveData<List<Task>> getAllTasks() {
        return repository.getAllTasks();
    }

    public LiveData<Task> getTaskById(long taskId) {
        return repository.getTaskById(taskId);
    }

    // ─── Actions ─────────────────────────────────────────────────────────────────

    public void insert(Task task) {
        repository.insert(task);
    }

    public void update(Task task) {
        repository.update(task);
    }

    public void delete(Task task) {
        repository.delete(task);
    }

    /**
     * Đánh dấu task hoàn thành hoặc chưa hoàn thành.
     * Sử dụng query UPDATE trực tiếp trên DB thay vì load toàn bộ object,
     * giúp hiệu quả hơn và tránh race condition.
     *
     * @param task        Task cần cập nhật
     * @param isCompleted true = hoàn thành, false = chưa hoàn thành
     */
    public void markTaskAsCompleted(Task task, boolean isCompleted) {
        repository.markTaskAsCompleted(task.getId(), isCompleted);
    }

    public void deleteAllCompleted() {
        repository.deleteAllCompleted();
    }
}
