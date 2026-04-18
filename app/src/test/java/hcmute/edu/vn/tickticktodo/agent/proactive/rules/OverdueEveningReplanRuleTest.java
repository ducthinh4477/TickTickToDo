package hcmute.edu.vn.tickticktodo.agent.proactive.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.Suggestion;
import hcmute.edu.vn.tickticktodo.data.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.Task;

public class OverdueEveningReplanRuleTest {

    @Test
    public void evaluate_emitsSuggestion_whenEveningAndOverdueEnough() {
        OverdueEveningReplanRule rule = new OverdueEveningReplanRule();
        long now = buildNowAtHour(20);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getOverdueIncompleteTasksSync(now)).thenReturn(createTasks(4));
        when(taskDao.getIncompleteTasksForDaySync(anyLong(), anyLong())).thenReturn(createTasks(2));

        AgentEvent event = AgentEvent.now(AgentEvent.TYPE_PROACTIVE_TICK, "test", new JSONObject());
        ProactiveRule.RuleContext context = new ProactiveRule.RuleContext(
                mock(Context.class),
                database,
                null,
                now
        );

        Suggestion suggestion = rule.evaluate(event, context);

        assertNotNull(suggestion);
        assertEquals("OVERDUE_EVENING_REPLAN", suggestion.getType());
        assertEquals("Buoi toi nen replan task qua han", suggestion.getTitle());
    }

    @Test
    public void evaluate_returnsNull_whenOutsideEveningWindow() {
        OverdueEveningReplanRule rule = new OverdueEveningReplanRule();
        long now = buildNowAtHour(10);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getOverdueIncompleteTasksSync(now)).thenReturn(createTasks(4));
        when(taskDao.getIncompleteTasksForDaySync(anyLong(), anyLong())).thenReturn(createTasks(2));

        AgentEvent event = AgentEvent.now(AgentEvent.TYPE_PROACTIVE_TICK, "test", new JSONObject());
        ProactiveRule.RuleContext context = new ProactiveRule.RuleContext(
                mock(Context.class),
                database,
                null,
                now
        );

        Suggestion suggestion = rule.evaluate(event, context);
        assertNull(suggestion);
    }

    private long buildNowAtHour(int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private List<Task> createTasks(int count) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Task task = new Task();
            task.setId(i + 1L);
            task.setTitle("Task " + i);
            task.setPriority(3);
            task.setCompleted(false);
            tasks.add(task);
        }
        return tasks;
    }
}
