package hcmute.edu.vn.doinbot.agent.proactive;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProactiveConfigTest {

    @Test
    public void isQuietHours_returnsTrue_atLateNightAndEarlyMorning() {
        assertTrue(ProactiveConfig.isQuietHours(23));
        assertTrue(ProactiveConfig.isQuietHours(6));
    }

    @Test
    public void isQuietHours_returnsFalse_outsideQuietWindow() {
        assertFalse(ProactiveConfig.isQuietHours(12));
        assertFalse(ProactiveConfig.isQuietHours(7));
    }
}
