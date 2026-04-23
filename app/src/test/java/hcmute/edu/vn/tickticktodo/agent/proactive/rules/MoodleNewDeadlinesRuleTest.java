package hcmute.edu.vn.tickticktodo.agent.proactive.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Test;

import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.Suggestion;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;

public class MoodleNewDeadlinesRuleTest {

    @Test
    public void evaluate_emitsSuggestion_whenExternalSyncHasNewDeadlines() throws Exception {
        MoodleNewDeadlinesRule rule = new MoodleNewDeadlinesRule();

        JSONObject payload = new JSONObject();
        payload.put("newTasksCount", 3);

        AgentEvent event = AgentEvent.now(AgentEvent.TYPE_EXTERNAL_DEADLINES_SYNCED, "test", payload);
        ProactiveRule.RuleContext context = new ProactiveRule.RuleContext(
                mock(Context.class),
                mock(TaskDatabase.class),
                null,
                System.currentTimeMillis()
        );

        Suggestion suggestion = rule.evaluate(event, context);

        assertNotNull(suggestion);
        assertEquals("MOODLE_NEW_DEADLINES", suggestion.getType());
        assertEquals("Co deadline Moodle moi can sap xep", suggestion.getTitle());
    }

    @Test
    public void evaluate_returnsNull_whenNoNewDeadlines() throws Exception {
        MoodleNewDeadlinesRule rule = new MoodleNewDeadlinesRule();

        JSONObject payload = new JSONObject();
        payload.put("newDeadlineCount", 0);

        AgentEvent event = AgentEvent.now(AgentEvent.TYPE_EXTERNAL_DEADLINES_SYNCED, "test", payload);
        ProactiveRule.RuleContext context = new ProactiveRule.RuleContext(
                mock(Context.class),
                mock(TaskDatabase.class),
                null,
                System.currentTimeMillis()
        );

        Suggestion suggestion = rule.evaluate(event, context);
        assertNull(suggestion);
    }
}
