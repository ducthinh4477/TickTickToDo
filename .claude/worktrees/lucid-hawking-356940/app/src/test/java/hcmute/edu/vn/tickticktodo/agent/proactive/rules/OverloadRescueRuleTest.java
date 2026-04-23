package hcmute.edu.vn.doinbot.agent.proactive.rules;

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

import hcmute.edu.vn.doinbot.agent.context.ContextSnapshot;
import hcmute.edu.vn.doinbot.agent.core.AgentEvent;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveRule;
import hcmute.edu.vn.doinbot.agent.proactive.Suggestion;
import hcmute.edu.vn.doinbot.data.dao.TaskDao;
import hcmute.edu.vn.doinbot.data.database.TaskDatabase;
import hcmute.edu.vn.doinbot.model.Task;

public class OverloadRescueRuleTest {

    @Test
    public void evaluate_emitsSuggestion_whenOverloadedAndBatteryAllowed() {
        OverloadRescueRule rule = new OverloadRescueRule();
        long now = buildNowAtHour(14);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getIncompleteTasksForDaySync(anyLong(), anyLong())).thenReturn(createTasks(9, now));
        when(taskDao.getOverdueIncompleteTasksSync(now)).thenReturn(createTasks(2, now - 60_000L));

        ContextSnapshot snapshot = new ContextSnapshot(
                "AFTERNOON",
                Calendar.MONDAY,
                65,
                false,
                "WIFI",
                true,
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
        assertEquals("OVERLOAD_RESCUE", suggestion.getType());
        assertEquals("Lich dang qua tai, nen kich hoat che do cuu hoa", suggestion.getTitle());
    }

    @Test
    public void evaluate_returnsNull_whenBatteryTooLowAndNotCharging() {
        OverloadRescueRule rule = new OverloadRescueRule();
        long now = buildNowAtHour(14);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getIncompleteTasksForDaySync(anyLong(), anyLong())).thenReturn(createTasks(12, now));
        when(taskDao.getOverdueIncompleteTasksSync(now)).thenReturn(createTasks(5, now - 60_000L));

        ContextSnapshot snapshot = new ContextSnapshot(
                "AFTERNOON",
                Calendar.MONDAY,
                10,
                false,
                "WIFI",
                true,
                now
        );

        AgentEvent event = AgentEvent.now(AgentEvent.TYPE_PROACTIVE_TICK, "test", new JSONObject());
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
        calendar.set(Calendar.MINUTE, 20);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private List<Task> createTasks(int count, long dueAt) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Task task = new Task();
            task.setId(i + 1L);
            task.setTitle("Task " + i);
            task.setPriority(3);
            task.setDueDate(dueAt + (i * 10_000L));
            task.setCompleted(false);
            tasks.add(task);
        }
        return tasks;
    }
}
