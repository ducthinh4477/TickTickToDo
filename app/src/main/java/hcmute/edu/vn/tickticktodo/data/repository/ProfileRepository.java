package hcmute.edu.vn.tickticktodo.data.repository;

import android.content.Context;

import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveEngine;
import hcmute.edu.vn.tickticktodo.data.dao.UserProfileDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.data.model.SuggestionFeedbackEntity;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;

public class ProfileRepository {

    public static final class FeedbackStats {
        public int accepted;
        public int dismissed;
        public int applied;
        public int total;
    }

    private final UserProfileDao userProfileDao;

    public ProfileRepository(Context context) {
        TaskDatabase database = TaskDatabase.getInstance(context.getApplicationContext());
        this.userProfileDao = database.userProfileDao();
    }

    public UserProfileEntity getOrCreateProfileSync() {
        UserProfileEntity profile = userProfileDao.getProfileSync();
        if (profile != null) {
            return profile;
        }

        UserProfileEntity initial = new UserProfileEntity();
        initial.id = 1L;
        initial.updatedAtMillis = System.currentTimeMillis();
        userProfileDao.upsertProfile(initial);
        return initial;
    }

    public void upsertProfileSync(UserProfileEntity profile) {
        if (profile == null) {
            return;
        }
        if (profile.id <= 0L) {
            profile.id = 1L;
        }
        profile.updatedAtMillis = System.currentTimeMillis();
        userProfileDao.upsertProfile(profile);
    }

    public void insertSuggestionFeedbackSync(String suggestionId,
                                             String suggestionType,
                                             String feedbackType,
                                             String channel,
                                             float priorityScore,
                                             float confidence,
                                             long createdAtMillis) {
        SuggestionFeedbackEntity feedback = new SuggestionFeedbackEntity();
        feedback.suggestionId = suggestionId;
        feedback.suggestionType = suggestionType;
        feedback.feedbackType = feedbackType;
        feedback.channel = channel;
        feedback.priorityScore = priorityScore;
        feedback.confidence = confidence;
        feedback.createdAtMillis = createdAtMillis;
        userProfileDao.insertFeedback(feedback);
    }

    public FeedbackStats getFeedbackStatsSinceSync(long sinceMillis) {
        FeedbackStats stats = new FeedbackStats();
        stats.accepted = userProfileDao.countFeedbackByTypeSinceSync(ProactiveEngine.FEEDBACK_ACCEPT, sinceMillis);
        stats.dismissed = userProfileDao.countFeedbackByTypeSinceSync(ProactiveEngine.FEEDBACK_DISMISS, sinceMillis);
        stats.applied = userProfileDao.countFeedbackByTypeSinceSync(ProactiveEngine.FEEDBACK_APPLY, sinceMillis);
        stats.total = stats.accepted + stats.dismissed + stats.applied;
        return stats;
    }

    public float getDismissRateForSuggestionTypeSinceSync(String suggestionType, long sinceMillis) {
        if (suggestionType == null || suggestionType.trim().isEmpty()) {
            return 0f;
        }

        int dismissed = userProfileDao.countFeedbackForSuggestionTypeSinceSync(
                suggestionType,
                ProactiveEngine.FEEDBACK_DISMISS,
                sinceMillis
        );
        int accepted = userProfileDao.countFeedbackForSuggestionTypeSinceSync(
                suggestionType,
                ProactiveEngine.FEEDBACK_ACCEPT,
                sinceMillis
        );
        int applied = userProfileDao.countFeedbackForSuggestionTypeSinceSync(
                suggestionType,
                ProactiveEngine.FEEDBACK_APPLY,
                sinceMillis
        );

        int total = dismissed + accepted + applied;
        if (total <= 0) {
            return 0f;
        }

        return dismissed / (float) total;
    }

    public List<SuggestionFeedbackEntity> getFeedbackSinceSync(long sinceMillis) {
        return userProfileDao.getFeedbackSinceSync(sinceMillis);
    }

    public List<SuggestionFeedbackEntity> getRecentFeedbackSinceSync(long sinceMillis, int limit) {
        int safeLimit = Math.max(1, limit);
        return userProfileDao.getRecentFeedbackSinceSync(sinceMillis, safeLimit);
    }
}
