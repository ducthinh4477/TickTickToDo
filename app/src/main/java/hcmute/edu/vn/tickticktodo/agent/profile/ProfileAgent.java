package hcmute.edu.vn.tickticktodo.agent.profile;

import android.content.Context;
import android.util.Log;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveEngine;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.data.model.SuggestionFeedbackEntity;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;
import hcmute.edu.vn.tickticktodo.data.repository.ProfileRepository;
import hcmute.edu.vn.tickticktodo.model.Task;

public class ProfileAgent {

    private static final String TAG = "ProfileAgent";
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    private static volatile ProfileAgent INSTANCE;

    private final Context appContext;
    private final TaskDatabase database;
    private final ProfileRepository profileRepository;

    private ProfileAgent(Context context) {
        this.appContext = context.getApplicationContext();
        this.database = TaskDatabase.getInstance(appContext);
        this.profileRepository = new ProfileRepository(appContext);
    }

    public static ProfileAgent getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ProfileAgent.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ProfileAgent(context);
                }
            }
        }
        return INSTANCE;
    }

    public UserProfileEntity getCurrentProfile() {
        return profileRepository.getOrCreateProfileSync();
    }

    public void runDailyReflection() {
        long now = System.currentTimeMillis();

        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(now - DAY_MILLIS);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 1);

        List<Task> completed = database.taskDao().getCompletedTasksForDaySync(start.getTimeInMillis(), end.getTimeInMillis());
        List<Task> incomplete = database.taskDao().getIncompleteTasksForDaySync(start.getTimeInMillis(), end.getTimeInMillis());

        int completedCount = completed == null ? 0 : completed.size();
        int incompleteCount = incomplete == null ? 0 : incomplete.size();
        int total = completedCount + incompleteCount;

        float completionRate = total == 0 ? 0f : (completedCount / (float) total);
        ProfileRepository.FeedbackStats stats = profileRepository.getFeedbackStatsSinceSync(start.getTimeInMillis());

        UserProfileEntity profile = profileRepository.getOrCreateProfileSync();
        profile.avgDailyCompletionRate = blend(profile.avgDailyCompletionRate, completionRate, 0.30f);
        applyFeedbackRates(profile, stats);
        profile.lastDailyReflectionMillis = now;
        profile.updatedAtMillis = now;
        profileRepository.upsertProfileSync(profile);

        Log.d(TAG, "Daily reflection done completionRate=" + completionRate + ", feedbackTotal=" + stats.total);
    }

    public void runWeeklyReflection() {
        long now = System.currentTimeMillis();
        long since = now - (7L * DAY_MILLIS);

        List<Task> allTasks = database.taskDao().getAllTasksSync();
        int completedHourTotal = 0;
        int completedHourCount = 0;

        if (allTasks != null) {
            for (Task task : allTasks) {
                if (task == null || !task.isCompleted()) {
                    continue;
                }

                Long completedDate = task.getCompletedDate();
                if (completedDate == null || completedDate < since) {
                    continue;
                }

                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(completedDate);
                completedHourTotal += calendar.get(Calendar.HOUR_OF_DAY);
                completedHourCount++;
            }
        }

        UserProfileEntity profile = profileRepository.getOrCreateProfileSync();

        if (completedHourCount > 0) {
            int averageHour = Math.round(completedHourTotal / (float) completedHourCount);
            int suggestedStart = clamp(averageHour - 2, 6, 20);
            int suggestedEnd = clamp(suggestedStart + 3, suggestedStart + 1, 23);
            profile.focusStartHour = suggestedStart;
            profile.focusEndHour = suggestedEnd;
        }

        ProfileRepository.FeedbackStats stats = profileRepository.getFeedbackStatsSinceSync(since);
        if (stats.total > 0) {
            float applyRate = stats.applied / (float) stats.total;
            if (applyRate >= 0.40f) {
                profile.preferredSessionMinutes = clamp(profile.preferredSessionMinutes + 5, 25, 120);
            } else if ((stats.dismissed / (float) stats.total) >= 0.50f) {
                profile.preferredSessionMinutes = clamp(profile.preferredSessionMinutes - 5, 25, 120);
            }
        }

        applyFeedbackRates(profile, stats);
        profile.lastWeeklyReflectionMillis = now;
        profile.updatedAtMillis = now;
        profileRepository.upsertProfileSync(profile);

        Log.d(TAG, "Weekly reflection done focus=" + profile.focusStartHour + "-" + profile.focusEndHour
                + ", session=" + profile.preferredSessionMinutes);
    }

    public void updateFromFeedback(String suggestionId,
                                   String suggestionType,
                                   String feedbackType,
                                   String channel,
                                   float priorityScore,
                                   float confidence) {
        long now = System.currentTimeMillis();
        profileRepository.insertSuggestionFeedbackSync(
                suggestionId,
                suggestionType,
                feedbackType,
                channel,
                priorityScore,
                confidence,
                now
        );

        long since = now - (30L * DAY_MILLIS);
        ProfileRepository.FeedbackStats stats = profileRepository.getFeedbackStatsSinceSync(since);

        UserProfileEntity profile = profileRepository.getOrCreateProfileSync();
        applyFeedbackRates(profile, stats);
        profile.totalFeedbackCount = Math.max(profile.totalFeedbackCount, stats.total);
        profile.updatedAtMillis = now;
        profileRepository.upsertProfileSync(profile);

        Log.d(TAG, "Feedback updated id=" + suggestionId + ", type=" + feedbackType + ", total=" + stats.total);
    }

    public String buildPersonaSummary() {
        UserProfileEntity profile = profileRepository.getOrCreateProfileSync();
        return String.format(
                Locale.ROOT,
                "Focus %02d:00-%02d:00, session %d min, completion %.0f%%, accept %.0f%%, apply %.0f%%",
                profile.focusStartHour,
                profile.focusEndHour,
                profile.preferredSessionMinutes,
                profile.avgDailyCompletionRate * 100f,
                profile.suggestionAcceptanceRate * 100f,
                profile.suggestionApplyRate * 100f
        );
    }

    public float getDismissRateForSuggestionType(String suggestionType) {
        long since = System.currentTimeMillis() - (30L * DAY_MILLIS);
        return profileRepository.getDismissRateForSuggestionTypeSinceSync(suggestionType, since);
    }

    public int getRecentDismissStreak(int maxSamples, long sinceMillis) {
        int safeLimit = clamp(maxSamples, 1, 60);
        List<SuggestionFeedbackEntity> recent = profileRepository.getRecentFeedbackSinceSync(sinceMillis, safeLimit);
        if (recent == null || recent.isEmpty()) {
            return 0;
        }

        int streak = 0;
        for (SuggestionFeedbackEntity feedback : recent) {
            if (feedback == null || feedback.feedbackType == null) {
                break;
            }

            String type = feedback.feedbackType.trim().toUpperCase(Locale.ROOT);
            if (!ProactiveEngine.FEEDBACK_DISMISS.equals(type)) {
                break;
            }
            streak++;
        }
        return streak;
    }

    private void applyFeedbackRates(UserProfileEntity profile, ProfileRepository.FeedbackStats stats) {
        if (profile == null || stats == null || stats.total <= 0) {
            return;
        }

        profile.suggestionAcceptanceRate = stats.accepted / (float) stats.total;
        profile.suggestionDismissRate = stats.dismissed / (float) stats.total;
        profile.suggestionApplyRate = stats.applied / (float) stats.total;
        profile.totalFeedbackCount = stats.total;
    }

    private float blend(float oldValue, float newValue, float alpha) {
        float safeAlpha = Math.max(0.05f, Math.min(0.95f, alpha));
        return oldValue * (1f - safeAlpha) + newValue * safeAlpha;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
