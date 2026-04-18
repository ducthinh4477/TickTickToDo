package hcmute.edu.vn.tickticktodo.agent.proactive;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.lifecycle.LiveData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.agent.context.ContextAgent;
import hcmute.edu.vn.tickticktodo.agent.context.ContextSnapshot;
import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.agent.core.AgentEventBus;
import hcmute.edu.vn.tickticktodo.agent.profile.ProfileAgent;
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.MiddayOverloadRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.MoodleNewDeadlinesRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.OverdueEveningReplanRule;
import hcmute.edu.vn.tickticktodo.data.dao.SuggestionDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.data.model.SuggestionEntity;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;

public class ProactiveEngine {

    private static final String SUBSCRIBER_ID = "PROACTIVE_ENGINE";
    private static final String TAG = "ProactiveEngine";
    public static final String FEEDBACK_ACCEPT = "ACCEPT";
    public static final String FEEDBACK_DISMISS = "DISMISS";
    public static final String FEEDBACK_APPLY = "APPLY";
    private static volatile ProactiveEngine INSTANCE;

    private final Context appContext;
    private final TaskDatabase database;
    private final SuggestionDao suggestionDao;
    private final ContextAgent contextAgent;
    private final ProfileAgent profileAgent;
    private final AgentEventBus eventBus;
    private final ExecutorService workerExecutor;
    private final List<ProactiveRule> rules;

    private ProactiveEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.database = TaskDatabase.getInstance(appContext);
        this.suggestionDao = database.suggestionDao();
        this.contextAgent = ContextAgent.getInstance(appContext);
        this.profileAgent = ProfileAgent.getInstance(appContext);
        this.eventBus = AgentEventBus.getInstance();
        this.workerExecutor = Executors.newSingleThreadExecutor();

        this.rules = new ArrayList<>();
        this.rules.add(new OverdueEveningReplanRule());
        this.rules.add(new MoodleNewDeadlinesRule());
        this.rules.add(new MiddayOverloadRule());

        this.eventBus.subscribeAll(SUBSCRIBER_ID, this::onEvent);
    }

    public static ProactiveEngine getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ProactiveEngine.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ProactiveEngine(context);
                }
            }
        }
        return INSTANCE;
    }

    public static ProactiveEngine getExisting() {
        return INSTANCE;
    }

    public void onEvent(AgentEvent event) {
        if (event == null) {
            return;
        }

        Log.d(TAG, "onEvent in type=" + event.getType() + ", source=" + event.getSource());

        try {
            workerExecutor.execute(() -> evaluateEventInternal(event));
        } catch (Exception ignored) {
            Log.w(TAG, "Failed to enqueue event type=" + event.getType());
        }
    }

    public void evaluateNow(String reason) {
        JSONObject payload = new JSONObject();
        safePut(payload, "reason", reason == null ? "manual" : reason);
        onEvent(AgentEvent.now(AgentEvent.TYPE_PROACTIVE_TICK, "ProactiveEngine", payload));
    }

    public LiveData<List<SuggestionEntity>> observePendingSuggestions() {
        return suggestionDao.observePendingSuggestions(System.currentTimeMillis());
    }

    public List<SuggestionEntity> getHighPriorityPendingSuggestionsSync(float minPriorityScore, int limit) {
        int safeLimit = Math.max(1, limit);
        return suggestionDao.getHighPriorityPendingSuggestionsSync(
                System.currentTimeMillis(),
                minPriorityScore,
                safeLimit
        );
    }

    public void markSuggestionShown(String suggestionId) {
        if (suggestionId == null || suggestionId.trim().isEmpty()) {
            return;
        }
        try {
            workerExecutor.execute(() -> suggestionDao.updateStatus(suggestionId, Suggestion.STATUS_SHOWN));
        } catch (Exception ignored) {
        }
    }

    public void recordSuggestionFeedback(String suggestionId, String feedbackType, String channel) {
        if (suggestionId == null || suggestionId.trim().isEmpty()) {
            return;
        }

        final String safeType = normalizeFeedbackType(feedbackType);
        if (safeType == null) {
            return;
        }

        final String safeChannel = channel == null ? "unknown" : channel;

        try {
            workerExecutor.execute(() -> {
                SuggestionEntity suggestionEntity = suggestionDao.findByIdSync(suggestionId);
                if (suggestionEntity == null) {
                    Log.w(TAG, "recordFeedback skipped, suggestion not found id=" + suggestionId);
                    return;
                }

                suggestionDao.updateStatus(suggestionId, mapFeedbackToSuggestionStatus(safeType));
                profileAgent.updateFromFeedback(
                        suggestionId,
                        suggestionEntity.type,
                        safeType,
                        safeChannel,
                        suggestionEntity.priorityScore,
                        suggestionEntity.confidence
                );

                Log.d(TAG, "feedback recorded id=" + suggestionId + ", feedback=" + safeType + ", channel=" + safeChannel);
            });
        } catch (Exception ignored) {
            Log.w(TAG, "Failed to enqueue feedback id=" + suggestionId + ", feedback=" + safeType);
        }
    }

    private void evaluateEventInternal(AgentEvent event) {
        long startAll = SystemClock.elapsedRealtime();
        long now = System.currentTimeMillis();
        suggestionDao.deleteExpired(now);

        ContextSnapshot snapshot = contextAgent.getLatestSnapshot();
        ProactiveRule.RuleContext ruleContext = new ProactiveRule.RuleContext(
                appContext,
                database,
                snapshot,
                now
        );

        for (ProactiveRule rule : rules) {
            if (rule == null || !rule.supports(event)) {
                continue;
            }

            long startRule = SystemClock.elapsedRealtime();

            Suggestion suggestion;
            try {
                suggestion = rule.evaluate(event, ruleContext);
            } catch (Exception ignored) {
                long ruleDuration = SystemClock.elapsedRealtime() - startRule;
                Log.w(TAG, "ruleError rule=" + rule.getRuleId() + ", event=" + event.getType() + ", costMs=" + ruleDuration);
                continue;
            }

            long ruleDuration = SystemClock.elapsedRealtime() - startRule;
            Log.d(TAG, "ruleEvaluated rule=" + rule.getRuleId()
                    + ", event=" + event.getType()
                    + ", emitted=" + (suggestion != null)
                    + ", costMs=" + ruleDuration);

            persistSuggestionIfNeeded(suggestion, snapshot, now);
        }

        long totalDuration = SystemClock.elapsedRealtime() - startAll;
        Log.d(TAG, "onEvent done type=" + event.getType() + ", totalCostMs=" + totalDuration);
    }

    private void persistSuggestionIfNeeded(Suggestion suggestion, ContextSnapshot snapshot, long nowMillis) {
        if (suggestion == null || suggestion.getExpiresAtMillis() <= nowMillis) {
            return;
        }

        Suggestion adjustedSuggestion = applyProfileAdjustment(suggestion, snapshot);
        if (adjustedSuggestion == null) {
            return;
        }

        SuggestionEntity activeSuggestion = suggestionDao.findActiveByTypeSync(adjustedSuggestion.getType(), nowMillis);
        if (activeSuggestion != null
                && Math.abs(nowMillis - activeSuggestion.createdAtMillis) < ProactiveConfig.SUGGESTION_DEDUPE_WINDOW_MILLIS) {
            Log.d(TAG, "skipEmit reason=dedupe type=" + adjustedSuggestion.getType() + ", existingId=" + activeSuggestion.id);
            return;
        }

        suggestionDao.upsert(adjustedSuggestion.toEntity());
        Log.d(TAG, "emitSuggestion id=" + adjustedSuggestion.getId()
                + ", type=" + adjustedSuggestion.getType()
                + ", priority=" + adjustedSuggestion.getPriorityScore()
                + ", confidence=" + adjustedSuggestion.getConfidence());

        JSONObject payload = new JSONObject();
        safePut(payload, "suggestionId", adjustedSuggestion.getId());
        safePut(payload, "type", adjustedSuggestion.getType());
        eventBus.publish(AgentEvent.now(AgentEvent.TYPE_SUGGESTION_CREATED, "ProactiveEngine", payload));
    }

    private Suggestion applyProfileAdjustment(Suggestion suggestion, ContextSnapshot snapshot) {
        UserProfileEntity profile = profileAgent.getCurrentProfile();
        if (profile == null) {
            return suggestion;
        }

        float adjustedPriority = suggestion.getPriorityScore();
        int currentHour = getCurrentHour();
        if (currentHour < profile.focusStartHour || currentHour > profile.focusEndHour) {
            adjustedPriority -= 0.05f;
        }

        if (profile.suggestionDismissRate >= 0.60f) {
            adjustedPriority -= 0.06f;
        } else if (profile.suggestionApplyRate >= 0.45f) {
            adjustedPriority += 0.04f;
        }

        float dismissRateForType = profileAgent.getDismissRateForSuggestionType(suggestion.getType());
        if (dismissRateForType >= 0.65f) {
            adjustedPriority -= 0.07f;
        }

        adjustedPriority = clamp(adjustedPriority, 0f, 1f);
        if (Math.abs(adjustedPriority - suggestion.getPriorityScore()) < 0.0001f) {
            return suggestion;
        }

        Log.d(TAG, "profileAdjusted type=" + suggestion.getType()
                + ", oldPriority=" + suggestion.getPriorityScore()
                + ", newPriority=" + adjustedPriority);

        return new Suggestion(
                suggestion.getId(),
                suggestion.getType(),
                suggestion.getTitle(),
                suggestion.getReason(),
                suggestion.getConfidence(),
                adjustedPriority,
                suggestion.getCreatedAtMillis(),
                suggestion.getExpiresAtMillis(),
                suggestion.isRequiresConfirmation(),
                suggestion.getStatus()
        );
    }

    private String normalizeFeedbackType(String feedbackType) {
        if (feedbackType == null) {
            return null;
        }
        String normalized = feedbackType.trim().toUpperCase();
        switch (normalized) {
            case FEEDBACK_ACCEPT:
            case FEEDBACK_DISMISS:
            case FEEDBACK_APPLY:
                return normalized;
            default:
                return null;
        }
    }

    private String mapFeedbackToSuggestionStatus(String feedbackType) {
        if (FEEDBACK_APPLY.equals(feedbackType)) {
            return Suggestion.STATUS_APPLIED;
        }
        if (FEEDBACK_ACCEPT.equals(feedbackType)) {
            return Suggestion.STATUS_ACCEPTED;
        }
        return Suggestion.STATUS_DISMISSED;
    }

    private int getCurrentHour() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        return calendar.get(java.util.Calendar.HOUR_OF_DAY);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
