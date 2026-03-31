import re

file_dao = "app/src/main/java/hcmute/edu/vn/tickticktodo/dao/TaskDao.java"
with open(file_dao, "r", encoding="utf-8") as f:
    text = f.read()

# adding methods for logs
new_methods = """
    // --- Lịch sử/Nhật ký (Logs) ---
    @Query("SELECT * FROM tasks WHERE is_completed = 1 ORDER BY completed_date DESC")
    LiveData<List<Task>> getAllCompletedTasksLog();

    @Query("SELECT * FROM tasks WHERE due_date < :now AND is_completed = 0 AND due_date IS NOT NULL ORDER BY due_date DESC")
    LiveData<List<Task>> getAllOverdueTasksLog(long now);
"""
if "getAllCompletedTasksLog" not in text:
    text = text.replace("public interface TaskDao {", "public interface TaskDao {\n" + new_methods)


with open(file_dao, "w", encoding="utf-8") as f:
    f.write(text)
print("DAO updated")