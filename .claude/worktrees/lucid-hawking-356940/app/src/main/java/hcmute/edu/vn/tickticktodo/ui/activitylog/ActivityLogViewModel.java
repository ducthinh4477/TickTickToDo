package hcmute.edu.vn.doinbot.ui.activitylog;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import hcmute.edu.vn.doinbot.model.ActivityLog;
import hcmute.edu.vn.doinbot.data.repository.ActivityLogRepository;

public class ActivityLogViewModel extends AndroidViewModel {
    private final ActivityLogRepository repository;

    public ActivityLogViewModel(@NonNull Application application) {
        super(application);
        repository = new ActivityLogRepository(application);
    }

    public LiveData<List<ActivityLog>> getAllLogs() {
        return repository.getAllLogs();
    }

    public LiveData<List<ActivityLog>> searchLogs(String query) {
        return repository.searchLogs(query);
    }
    
    public void logAction(String action, String title) {
        repository.insertLog(action, title);
    }

    public void clearAllLogs() {
        repository.clearAllLogs();
    }
}
