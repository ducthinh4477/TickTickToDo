package hcmute.edu.vn.tickticktodo.agent.proactive;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class RuleExecutionGuard {

    private static final String UNKNOWN_RULE_ID = "UNKNOWN_RULE";
    private static final int MAX_FAILURE_EXPONENT = 12;

    private final long baseBackoffMillis;
    private final long maxBackoffMillis;
    private final int maxTrackedRules;
    private final LinkedHashMap<String, RuleState> ruleStates;

    RuleExecutionGuard() {
        this(30_000L, 20L * 60L * 1000L, 64);
    }

    RuleExecutionGuard(long baseBackoffMillis, long maxBackoffMillis, int maxTrackedRules) {
        this.baseBackoffMillis = Math.max(1_000L, baseBackoffMillis);
        this.maxBackoffMillis = Math.max(this.baseBackoffMillis, maxBackoffMillis);
        this.maxTrackedRules = Math.max(8, maxTrackedRules);
        this.ruleStates = new LinkedHashMap<>();
    }

    boolean isBlocked(String ruleId, long nowMillis) {
        RuleState state = ruleStates.get(normalizeRuleId(ruleId));
        return state != null && nowMillis < state.blockedUntilMillis;
    }

    long getRemainingBackoffMillis(String ruleId, long nowMillis) {
        RuleState state = ruleStates.get(normalizeRuleId(ruleId));
        if (state == null) {
            return 0L;
        }
        return Math.max(0L, state.blockedUntilMillis - nowMillis);
    }

    boolean shouldLogSkip(String ruleId, long nowMillis, long logWindowMillis) {
        String normalizedId = normalizeRuleId(ruleId);
        RuleState state = ruleStates.get(normalizedId);
        if (state == null || nowMillis >= state.blockedUntilMillis) {
            return false;
        }

        long safeWindow = Math.max(1_000L, logWindowMillis);
        if (state.lastSkipLogAtMillis > 0L && (nowMillis - state.lastSkipLogAtMillis) < safeWindow) {
            return false;
        }

        state.lastSkipLogAtMillis = nowMillis;
        return true;
    }

    FailureSnapshot recordFailure(String ruleId, long nowMillis) {
        String normalizedId = normalizeRuleId(ruleId);
        RuleState state = getOrCreateState(normalizedId);

        state.consecutiveFailures = Math.min(MAX_FAILURE_EXPONENT, state.consecutiveFailures + 1);

        long backoff = computeBackoffMillis(state.consecutiveFailures);
        state.blockedUntilMillis = nowMillis + backoff;
        state.lastFailureAtMillis = nowMillis;

        return new FailureSnapshot(
                normalizedId,
                state.consecutiveFailures,
                backoff,
                state.blockedUntilMillis
        );
    }

    void recordSuccess(String ruleId) {
        ruleStates.remove(normalizeRuleId(ruleId));
    }

    private RuleState getOrCreateState(String ruleId) {
        RuleState state = ruleStates.get(ruleId);
        if (state != null) {
            return state;
        }

        trimIfNeeded();

        RuleState newState = new RuleState();
        ruleStates.put(ruleId, newState);
        return newState;
    }

    private void trimIfNeeded() {
        while (ruleStates.size() >= maxTrackedRules) {
            Iterator<Map.Entry<String, RuleState>> iterator = ruleStates.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private long computeBackoffMillis(int failureCount) {
        int exponent = Math.max(0, Math.min(MAX_FAILURE_EXPONENT - 1, failureCount - 1));
        long multiplier = 1L << exponent;
        long candidate = safeMultiply(baseBackoffMillis, multiplier);
        return Math.min(candidate, maxBackoffMillis);
    }

    private long safeMultiply(long left, long right) {
        if (left == 0L || right == 0L) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private String normalizeRuleId(String ruleId) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            return UNKNOWN_RULE_ID;
        }
        return ruleId.trim();
    }

    static final class FailureSnapshot {
        final String ruleId;
        final int consecutiveFailures;
        final long backoffMillis;
        final long blockedUntilMillis;

        FailureSnapshot(String ruleId, int consecutiveFailures, long backoffMillis, long blockedUntilMillis) {
            this.ruleId = ruleId;
            this.consecutiveFailures = consecutiveFailures;
            this.backoffMillis = backoffMillis;
            this.blockedUntilMillis = blockedUntilMillis;
        }
    }

    private static final class RuleState {
        int consecutiveFailures;
        long blockedUntilMillis;
        long lastFailureAtMillis;
        long lastSkipLogAtMillis;
    }
}
