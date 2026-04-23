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

import hcmute.edu.vn.doinbot.agent.core.AgentEvent;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveRule;
import hcmute.edu.vn.doinbot.agent.proactive.Suggestion;
import hcmute.edu.vn.doinbot.data.dao.TaskDao;
import hcmute.edu.vn.doinbot.data.database.TaskDatabase;
import hcmute.edu.vn.doinbot.model.Task;

public class MiddayOverloadRuleTest {

    @Test
    public void evaluate_emitsSuggestion_whenMiddayAndOverloaded() {
        MiddayOverloadRule rule = new MiddayOverloadRule();
        long now = buildNowAtHour(13);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getIncompleteTasksForDaySync(anyLong(), anyLong())).thenReturn(createTasks(7));
        when(taskDao.getOverdueIncompleteTasksSync(now)).thenReturn(createTasks(2));

        AgentEvent event = AgentEvent.now(AgentEvent.TYPE_PROACTIVE_TICK, "test", new JSONObject());
        ProactiveRule.RuleContext context = new ProactiveRule.RuleContext(
                mock(Context.class),
                database,
                null,
                now
        );

        Suggestion suggestion = rule.evaluate(event, context);

        assertNotNull(suggestion);
        assertEquals("MIDDAY_OVERLOAD", suggestion.getType());
        assertEquals("Buoi trua dang qua tai, nen chia nho cong viec", suggestion.getTitle());
    }

    @Test
    public void evaluate_returnsNull_whenNotOverloaded() {
        MiddayOverloadRule rule = new MiddayOverloadRule();
        long now = buildNowAtHour(13);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getIncompleteTasksForDaySync(anyLong(), anyLong())).thenReturn(createTasks(2));
        when(taskDao.getOverdueIncompleteTasksSync(now)).thenReturn(createTasks(0));

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
        calendar.set(Calendar.MINUTE, 15);
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
            task.setPriority(2);
            task.setCompleted(false);
            tasks.add(task);
        }
        return tasks;
    }
}
