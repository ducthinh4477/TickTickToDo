package hcmute.edu.vn.tickticktodo.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import hcmute.edu.vn.tickticktodo.data.model.SuggestionEntity;

@Dao
public interface SuggestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SuggestionEntity entity);

    @Query("SELECT * FROM suggestions " +
            "WHERE status = 'NEW' AND expires_at_millis > :nowMillis " +
            "ORDER BY priority_score DESC, created_at_millis DESC")
    LiveData<List<SuggestionEntity>> observePendingSuggestions(long nowMillis);

    @Query("SELECT * FROM suggestions " +
            "WHERE status = 'NEW' AND expires_at_millis > :nowMillis AND priority_score >= :minPriorityScore " +
            "ORDER BY priority_score DESC, created_at_millis DESC LIMIT :limit")
    List<SuggestionEntity> getHighPriorityPendingSuggestionsSync(long nowMillis, float minPriorityScore, int limit);

    @Query("SELECT * FROM suggestions " +
            "WHERE type = :type AND status IN ('NEW', 'SHOWN') AND expires_at_millis > :nowMillis " +
            "ORDER BY created_at_millis DESC LIMIT 1")
    SuggestionEntity findActiveByTypeSync(String type, long nowMillis);

    @Query("SELECT * FROM suggestions WHERE id = :suggestionId LIMIT 1")
    SuggestionEntity findByIdSync(String suggestionId);

    @Query("SELECT * FROM suggestions ORDER BY created_at_millis DESC")
    List<SuggestionEntity> getAllSuggestionsSync();

    @Query("UPDATE suggestions SET status = :status WHERE id = :id")
    void updateStatus(String id, String status);

    @Query("DELETE FROM suggestions WHERE expires_at_millis <= :nowMillis")
    int deleteExpired(long nowMillis);
}
