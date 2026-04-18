package hcmute.edu.vn.tickticktodo.data.repository;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.lifecycle.LiveData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.agent.core.AgentEventBus;
import hcmute.edu.vn.tickticktodo.data.dao.SubtaskDao;
import hcmute.edu.vn.tickticktodo.data.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.data.dao.TodoListDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.Subtask;
import hcmute.edu.vn.tickticktodo.helper.ReminderScheduler;
import hcmute.edu.vn.tickticktodo.helper.UserStatsManager;
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
    private final SubtaskDao subtaskDao;
    private final TodoListDao todoListDao;
    private final ExecutorService executor;
    private final Application application; // giữ để pass Context cho ReminderScheduler
    private final ActivityLogRepository logRepository;
    private final UserStatsManager userStatsManager;
    private final AgentEventBus eventBus;

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
        subtaskDao = db.subtaskDao();
        todoListDao = db.todoListDao();
        executor = Executors.newSingleThreadExecutor();
        this.application = application;
        this.logRepository = new ActivityLogRepository(application);
        this.userStatsManager = UserStatsManager.getInstance(application);
        this.eventBus = AgentEventBus.getInstance();
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

    public LiveData<List<Subtask>> getSubtasksByTaskId(long taskId) {
        return subtaskDao.getSubtasksByTaskId(taskId);
    }

    // ─── WRITE (background thread) ──────────────────────────────────────────────

    public void insert(Task task) {
        insert(task, null);
    }

    public void insert(Task task, Runnable onComplete) {
        executor.execute(() -> {
            long newId = taskDao.insert(task);
            // Set alarm: gán id vừa insert rồi schedule
            task.setId(newId);
            ReminderScheduler.scheduleReminder(application, task);
            
            // Nhật ký
            logRepository.insertLog("TẠO MỚI", task.getTitle());

            // Notification if due today
            checkAndNotifyIfToday(task);

            publishTaskEvent(AgentEvent.TYPE_TASK_CREATED, task);

            postToMain(onComplete);
        });
    }

    public void insertBatch(List<Task> tasks, Runnable onComplete) {
        if (tasks == null || tasks.isEmpty()) {
            postToMain(onComplete);
            return;
        }

        executor.execute(() -> {
            List<Long> ids = taskDao.insertAll(tasks);
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                if (ids != null && i < ids.size()) {
                    task.setId(ids.get(i));
                }
                if (!task.isCompleted()) {
                    ReminderScheduler.scheduleReminder(application, task);
                }
                publishTaskEvent(AgentEvent.TYPE_TASK_CREATED, task);
            }

            logRepository.insertLog("TẠO HÀNG LOẠT", "Số lượng: " + tasks.size());
            postToMain(onComplete);
        });
    }

    public void rescheduleTasksToTomorrow(List<Long> taskIds, Runnable onComplete) {
        if (taskIds == null || taskIds.isEmpty()) {
            postToMain(onComplete);
            return;
        }

        executor.execute(() -> {
            Calendar nextDay = Calendar.getInstance();
            nextDay.add(Calendar.DAY_OF_MONTH, 1);
            nextDay.set(Calendar.HOUR_OF_DAY, 9);
            nextDay.set(Calendar.MINUTE, 0);
            nextDay.set(Calendar.SECOND, 0);
            nextDay.set(Calendar.MILLISECOND, 0);
            long newDueDate = nextDay.getTimeInMillis();

            taskDao.updateDueDateForTaskIds(taskIds, newDueDate);

            for (Long taskId : taskIds) {
                Task task = taskDao.getTaskByIdSync(taskId);
                if (task != null && !task.isCompleted()) {
                    ReminderScheduler.scheduleReminder(application, task);
                }
                publishTaskEvent(AgentEvent.TYPE_TASK_RESCHEDULED, task);
            }

            logRepository.insertLog("DỜI CÔNG VIỆC", "Số lượng: " + taskIds.size());
            postToMain(onComplete);
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
            Task previousTask = taskDao.getTaskByIdSync(task.getId());
            boolean wasCompleted = previousTask != null && previousTask.isCompleted();

            taskDao.update(task);

            handleCompletionTransition(wasCompleted, task.isCompleted());
            
            // Nhật ký
            logRepository.insertLog("CẬP NHẬT", task.getTitle());
            
            // Đặt lại alarm (dueDate có thể thay đổi), hoặc cancel nếu đã hoàn thành
            if (task.isCompleted()) {
                ReminderScheduler.cancelReminder(application, task.getId());
            } else {
                ReminderScheduler.scheduleReminder(application, task);
            }

            publishTaskEvent(task.isCompleted() && !wasCompleted
                    ? AgentEvent.TYPE_TASK_COMPLETED
                    : AgentEvent.TYPE_TASK_UPDATED, task);
        });
    }

    public void delete(Task task) {
        executor.execute(() -> {
            taskDao.delete(task);
            
            // Nhật ký
            logRepository.insertLog("XÓA", task.getTitle());
            
            ReminderScheduler.cancelReminder(application, task.getId()); // huỷ alarm
            publishTaskEvent(AgentEvent.TYPE_TASK_DELETED, task);
        });
    }

    public void applyAiBreakdownSubtasks(long taskId, List<String> steps, Runnable onComplete) {
        executor.execute(() -> {
            subtaskDao.deleteUnapprovedByTaskId(taskId);

            List<Subtask> pendingSubtasks = new ArrayList<>();
            Integer maxOrderIndex = subtaskDao.getMaxOrderIndex(taskId);
            int nextOrderIndex = maxOrderIndex == null ? 0 : maxOrderIndex + 1;

            if (steps != null) {
                for (String step : steps) {
                    if (step == null) {
                        continue;
                    }
                    String cleanStep = step.trim();
                    if (cleanStep.isEmpty()) {
                        continue;
                    }

                    Subtask subtask = new Subtask(taskId, cleanStep, false, false, 0, nextOrderIndex++);
                    pendingSubtasks.add(subtask);
                }
            }

            if (!pendingSubtasks.isEmpty()) {
                subtaskDao.insertAll(pendingSubtasks);
            }

            taskDao.touchTask(taskId);
            postToMain(onComplete);
        });
    }

    public void markSubtaskCompleted(long subtaskId, long taskId, boolean isCompleted) {
        executor.execute(() -> {
            subtaskDao.markSubtaskCompleted(subtaskId, isCompleted);
            taskDao.touchTask(taskId);
        });
    }

    public void setSubtaskApproved(long subtaskId, long taskId, boolean isApproved) {
        executor.execute(() -> {
            subtaskDao.setSubtaskApproved(subtaskId, isApproved);
            taskDao.touchTask(taskId);
        });
    }

    public void updateSubtaskPriority(long subtaskId, long taskId, int priority) {
        executor.execute(() -> {
            int safePriority = Math.max(0, Math.min(3, priority));
            subtaskDao.updateSubtaskPriority(subtaskId, safePriority);
            taskDao.touchTask(taskId);
        });
    }

    public void markTaskAsCompletedWithDate(long taskId, boolean isCompleted, Long completedDate) {
        executor.execute(() -> {
            Task previousTask = taskDao.getTaskByIdSync(taskId);
            boolean wasCompleted = previousTask != null && previousTask.isCompleted();

            taskDao.markTaskAsCompletedWithDate(taskId, isCompleted, completedDate);

            handleCompletionTransition(wasCompleted, isCompleted);
            
            // Nhật ký
            logRepository.insertLog(isCompleted ? "HOÀN THÀNH" : "CHƯA HOÀN THÀNH", "Task ID: " + taskId);
            
            if (isCompleted) {
                ReminderScheduler.cancelReminder(application, taskId); // hoàn thành → huỷ alarm
            }

            Task latestTask = taskDao.getTaskByIdSync(taskId);
            publishTaskEvent(isCompleted ? AgentEvent.TYPE_TASK_COMPLETED : AgentEvent.TYPE_TASK_UPDATED, latestTask);
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

    private void handleCompletionTransition(boolean wasCompleted, boolean isCompleted) {
        if (!wasCompleted && isCompleted) {
            UserStatsManager.XpResult result = userStatsManager.addXp(10, true);
            if (result.levelUp) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(
                                application,
                                application.getString(hcmute.edu.vn.tickticktodo.R.string.level_up_toast, result.stats.level),
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }
        } else if (wasCompleted && !isCompleted) {
            userStatsManager.addXp(-10, false);
        }
    }

    private void postToMain(Runnable callback) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(callback);
        }
    }

    private void publishTaskEvent(String eventType, Task task) {
        if (task == null) {
            return;
        }

        JSONObject payload = new JSONObject();
        safePut(payload, "taskId", task.getId());
        safePut(payload, "title", task.getTitle());
        safePut(payload, "dueDate", task.getDueDate());
        safePut(payload, "priority", task.getPriority());
        safePut(payload, "completed", task.isCompleted());
        safePut(payload, "source", task.getSource());
        eventBus.publish(AgentEvent.now(eventType, "TaskRepository", payload));
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
