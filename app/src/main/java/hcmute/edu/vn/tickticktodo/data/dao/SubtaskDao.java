package hcmute.edu.vn.tickticktodo.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hcmute.edu.vn.tickticktodo.model.Subtask;

@Dao
public interface SubtaskDao {

    @Query("SELECT * FROM subtasks WHERE task_id = :taskId ORDER BY order_index ASC, id ASC")
    LiveData<List<Subtask>> getSubtasksByTaskId(long taskId);

    @Query("SELECT * FROM subtasks WHERE task_id = :taskId ORDER BY order_index ASC, id ASC")
    List<Subtask> getSubtasksByTaskIdSync(long taskId);

    @Insert
    long insert(Subtask subtask);

    @Insert
    List<Long> insertAll(List<Subtask> subtasks);

    @Update
    void update(Subtask subtask);

    @Delete
    void delete(Subtask subtask);

    @Query("DELETE FROM subtasks WHERE task_id = :taskId")
    void deleteByTaskId(long taskId);

    @Query("DELETE FROM subtasks WHERE task_id = :taskId AND is_approved = 0")
    void deleteUnapprovedByTaskId(long taskId);

    @Query("UPDATE subtasks SET is_completed = :isCompleted WHERE id = :subtaskId")
    void markSubtaskCompleted(long subtaskId, boolean isCompleted);

    @Query("UPDATE subtasks SET is_approved = :isApproved WHERE id = :subtaskId")
    void setSubtaskApproved(long subtaskId, boolean isApproved);

    @Query("UPDATE subtasks SET priority = :priority WHERE id = :subtaskId")
    void updateSubtaskPriority(long subtaskId, int priority);

    @Query("SELECT MAX(order_index) FROM subtasks WHERE task_id = :taskId")
    Integer getMaxOrderIndex(long taskId);
}