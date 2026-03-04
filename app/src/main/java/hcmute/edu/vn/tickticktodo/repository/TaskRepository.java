package hcmute.edu.vn.tickticktodo.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.dao.TodoListDao;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.model.TodoList;

/**
 * Repository đóng vai trò trung gian giữa ViewModel và nguồn dữ liệu (Room).
 * Mọi thao tác ghi (insert, update, delete) được thực thi trên background thread
 * thông qua ExecutorService.
 */
public class TaskRepository {

    private final TaskDao taskDao;
    private final TodoListDao todoListDao;
    private final ExecutorService executor;

    public TaskRepository(Application application) {
        TaskDatabase db = TaskDatabase.getInstance(application);
        taskDao = db.taskDao();
        todoListDao = db.todoListDao();
        executor = Executors.newSingleThreadExecutor();
    }

    // ─── READ ────────────────────────────────────────────────────────────────────

    public LiveData<List<Task>> getIncompleteTasks(long startOfDay, long endOfDay) {
        return taskDao.getIncompleteTasks(startOfDay, endOfDay);
    }

    public LiveData<List<Task>> getCompletedTasks(long startOfDay, long endOfDay) {
        return taskDao.getCompletedTasks(startOfDay, endOfDay);
    }

    public LiveData<List<Task>> getAllTasks() {
        return taskDao.getAllTasks();
    }

    public LiveData<Task> getTaskById(long taskId) {
        return taskDao.getTaskById(taskId);
    }

    // ─── WRITE (background thread) ──────────────────────────────────────────────

    public void insert(Task task) {
        executor.execute(() -> taskDao.insert(task));
    }

    public void update(Task task) {
        executor.execute(() -> taskDao.update(task));
    }

    public void delete(Task task) {
        executor.execute(() -> taskDao.delete(task));
    }

    public void markTaskAsCompleted(long taskId, boolean isCompleted) {
        executor.execute(() -> taskDao.markTaskAsCompleted(taskId, isCompleted));
    }

    public void deleteAllCompleted() {
        executor.execute(taskDao::deleteAllCompleted);
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
