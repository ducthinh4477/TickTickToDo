package hcmute.edu.vn.tickticktodo.agent.proactive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuleExecutionGuardTest {

    @Test
    public void recordFailure_usesExponentialBackoffAndCapsAtMax() {
        RuleExecutionGuard guard = new RuleExecutionGuard(1_000L, 8_000L, 16);
        long now = 10_000L;

        RuleExecutionGuard.FailureSnapshot first = guard.recordFailure("RULE_A", now);
        assertEquals(1, first.consecutiveFailures);
        assertEquals(1_000L, first.backoffMillis);
        assertTrue(guard.isBlocked("RULE_A", now + 500L));
        assertFalse(guard.isBlocked("RULE_A", now + 1_000L));

        RuleExecutionGuard.FailureSnapshot second = guard.recordFailure("RULE_A", now + 1_000L);
        assertEquals(2, second.consecutiveFailures);
        assertEquals(2_000L, second.backoffMillis);

        RuleExecutionGuard.FailureSnapshot latest = second;
        long cursor = now + 3_000L;
        for (int i = 0; i < 4; i++) {
            latest = guard.recordFailure("RULE_A", cursor);
            cursor += latest.backoffMillis;
        }

        assertEquals(8_000L, latest.backoffMillis);
    }

    @Test
    public void recordSuccess_clearsBlockingState() {
        RuleExecutionGuard guard = new RuleExecutionGuard(2_000L, 8_000L, 16);
        long now = 20_000L;

        guard.recordFailure("RULE_B", now);
        assertTrue(guard.isBlocked("RULE_B", now + 500L));

        guard.recordSuccess("RULE_B");
        assertFalse(guard.isBlocked("RULE_B", now + 500L));
        assertEquals(0L, guard.getRemainingBackoffMillis("RULE_B", now + 500L));
    }

    @Test
    public void shouldLogSkip_throttlesRepeatedSkipLogs() {
        RuleExecutionGuard guard = new RuleExecutionGuard(5_000L, 30_000L, 16);
        long now = 30_000L;

        guard.recordFailure("RULE_C", now);

        assertTrue(guard.shouldLogSkip("RULE_C", now + 100L, 2_000L));
        assertFalse(guard.shouldLogSkip("RULE_C", now + 500L, 2_000L));
        assertTrue(guard.shouldLogSkip("RULE_C", now + 2_500L, 2_000L));
    }
}
