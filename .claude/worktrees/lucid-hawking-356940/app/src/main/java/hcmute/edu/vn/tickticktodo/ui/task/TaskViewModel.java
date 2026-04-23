package hcmute.edu.vn.doinbot.ui.task;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.doinbot.model.Subtask;
import hcmute.edu.vn.doinbot.model.Task;
import hcmute.edu.vn.doinbot.model.TodoList;
import hcmute.edu.vn.doinbot.data.repository.TaskRepository;

/**
 * ViewModel cho màn hình "Today" và các tính năng Sort / Statistics.
 * Cung cấp dữ liệu (LiveData) cho UI và xử lý logic nghiệp vụ cơ bản.
 */
public class TaskViewModel extends AndroidViewModel {

    // ─── Sort modes ──────────────────────────────────────────────────────────────
    public static final int SORT_BY_DATE_ASC  = 0;
    public static final int SORT_BY_DATE_DESC = 1;
    public static final int SORT_BY_PRIORITY  = 2;

    private final TaskRepository repository;
    private final LiveData<List<Task>> todayIncompleteTasks;
    private final LiveData<List<Task>> todayCompletedTasks;
    private final LiveData<List<Task>> next7DaysTasks;
    private final LiveData<List<Task>> overdueTasks;
    private final LiveData<List<Task>> todayAllTasks;

    // Sort: MutableLiveData giữ chế độ Sort hiện tại, switchMap tự cập nhật danh sách
    private final MutableLiveData<Integer> sortMode = new MutableLiveData<>(SORT_BY_DATE_ASC);
    private final LiveData<List<Task>> sortedAllTasks;

    // Thời gian ngày hôm nay (dùng chung cho nhiều query)
    private final long startOfDay;
    private final long endOfDay;

    public TaskViewModel(@NonNull Application application) {
        super(application);
        repository = new TaskRepository(application);

        // Tính khoảng thời gian ngày hôm nay: [00:00:00.000 .. 00:00:00.000 ngày tiếp theo)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        startOfDay = calendar.getTimeInMillis();

        calendar.add(Calendar.DAY_OF_MONTH, 1);
        endOfDay = calendar.getTimeInMillis();

        todayIncompleteTasks = repository.getIncompleteTasks(startOfDay, endOfDay);
        todayCompletedTasks = repository.getCompletedTasks(startOfDay, endOfDay);
        todayAllTasks = repository.getTasksByDateRange(startOfDay, endOfDay);
        next7DaysTasks = repository.getTasksForNext7Days();
        overdueTasks = repository.getOverdueTasks();

        // switchMap: mỗi khi sortMode thay đổi, tự động chuyển LiveData source
        sortedAllTasks = Transformations.switchMap(sortMode, mode -> {
            switch (mode) {
                case SORT_BY_DATE_DESC:
                    return repository.getAllTasksSortByDateDesc();
                case SORT_BY_PRIORITY:
                    return repository.getAllTasksSortByPriority();
                case SORT_BY_DATE_ASC:
                default:
                    return repository.getAllTasksSortByDateAsc();
            }
        });
    }

    // ─── Sort ────────────────────────────────────────────────────────────────────

    /**
     * Thay đổi chế độ Sort. UI sẽ tự động cập nhật qua LiveData.
     *
     * Ví dụ gọi từ Activity/Fragment:
     *   taskViewModel.setSortMode(TaskViewModel.SORT_BY_PRIORITY);
     */
    public void setSortMode(int mode) {
        sortMode.setValue(mode);
    }

    public int getCurrentSortMode() {
        Integer mode = sortMode.getValue();
        return mode != null ? mode : SORT_BY_DATE_ASC;
    }

    public LiveData<Integer> getSortModeLiveData() {
        return sortMode;
    }

    /**
     * LiveData danh sách tất cả task đã được sắp xếp theo chế độ hiện tại.
     * Tự động thay đổi khi gọi setSortMode().
     */
    public LiveData<List<Task>> getSortedAllTasks() {
        return sortedAllTasks;
    }

    public LiveData<List<Task>> getNext7DaysTasks() {
        return next7DaysTasks;
    }

    public LiveData<List<Task>> getOverdueTasks() {
        return overdueTasks;
    }

    // ─── Observe ─────────────────────────────────────────────────────────────────

    public LiveData<List<Task>> getTodayIncompleteTasks() {
        return todayIncompleteTasks;
    }

    public LiveData<List<Task>> getTodayCompletedTasks() {
        return todayCompletedTasks;
    }

    public LiveData<List<Task>> getAllCompletedTasksLog() {
        return repository.getAllCompletedTasksLog();
    }

    public LiveData<List<Task>> getAllOverdueTasksLog() {
        return repository.getAllOverdueTasksLog();
    }

    public LiveData<List<Task>> getTodayAllTasks() {
        return todayAllTasks;
    }

    public LiveData<List<Task>> getAllTasks() {
        return repository.getAllTasks();
    }

    /**
     * Truy vấn tất cả task (hoàn thành + chưa hoàn thành) trong một ngày cụ thể.
     * CalendarActivity gọi method này mỗi khi user chọn ngày khác trên CalendarView.
     *
     * @param startOfDay timestamp đầu ngày (00:00:00.000)
     * @param endOfDay   timestamp đầu ngày kế tiếp (exclusive)
     */
    public LiveData<List<Task>> getTasksByDate(long startOfDay, long endOfDay) {
        return repository.getTasksByDate(startOfDay, endOfDay);
    }

    /**
     * Truy vấn tất cả task trong một khoảng ngày tuỳ ý (dùng cho Calendar Month Grid).
     * CalendarActivity gọi method này một lần cho toàn bộ cửa sổ 42 ngày (6 tuần)
     * rồi phân bổ task vào từng ô ngày trong CalendarAdapter.
     *
     * @param startDate timestamp bắt đầu (thường = ngày đầu tuần đầu tiên hiển thị)
     * @param endDate   timestamp kết thúc (exclusive, thường = ngày sau tuần cuối)
     */
    public LiveData<List<Task>> getTasksByDateRange(long startDate, long endDate) {
        return repository.getTasksByDateRange(startDate, endDate);
    }

    public LiveData<Task> getTaskById(long taskId) {
        return repository.getTaskById(taskId);
    }

    public LiveData<List<Subtask>> getSubtasksByTaskId(long taskId) {
        return repository.getSubtasksByTaskId(taskId);
    }

    // ─── Moodle ──────────────────────────────────────────────────────────────────

    public LiveData<List<Task>> getUpcomingMoodleTasks(long currentTime) {
        return repository.getUpcomingMoodleTasks(currentTime);
    }

    public LiveData<Integer> getUnreadMoodleTasksCount() {
        return repository.getUnreadMoodleTasksCount();
    }

    // ─── Actions ─────────────────────────────────────────────────────────────────

    public void insert(Task task) {
        repository.insert(task);
    }

    public void insertBatch(List<Task> tasks, Runnable onComplete) {
        repository.insertBatch(tasks, onComplete);
    }

    public void update(Task task) {
        repository.update(task);
    }

    public void applyAiBreakdownToSubtasks(long taskId, List<String> steps, Runnable onComplete) {
        repository.applyAiBreakdownSubtasks(taskId, steps, onComplete);
    }

    public void markSubtaskCompleted(Subtask subtask, boolean isCompleted) {
        if (subtask == null) {
            return;
        }
        repository.markSubtaskCompleted(subtask.getId(), subtask.getTaskId(), isCompleted);
    }

    public void setSubtaskApproved(Subtask subtask, boolean isApproved) {
        if (subtask == null) {
            return;
        }
        repository.setSubtaskApproved(subtask.getId(), subtask.getTaskId(), isApproved);
    }

    public void updateSubtaskPriority(Subtask subtask, int priority) {
        if (subtask == null) {
            return;
        }
        repository.updateSubtaskPriority(subtask.getId(), subtask.getTaskId(), priority);
    }

    public void moveUnfinishedTasksToTomorrow(List<Long> taskIds, Runnable onComplete) {
        repository.rescheduleTasksToTomorrow(taskIds, onComplete);
    }

    public void delete(Task task) {
        repository.delete(task);
    }

    /**
     * Đánh dấu task hoàn thành hoặc chưa hoàn thành.
     * Ghi nhận completedDate khi đánh dấu hoàn thành (dùng cho Statistics).
     *
     * @param task        Task cần cập nhật
     * @param isCompleted true = hoàn thành, false = chưa hoàn thành
     */
    public void markTaskAsCompleted(Task task, boolean isCompleted) {
        Long completedDate = isCompleted ? System.currentTimeMillis() : null;
        repository.markTaskAsCompletedWithDate(task.getId(), isCompleted, completedDate);
    }

    public void deleteAllCompleted() {
        repository.deleteAllCompleted();
    }

    public void updateOrderIndex(long taskId, int orderIndex) {
        repository.updateOrderIndex(taskId, orderIndex);
    }

    // ─── Statistics ──────────────────────────────────────────────────────────────

    public LiveData<Integer> countTotalCompleted() {
        return repository.countTotalCompleted();
    }

    public LiveData<Integer> countCompletedToday() {
        return repository.countCompletedToday(startOfDay, endOfDay);
    }

    public LiveData<Integer> countCompletedLast7Days() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        long endOfToday = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        long sevenDaysAgo = cal.getTimeInMillis();
        return repository.countCompletedLast7Days(sevenDaysAgo, endOfToday);
    }

    public LiveData<Integer> countTotalTasksToday() {
        return repository.countTotalTasksToday(startOfDay, endOfDay);
    }

    // ─── TodoList ────────────────────────────────────────────────────────────────

    public LiveData<List<TodoList>> getAllLists() {
        return repository.getAllLists();
    }

    public LiveData<TodoList> getListById(long listId) {
        return repository.getListById(listId);
    }

    public void insertList(TodoList todoList) {
        repository.insertList(todoList);
    }

    public void updateList(TodoList todoList) {
        repository.updateList(todoList);
    }

    public void deleteList(TodoList todoList) {
        repository.deleteList(todoList);
    }
}
