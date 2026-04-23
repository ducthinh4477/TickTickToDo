package hcmute.edu.vn.tickticktodo.agent.proactive.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.context.ContextSnapshot;
import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.Suggestion;
import hcmute.edu.vn.tickticktodo.data.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.Task;

public class CommuteMicroTaskRuleTest {

    @Test
    public void evaluate_emitsSuggestion_whenCellularCommuteAndHasCandidates() {
        CommuteMicroTaskRule rule = new CommuteMicroTaskRule();
        long now = buildNowAtHour(8);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getAllTasksSync()).thenReturn(createQuickCandidates(now, 3));

        ContextSnapshot snapshot = new ContextSnapshot(
                "MORNING",
                Calendar.TUESDAY,
                70,
                false,
                "CELLULAR",
                false,
                now
        );

        AgentEvent event = AgentEvent.now(AgentEvent.TYPE_CONTEXT_REFRESHED, "test", new JSONObject());
        ProactiveRule.RuleContext context = new ProactiveRule.RuleContext(
                mock(Context.class),
                database,
                snapshot,
                now
        );

        Suggestion suggestion = rule.evaluate(event, context);

        assertNotNull(suggestion);
        assertEquals("COMMUTE_MICRO_TASK", suggestion.getType());
        assertEquals("Khung di chuyen phu hop cho micro-task", suggestion.getTitle());
    }

    @Test
    public void evaluate_returnsNull_whenNotCellularConnectivity() {
        CommuteMicroTaskRule rule = new CommuteMicroTaskRule();
        long now = buildNowAtHour(8);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getAllTasksSync()).thenReturn(createQuickCandidates(now, 3));

        ContextSnapshot snapshot = new ContextSnapshot(
                "MORNING",
                Calendar.TUESDAY,
                70,
                false,
                "WIFI",
                false,
                now
        );

        AgentEvent event = AgentEvent.now(AgentEvent.TYPE_CONTEXT_REFRESHED, "test", new JSONObject());
        ProactiveRule.RuleContext context = new ProactiveRule.RuleContext(
                mock(Context.class),
                database,
                snapshot,
                now
        );

        Suggestion suggestion = rule.evaluate(event, context);
        assertNull(suggestion);
    }

    private long buildNowAtHour(int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 40);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private List<Task> createQuickCandidates(long now, int count) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Task task = new Task();
            task.setId(i + 1L);
            task.setTitle("Quick " + i);
            task.setPriority(2);
            task.setCompleted(false);
            task.setDueDate(now + ((i + 1L) * 60L * 60L * 1000L));
            tasks.add(task);
        }
        return tasks;
    }
}
