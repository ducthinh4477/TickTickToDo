package hcmute.edu.vn.tickticktodo.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hcmute.edu.vn.tickticktodo.model.ActivityLog;

@Dao
public interface ActivityLogDao {
    @Insert
    void insert(ActivityLog log);

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    LiveData<List<ActivityLog>> getAllLogs();

    @Query("SELECT * FROM activity_logs WHERE task_title LIKE '%' || :query || '%' OR action LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    LiveData<List<ActivityLog>> searchLogs(String query);

    @Query("DELETE FROM activity_logs")
    void deleteAllLogs();
}
