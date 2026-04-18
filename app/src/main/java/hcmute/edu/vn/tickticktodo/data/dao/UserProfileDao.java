package hcmute.edu.vn.tickticktodo.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import hcmute.edu.vn.tickticktodo.data.model.SuggestionFeedbackEntity;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;

@Dao
public interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    UserProfileEntity getProfileSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertProfile(UserProfileEntity profile);

    @Insert
    long insertFeedback(SuggestionFeedbackEntity feedback);

    @Query("SELECT * FROM suggestion_feedback WHERE created_at_millis >= :sinceMillis ORDER BY created_at_millis DESC")
    List<SuggestionFeedbackEntity> getFeedbackSinceSync(long sinceMillis);

    @Query("SELECT COUNT(*) FROM suggestion_feedback WHERE feedback_type = :feedbackType AND created_at_millis >= :sinceMillis")
    int countFeedbackByTypeSinceSync(String feedbackType, long sinceMillis);

    @Query("SELECT COUNT(*) FROM suggestion_feedback WHERE suggestion_type = :suggestionType AND feedback_type = :feedbackType AND created_at_millis >= :sinceMillis")
    int countFeedbackForSuggestionTypeSinceSync(String suggestionType, String feedbackType, long sinceMillis);
}
