package hcmute.edu.vn.tickticktodo.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.dao.TodoListDao;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.ReminderScheduler;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.model.TodoList;

/**
 * Repository đóng vai trò trung gian giữa ViewModel và nguồn dữ liệu (Room).
 * Mọi thao tác ghi (insert, update, delete) được thực thi trên background thread
 * thông qua ExecutorService.
 *
 * Repository cũng tự động gọi ReminderScheduler để set/cancel alarm nhắc nhở
 * mỗi khi task được thêm, cập nhật hoặc xóa.
 */
public class TaskRepository {

    private final TaskDao taskDao;
    private final TodoListDao todoListDao;
    private final ExecutorService executor;
    private final Application application; // giữ để pass Context cho ReminderScheduler
    private final ActivityLogRepository logRepository;

    public LiveData<List<Task>> getAllCompletedTasksLog() {
        return taskDao.getAllCompletedTasksLog();
    }

    public LiveData<List<Task>> getAllOverdueTasksLog() {
        long now = System.currentTimeMillis();
        return taskDao.getAllOverdueTasksLog(now);
    }

    public TaskRepository(Application application) {
        TaskDatabase db = TaskDatabase.getInstance(application);
        taskDao = db.taskDao();
        todoListDao = db.todoListDao();
        executor = Executors.newSingleThreadExecutor();
        this.application = application;
        this.logRepository = new ActivityLogRepository(application);
    }

    // ─── READ ────────────────────────────────────────────────────────────────────

    public LiveData<List<Task>> getIncompleteTasks(long startOfDay, long endOfDay) {
        return taskDao.getIncompleteTasks(startOfDay, endOfDay);
    }

    public LiveData<List<Task>> getCompletedTasks(long startOfDay, long endOfDay) {
        return taskDao.getCompletedTasks(startOfDay, endOfDay);
    }

    public LiveData<List<Task>> getTasksByDate(long startOfDay, long endOfDay) {
        return taskDao.getTasksByDate(startOfDay, endOfDay);
    }

    public LiveData<List<Task>> getTasksByDateRange(long startDate, long endDate) {
        return taskDao.getTasksByDateRange(startDate, endDate);
    }

    public LiveData<List<Task>> getAllTasks() {
        return taskDao.getAllTasks();
    }

    public LiveData<List<Task>> getTasksForNext7Days() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        cal.add(java.util.Calendar.DAY_OF_MONTH, 8); 
        long endOf7thDay = cal.getTimeInMillis();
        return taskDao.getTasksForNext7Days(startOfDay, endOf7thDay);
    }

    public LiveData<List<Task>> getOverdueTasks() {
        long now = System.currentTimeMillis();
        long h24 = now - (24 * 60 * 60 * 1000L);
        return taskDao.getOverdueTasks(now, h24);
    }

    public LiveData<Task> getTaskById(long taskId) {
        return taskDao.getTaskById(taskId);
    }

    // ─── WRITE (background thread) ──────────────────────────────────────────────

    public void insert(Task task) {
        executor.execute(() -> {
            long newId = taskDao.insert(task);
            // Set alarm: gán id vừa insert rồi schedule
            task.setId(newId);
            ReminderScheduler.scheduleReminder(application, task);
            
            // Nhật ký
            logRepository.insertLog("TẠO MỚI", task.getTitle());

            // Notification if due today
            checkAndNotifyIfToday(task);
        });
    }

    private void checkAndNotifyIfToday(Task task) {
        if (task.getDueDate() == null) return;

        java.util.Calendar todayStart = java.util.Calendar.getInstance();
        todayStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
        todayStart.set(java.util.Calendar.MINUTE, 0);
        todayStart.set(java.util.Calendar.SECOND, 0);
        todayStart.set(java.util.Calendar.MILLISECOND, 0);

        java.util.Calendar todayEnd = (java.util.Calendar) todayStart.clone();
        todayEnd.add(java.util.Calendar.DAY_OF_YEAR, 1);

        long due = task.getDueDate();
        if (due >= todayStart.getTimeInMillis() && due < todayEnd.getTimeInMillis()) {
            hcmute.edu.vn.tickticktodo.helper.NotificationHelper.showTaskNotification(
                    application,
                    "Bạn có công việc mới cho hôm nay",
                    task.getTitle(),
                    (int) task.getId()
            );
        }
    }

    public void update(Task task) {
        executor.execute(() -> {
            taskDao.update(task);
            
            // Nhật ký
            logRepository.insertLog("CẬP NHẬT", task.getTitle());
            
            // Đặt lại alarm (dueDate có thể thay đổi), hoặc cancel nếu đã hoàn thành
            if (task.isCompleted()) {
                ReminderScheduler.cancelReminder(application, task.getId());
            } else {
                ReminderScheduler.scheduleReminder(application, task);
            }
        });
    }

    public void delete(Task task) {
        executor.execute(() -> {
            taskDao.delete(task);
            
            // Nhật ký
            logRepository.insertLog("XÓA", task.getTitle());
            
            ReminderScheduler.cancelReminder(application, task.getId()); // huỷ alarm
        });
    }

    public void markTaskAsCompletedWithDate(long taskId, boolean isCompleted, Long completedDate) {
        executor.execute(() -> {
            taskDao.markTaskAsCompletedWithDate(taskId, isCompleted, completedDate);
            
            // Nhật ký
            logRepository.insertLog(isCompleted ? "HOÀN THÀNH" : "CHƯA HOÀN THÀNH", "Task ID: " + taskId);
            
            if (isCompleted) {
                ReminderScheduler.cancelReminder(application, taskId); // hoàn thành → huỷ alarm
            }
        });
    }

    public void deleteAllCompleted() {
        executor.execute(taskDao::deleteAllCompleted);
        // Không cần cancel từng alarm vì task đã hoàn thành đã bị cancel khi mark complete
    }

    // ─── SORT ────────────────────────────────────────────────────────────────────

    public LiveData<List<Task>> getAllTasksSortByDateAsc() {
        return taskDao.getAllTasksSortByDateAsc();
    }

    public LiveData<List<Task>> getAllTasksSortByDateDesc() {
        return taskDao.getAllTasksSortByDateDesc();
    }

    public LiveData<List<Task>> getAllTasksSortByPriority() {
        return taskDao.getAllTasksSortByPriority();
    }

    // ─── Moodle ──────────────────────────────────────────────────────────────────

    public LiveData<List<Task>> getUpcomingMoodleTasks(long currentTime) {
        return taskDao.getUpcomingMoodleTasks(currentTime);
    }

    public LiveData<Integer> getUnreadMoodleTasksCount() {
        return taskDao.getUnreadMoodleTasksCount();
    }

    public LiveData<List<Task>> getAllTasksSortByTitle() {
        return taskDao.getAllTasksSortByTitle();
    }

    public LiveData<List<Task>> getAllTasksSortByCustom() {
        return taskDao.getAllTasksSortByCustom();
    }

    public void updateOrderIndex(long taskId, int orderIndex) {
        executor.execute(() -> taskDao.updateOrderIndex(taskId, orderIndex));
    }

    // ─── STATISTICS ─────────────────────────────────────────────────────────────

    public LiveData<Integer> countTotalCompleted() {
        return taskDao.countTotalCompleted();
    }

    public LiveData<Integer> countCompletedToday(long startOfDay, long endOfDay) {
        return taskDao.countCompletedToday(startOfDay, endOfDay);
    }

    public LiveData<Integer> countCompletedLast7Days(long sevenDaysAgo, long endOfToday) {
        return taskDao.countCompletedLast7Days(sevenDaysAgo, endOfToday);
    }

    public LiveData<Integer> countTotalTasksToday(long startOfDay, long endOfDay) {
        return taskDao.countTotalTasksToday(startOfDay, endOfDay);
    }


    // ─── TodoList CRUD ────────────────────────────────────────────────────────────

    public LiveData<List<TodoList>> getAllLists() {
        return todoListDao.getAllLists();
    }

    public LiveData<TodoList> getListById(long listId) {
        return todoListDao.getListById(listId);
    }

    public void insertList(TodoList todoList) {
        executor.execute(() -> todoListDao.insert(todoList));
    }

    public void updateList(TodoList todoList) {
        executor.execute(() -> todoListDao.update(todoList));
    }

    public void deleteList(TodoList todoList) {
        executor.execute(() -> todoListDao.delete(todoList));
    }
}
