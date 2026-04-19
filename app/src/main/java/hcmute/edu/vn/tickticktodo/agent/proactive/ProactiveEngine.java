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
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.CommuteMicroTaskRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.DailyStartPlanningRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.MiddayOverloadRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.MoodleNewDeadlinesRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.OverloadRescueRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.OverdueEveningReplanRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.rules.WeeklyReviewRule;
import hcmute.edu.vn.tickticktodo.data.dao.AgentDecisionLogDao;
import hcmute.edu.vn.tickticktodo.data.dao.SuggestionDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.data.model.AgentDecisionLogEntity;
import hcmute.edu.vn.tickticktodo.data.model.SuggestionEntity;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;

public class ProactiveEngine {

    private static final String SUBSCRIBER_ID = "PROACTIVE_ENGINE";
    private static final String TAG = "ProactiveEngine";
    public static final String FEEDBACK_ACCEPT = "ACCEPT";
    public static final String FEEDBACK_DISMISS = "DISMISS";
    public static final String FEEDBACK_APPLY = "APPLY";
    private static final long RECENT_FEEDBACK_LOOKBACK_MILLIS = 14L * 24L * 60L * 60L * 1000L;
    private static final int RECENT_FEEDBACK_SAMPLE_SIZE = 12;
    private static final int DISMISS_STREAK_TRIGGER = 3;
    private static final long RULE_SKIP_LOG_COOLDOWN_MILLIS = 45_000L;
    private static final long DECISION_LOG_RETENTION_MILLIS = 21L * 24L * 60L * 60L * 1000L;
    private static final long DECISION_LOG_PRUNE_INTERVAL_MILLIS = 6L * 60L * 60L * 1000L;
    private static volatile ProactiveEngine INSTANCE;

    private final Context appContext;
    private final TaskDatabase database;
    private final SuggestionDao suggestionDao;
    private final AgentDecisionLogDao decisionLogDao;
    private final ContextAgent contextAgent;
    private final ProfileAgent profileAgent;
    private final RuleExecutionGuard ruleExecutionGuard;
    private final AgentEventBus eventBus;
    private final ExecutorService workerExecutor;
    private final List<ProactiveRule> rules;
    private long lastDecisionLogPruneAtMillis;

    private ProactiveEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.database = TaskDatabase.getInstance(appContext);
        this.suggestionDao = database.suggestionDao();
        this.decisionLogDao = database.agentDecisionLogDao();
        this.contextAgent = ContextAgent.getInstance(appContext);
        this.profileAgent = ProfileAgent.getInstance(appContext);
        this.ruleExecutionGuard = new RuleExecutionGuard();
        this.eventBus = AgentEventBus.getInstance();
        this.workerExecutor = Executors.newSingleThreadExecutor();

        this.rules = new ArrayList<>();
        this.rules.add(new DailyStartPlanningRule());
        this.rules.add(new OverloadRescueRule());
        this.rules.add(new OverdueEveningReplanRule());
        this.rules.add(new MoodleNewDeadlinesRule());
        this.rules.add(new MiddayOverloadRule());
        this.rules.add(new WeeklyReviewRule());
        this.rules.add(new CommuteMicroTaskRule());

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

    public float getAdaptiveOverlayMinPriority() {
        UserProfileEntity profile = profileAgent.getCurrentProfile();
        float threshold = ProactiveConfig.OVERLAY_MIN_PRIORITY_SCORE;
        int dismissStreak = getRecentDismissStreak();

        if (profile != null && profile.totalFeedbackCount >= 8) {
            if (profile.suggestionDismissRate >= 0.55f) {
                threshold += 0.05f;
            }
            if (profile.suggestionApplyRate >= 0.45f) {
                threshold -= 0.04f;
            }
        }

        threshold += computeDismissStreakBoost(dismissStreak, 0.02f, 0.10f);

        if (dismissStreak >= DISMISS_STREAK_TRIGGER) {
            Log.d(TAG, "overlayMinPriority adapted by dismissStreak=" + dismissStreak
                    + ", threshold=" + threshold);
        }

        return clamp(
                threshold,
                ProactiveConfig.OVERLAY_MIN_PRIORITY_SCORE_FLOOR,
                ProactiveConfig.OVERLAY_MIN_PRIORITY_SCORE_CEILING
        );
    }

    public long getAdaptiveOverlayCooldownMillis() {
        UserProfileEntity profile = profileAgent.getCurrentProfile();
        long cooldown = ProactiveConfig.OVERLAY_SURFACE_COOLDOWN_MILLIS;
        int dismissStreak = getRecentDismissStreak();

        if (profile != null && profile.totalFeedbackCount >= 8) {
            if (profile.suggestionDismissRate >= 0.55f) {
                cooldown = Math.round(cooldown * 2.0d);
            } else if (profile.suggestionApplyRate >= 0.45f) {
                cooldown = Math.round(cooldown * 0.75d);
            }
        }

        double streakMultiplier = 1.0d + computeDismissStreakBoost(dismissStreak, 0.35f, 1.40f);
        cooldown = Math.round(cooldown * streakMultiplier);

        if (dismissStreak >= DISMISS_STREAK_TRIGGER) {
            Log.d(TAG, "overlayCooldown adapted by dismissStreak=" + dismissStreak
                    + ", multiplier=" + streakMultiplier
                    + ", cooldownMs=" + cooldown);
        }

        return clampLong(
                cooldown,
                ProactiveConfig.OVERLAY_SURFACE_COOLDOWN_MIN_MILLIS,
                ProactiveConfig.OVERLAY_SURFACE_COOLDOWN_MAX_MILLIS
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
                logFeedbackMetricsSnapshot();
                logDecision(
                    "SUGGESTION_FEEDBACK",
                    suggestionEntity.type,
                    "feedback-" + safeType.toLowerCase(),
                    "channel=" + safeChannel,
                    suggestionId,
                    System.currentTimeMillis()
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
        maybePruneDecisionLogs(now);
        logDecision(event.getType(), "EVENT", "received", "source=" + event.getSource(), null, now);

        ContextSnapshot snapshot = contextAgent.getLatestSnapshot();
        ProactiveRule.RuleContext ruleContext = new ProactiveRule.RuleContext(
                appContext,
                database,
                snapshot,
                now
        );

        for (ProactiveRule rule : rules) {
            if (rule == null) {
                continue;
            }

            String ruleId = resolveRuleId(rule);

            if (ruleExecutionGuard.isBlocked(ruleId, now)) {
                long remainingMillis = ruleExecutionGuard.getRemainingBackoffMillis(ruleId, now);
                if (ruleExecutionGuard.shouldLogSkip(ruleId, now, RULE_SKIP_LOG_COOLDOWN_MILLIS)) {
                    Log.d(TAG, "ruleSkipped rule=" + ruleId
                            + ", event=" + event.getType()
                            + ", reason=backoff"
                            + ", remainingMs=" + remainingMillis);
                }
                logDecision(
                        event.getType(),
                        ruleId,
                        "blocked",
                        "reason=backoff,remainingMs=" + remainingMillis,
                        null,
                        now
                );
                continue;
            }

            boolean supports;
            try {
                supports = rule.supports(event);
            } catch (Exception e) {
                RuleExecutionGuard.FailureSnapshot failure = ruleExecutionGuard.recordFailure(ruleId, now);
                Log.w(TAG, "ruleSupportsError rule=" + failure.ruleId
                        + ", event=" + event.getType()
                        + ", failures=" + failure.consecutiveFailures
                        + ", cooldownMs=" + failure.backoffMillis, e);
                logDecision(
                    event.getType(),
                    failure.ruleId,
                    "supports-error",
                    "failures=" + failure.consecutiveFailures + ",cooldownMs=" + failure.backoffMillis,
                    null,
                    now
                );
                continue;
            }

            if (!supports) {
                continue;
            }

            long startRule = SystemClock.elapsedRealtime();

            Suggestion suggestion;
            try {
                suggestion = rule.evaluate(event, ruleContext);
                ruleExecutionGuard.recordSuccess(ruleId);
            } catch (Exception e) {
                long ruleDuration = SystemClock.elapsedRealtime() - startRule;
                RuleExecutionGuard.FailureSnapshot failure = ruleExecutionGuard.recordFailure(ruleId, now);
                Log.w(TAG, "ruleError rule=" + failure.ruleId
                        + ", event=" + event.getType()
                        + ", costMs=" + ruleDuration
                        + ", failures=" + failure.consecutiveFailures
                        + ", cooldownMs=" + failure.backoffMillis, e);
                logDecision(
                    event.getType(),
                    failure.ruleId,
                    "evaluate-error",
                    "costMs=" + ruleDuration
                        + ",failures=" + failure.consecutiveFailures
                        + ",cooldownMs=" + failure.backoffMillis,
                    null,
                    now
                );
                continue;
            }

            long ruleDuration = SystemClock.elapsedRealtime() - startRule;
            Log.d(TAG, "ruleEvaluated rule=" + ruleId
                    + ", event=" + event.getType()
                    + ", emitted=" + (suggestion != null)
                    + ", costMs=" + ruleDuration);
                logDecision(
                    event.getType(),
                    ruleId,
                    suggestion == null ? "evaluated-no-emit" : "evaluated-emit",
                    "costMs=" + ruleDuration,
                    suggestion == null ? null : suggestion.getId(),
                    now
                );

            persistSuggestionIfNeeded(suggestion, snapshot, now);
        }

        long totalDuration = SystemClock.elapsedRealtime() - startAll;
        Log.d(TAG, "onEvent done type=" + event.getType() + ", totalCostMs=" + totalDuration);
    }

    private String resolveRuleId(ProactiveRule rule) {
        if (rule == null) {
            return "UNKNOWN_RULE";
        }

        try {
            String ruleId = rule.getRuleId();
            if (ruleId == null || ruleId.trim().isEmpty()) {
                return "UNKNOWN_RULE";
            }
            return ruleId;
        } catch (Exception ignored) {
            return "UNKNOWN_RULE";
        }
    }

    private void persistSuggestionIfNeeded(Suggestion suggestion, ContextSnapshot snapshot, long nowMillis) {
        if (suggestion == null || suggestion.getExpiresAtMillis() <= nowMillis) {
            return;
        }

        Suggestion adjustedSuggestion = applyRankingAdjustment(suggestion, snapshot);
        if (adjustedSuggestion == null) {
            return;
        }

        long dedupeWindowMillis = getAdaptiveDedupeWindowMillis(adjustedSuggestion.getType());

        SuggestionEntity activeSuggestion = suggestionDao.findActiveByTypeSync(adjustedSuggestion.getType(), nowMillis);
        if (activeSuggestion != null
                && Math.abs(nowMillis - activeSuggestion.createdAtMillis) < dedupeWindowMillis) {
            Log.d(TAG, "skipEmit reason=dedupe type=" + adjustedSuggestion.getType()
                    + ", existingId=" + activeSuggestion.id
                    + ", windowMs=" + dedupeWindowMillis);
            logDecision(
                "SUGGESTION",
                adjustedSuggestion.getType(),
                "dedupe-skip",
                "existingId=" + activeSuggestion.id + ",windowMs=" + dedupeWindowMillis,
                activeSuggestion.id,
                nowMillis
            );
            return;
        }

        suggestionDao.upsert(adjustedSuggestion.toEntity());
        Log.d(TAG, "emitSuggestion id=" + adjustedSuggestion.getId()
                + ", type=" + adjustedSuggestion.getType()
                + ", priority=" + adjustedSuggestion.getPriorityScore()
                + ", confidence=" + adjustedSuggestion.getConfidence());
        logDecision(
            "SUGGESTION",
            adjustedSuggestion.getType(),
            "emitted",
            "priority=" + adjustedSuggestion.getPriorityScore()
                + ",confidence=" + adjustedSuggestion.getConfidence(),
            adjustedSuggestion.getId(),
            nowMillis
        );

        JSONObject payload = new JSONObject();
        safePut(payload, "suggestionId", adjustedSuggestion.getId());
        safePut(payload, "type", adjustedSuggestion.getType());
        eventBus.publish(AgentEvent.now(AgentEvent.TYPE_SUGGESTION_CREATED, "ProactiveEngine", payload));
    }

    private Suggestion applyRankingAdjustment(Suggestion suggestion, ContextSnapshot snapshot) {
        UserProfileEntity profile = profileAgent.getCurrentProfile();

        float profileAffinity = computeProfileAffinityScore(suggestion, profile);
        float contextSuitability = computeContextSuitabilityScore(suggestion, snapshot);

        float adjustedPriority = (suggestion.getPriorityScore() * 0.58f)
                + (suggestion.getConfidence() * 0.24f)
                + (profileAffinity * 0.12f)
                + (contextSuitability * 0.06f);
        adjustedPriority = clamp(adjustedPriority, 0f, 1f);

        float adjustedConfidence = clamp(
                (suggestion.getConfidence() * 0.80f) + (contextSuitability * 0.20f),
                0f,
                1f
        );

        if (Math.abs(adjustedPriority - suggestion.getPriorityScore()) < 0.0001f
                && Math.abs(adjustedConfidence - suggestion.getConfidence()) < 0.0001f) {
            return suggestion;
        }

        Log.d(TAG, "rankingAdjusted type=" + suggestion.getType()
                + ", oldPriority=" + suggestion.getPriorityScore()
                + ", newPriority=" + adjustedPriority
                + ", oldConfidence=" + suggestion.getConfidence()
                + ", newConfidence=" + adjustedConfidence
                + ", profileAffinity=" + profileAffinity
                + ", contextSuitability=" + contextSuitability);

        return new Suggestion(
                suggestion.getId(),
                suggestion.getType(),
                suggestion.getTitle(),
                suggestion.getReason(),
                adjustedConfidence,
                adjustedPriority,
                suggestion.getCreatedAtMillis(),
                suggestion.getExpiresAtMillis(),
                suggestion.isRequiresConfirmation(),
                suggestion.getStatus()
        );
    }

    private long getAdaptiveDedupeWindowMillis(String suggestionType) {
        long window = ProactiveConfig.SUGGESTION_DEDUPE_WINDOW_MILLIS;
        UserProfileEntity profile = profileAgent.getCurrentProfile();
        int dismissStreak = getRecentDismissStreak();

        if (profile != null && profile.totalFeedbackCount >= 8) {
            if (profile.suggestionDismissRate >= 0.55f) {
                window = Math.round(window * 1.5d);
            } else if (profile.suggestionApplyRate >= 0.45f) {
                window = Math.round(window * 0.75d);
            }
        }

        float dismissRateForType = profileAgent.getDismissRateForSuggestionType(suggestionType);
        if (dismissRateForType >= 0.65f) {
            window = Math.round(window * 1.4d);
        }

        double streakMultiplier = 1.0d + computeDismissStreakBoost(dismissStreak, 0.20f, 0.80f);
        window = Math.round(window * streakMultiplier);

        if (dismissStreak >= DISMISS_STREAK_TRIGGER) {
            Log.d(TAG, "dedupeWindow adapted by dismissStreak=" + dismissStreak
                    + ", type=" + suggestionType
                    + ", multiplier=" + streakMultiplier
                    + ", windowMs=" + window);
        }

        return clampLong(
                window,
                ProactiveConfig.SUGGESTION_DEDUPE_WINDOW_MIN_MILLIS,
                ProactiveConfig.SUGGESTION_DEDUPE_WINDOW_MAX_MILLIS
        );
    }

    private int getRecentDismissStreak() {
        long sinceMillis = System.currentTimeMillis() - RECENT_FEEDBACK_LOOKBACK_MILLIS;
        return profileAgent.getRecentDismissStreak(RECENT_FEEDBACK_SAMPLE_SIZE, sinceMillis);
    }

    private float computeDismissStreakBoost(int dismissStreak, float step, float maxBoost) {
        if (dismissStreak < DISMISS_STREAK_TRIGGER) {
            return 0f;
        }

        int extraDismisses = dismissStreak - (DISMISS_STREAK_TRIGGER - 1);
        return clamp(extraDismisses * step, 0f, maxBoost);
    }

    private float computeProfileAffinityScore(Suggestion suggestion, UserProfileEntity profile) {
        if (profile == null) {
            return 0.50f;
        }

        float score = 0.50f;
        if (profile.totalFeedbackCount >= 8) {
            score += profile.suggestionApplyRate * 0.35f;
            score += profile.suggestionAcceptanceRate * 0.10f;
            score -= profile.suggestionDismissRate * 0.30f;
        }

        float dismissRateForType = profileAgent.getDismissRateForSuggestionType(suggestion.getType());
        score -= dismissRateForType * 0.25f;

        int currentHour = getCurrentHour();
        boolean inFocusWindow = currentHour >= profile.focusStartHour && currentHour <= profile.focusEndHour;
        if (inFocusWindow
                && (containsTypeToken(suggestion.getType(), "PLAN")
                || containsTypeToken(suggestion.getType(), "REVIEW"))) {
            score += 0.08f;
        }

        return clamp(score, 0f, 1f);
    }

    private float computeContextSuitabilityScore(Suggestion suggestion, ContextSnapshot snapshot) {
        if (snapshot == null) {
            return 0.50f;
        }

        float score = 0.50f;
        String type = suggestion.getType();
        String timeOfDay = snapshot.getTimeOfDay();
        String connectivity = snapshot.getConnectivity();

        if (snapshot.getBatteryLevel() >= 0) {
            if (!snapshot.isCharging() && snapshot.getBatteryLevel() < 15) {
                score -= 0.25f;
            } else if (snapshot.getBatteryLevel() > 60 || snapshot.isCharging()) {
                score += 0.10f;
            }
        }

        if ("OFFLINE".equalsIgnoreCase(connectivity)
                && (containsTypeToken(type, "SYNC") || containsTypeToken(type, "MOODLE"))) {
            score -= 0.20f;
        }
        if ("CELLULAR".equalsIgnoreCase(connectivity) && containsTypeToken(type, "COMMUTE")) {
            score += 0.15f;
        }

        if (snapshot.isAppInForeground()) {
            score += 0.04f;
        }

        if ("NIGHT".equalsIgnoreCase(timeOfDay)
                && !containsTypeToken(type, "RESCUE")
                && !containsTypeToken(type, "REVIEW")) {
            score -= 0.15f;
        }
        if ("MORNING".equalsIgnoreCase(timeOfDay) && containsTypeToken(type, "DAILY_START")) {
            score += 0.18f;
        }
        if ("MIDDAY".equalsIgnoreCase(timeOfDay)
                && (containsTypeToken(type, "MIDDAY") || containsTypeToken(type, "RESCUE"))) {
            score += 0.12f;
        }
        if ("EVENING".equalsIgnoreCase(timeOfDay) && containsTypeToken(type, "EVENING")) {
            score += 0.12f;
        }

        return clamp(score, 0f, 1f);
    }

    private boolean containsTypeToken(String type, String token) {
        if (type == null || token == null) {
            return false;
        }
        return type.toUpperCase().contains(token.toUpperCase());
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

    private void logFeedbackMetricsSnapshot() {
        UserProfileEntity profile = profileAgent.getCurrentProfile();
        if (profile == null) {
            return;
        }

        Log.d(TAG, "feedbackMetrics acceptRate=" + profile.suggestionAcceptanceRate
                + ", dismissRate=" + profile.suggestionDismissRate
                + ", applyRate=" + profile.suggestionApplyRate
                + ", totalFeedback=" + profile.totalFeedbackCount);
    }

    private int getCurrentHour() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        return calendar.get(java.util.Calendar.HOUR_OF_DAY);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private void maybePruneDecisionLogs(long nowMillis) {
        if (decisionLogDao == null) {
            return;
        }
        if ((nowMillis - lastDecisionLogPruneAtMillis) < DECISION_LOG_PRUNE_INTERVAL_MILLIS) {
            return;
        }

        long cutoffMillis = nowMillis - DECISION_LOG_RETENTION_MILLIS;
        decisionLogDao.deleteOlderThan(cutoffMillis);
        lastDecisionLogPruneAtMillis = nowMillis;
    }

    private void logDecision(String eventType,
                             String stage,
                             String decision,
                             String detail,
                             String suggestionId,
                             long createdAtMillis) {
        if (decisionLogDao == null) {
            return;
        }

        try {
            AgentDecisionLogEntity entity = new AgentDecisionLogEntity();
            entity.source = TAG;
            entity.eventType = eventType;
            entity.stage = stage;
            entity.decision = decision;
            entity.detail = detail;
            entity.suggestionId = suggestionId;
            entity.createdAtMillis = createdAtMillis;
            decisionLogDao.insert(entity);
        } catch (Exception ignored) {
        }
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
