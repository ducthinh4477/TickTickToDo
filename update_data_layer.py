import re

dao_path = "app/src/main/java/hcmute/edu/vn/tickticktodo/dao/TaskDao.java"
repo_path = "app/src/main/java/hcmute/edu/vn/tickticktodo/repository/TaskRepository.java"
vm_path = "app/src/main/java/hcmute/edu/vn/tickticktodo/viewmodel/TaskViewModel.java"

# 1. Update DAO
with open(dao_path, "r", encoding="utf-8") as f:
    dao_text = f.read()

# Update getOverdueTasks with 24h param
dao_text = dao_text.replace(
    "@Query(\"SELECT * FROM tasks WHERE due_date < :now AND is_completed = 0 AND due_date IS NOT NULL ORDER BY due_date ASC\")\n    LiveData<List<Task>> getOverdueTasks(long now);",
    "@Query(\"SELECT * FROM tasks WHERE due_date < :now AND due_date >= :twentyFourHoursAgo AND is_completed = 0 AND due_date IS NOT NULL ORDER BY due_date ASC\")\n    LiveData<List<Task>> getOverdueTasks(long now, long twentyFourHoursAgo);"
)

# Replace countCompletedToday with countCompletedRecent / getCompletedRecent
dao_text = dao_text.replace(
    "LiveData<List<Task>> getCompletedTasks(long startOfDay, long endOfDay);",
    "LiveData<List<Task>> getCompletedTasks(long startOfDay, long endOfDay);\n\n    @Query(\"SELECT * FROM tasks WHERE is_completed = 1 AND completed_date >= :twentyFourHoursAgo ORDER BY completed_date DESC\")\n    LiveData<List<Task>> getRecentCompletedTasksForHome(long twentyFourHoursAgo);"
)

with open(dao_path, "w", encoding="utf-8") as f:
    f.write(dao_text)

# 2. Update Repo
with open(repo_path, "r", encoding="utf-8") as f:
    repo_text = f.read()

repo_text = repo_text.replace(
    "public LiveData<List<Task>> getOverdueTasks() {\n        long now = System.currentTimeMillis();\n        return taskDao.getOverdueTasks(now);\n    }",
    "public LiveData<List<Task>> getOverdueTasks() {\n        long now = System.currentTimeMillis();\n        long h24 = now - (24 * 60 * 60 * 1000L);\n        return taskDao.getOverdueTasks(now, h24);\n    }"
)

# Change getTodayCompletedTasks in repo to use the 24h query
repo_text = repo_text.replace(
    "public LiveData<List<Task>> getTodayCompletedTasks() {\n        long startOfDay = hcmute.edu.vn.tickticktodo.util.DateUtils.getStartOfToday();\n        long endOfDay = hcmute.edu.vn.tickticktodo.util.DateUtils.getEndOfToday();\n        return taskDao.getCompletedTasks(startOfDay, endOfDay);\n    }",
    "public LiveData<List<Task>> getTodayCompletedTasks() {\n        long h24 = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);\n        return taskDao.getRecentCompletedTasksForHome(h24);\n    }"
)

repo_text = repo_text.replace(
    "public TaskRepository(Application application) {",
    "public LiveData<List<Task>> getAllCompletedTasksLog() {\n        return taskDao.getAllCompletedTasksLog();\n    }\n\n    public LiveData<List<Task>> getAllOverdueTasksLog() {\n        long now = System.currentTimeMillis();\n        return taskDao.getAllOverdueTasksLog(now);\n    }\n\n    public TaskRepository(Application application) {"
)

with open(repo_path, "w", encoding="utf-8") as f:
    f.write(repo_text)

# 3. Update ViewModel
with open(vm_path, "r", encoding="utf-8") as f:
    vm_text = f.read()

vm_text = vm_text.replace("public LiveData<List<Task>> getTodayCompletedTasks() {\n        return todayCompletedTasks;\n    }", "public LiveData<List<Task>> getTodayCompletedTasks() {\n        return todayCompletedTasks;\n    }\n\n    public LiveData<List<Task>> getAllCompletedTasksLog() {\n        return repository.getAllCompletedTasksLog();\n    }\n\n    public LiveData<List<Task>> getAllOverdueTasksLog() {\n        return repository.getAllOverdueTasksLog();\n    }")

with open(vm_path, "w", encoding="utf-8") as f:
    f.write(vm_text)

print("Updated data layer.")
