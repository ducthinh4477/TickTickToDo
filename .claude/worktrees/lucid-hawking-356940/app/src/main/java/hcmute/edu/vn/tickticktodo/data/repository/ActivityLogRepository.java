package hcmute.edu.vn.doinbot.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.doinbot.data.dao.ActivityLogDao;
import hcmute.edu.vn.doinbot.data.database.TaskDatabase;
import hcmute.edu.vn.doinbot.model.ActivityLog;

public class ActivityLogRepository {
    private final ActivityLogDao logDao;
    private final ExecutorService executor;

    public ActivityLogRepository(Application application) {
        TaskDatabase db = TaskDatabase.getInstance(application);
        logDao = db.activityLogDao();
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<ActivityLog>> getAllLogs() {
        return logDao.getAllLogs();
    }

    public LiveData<List<ActivityLog>> searchLogs(String query) {
        return logDao.searchLogs(query);
    }

    public void insertLog(String action, String taskTitle) {
        executor.execute(() -> {
            logDao.insert(new ActivityLog(action, System.currentTimeMillis(), taskTitle));
        });
    }

    public void clearAllLogs() {
        executor.execute(logDao::deleteAllLogs);
    }
}
