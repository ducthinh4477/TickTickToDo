package hcmute.edu.vn.doinbot.agent.proactive.rules;

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

import hcmute.edu.vn.doinbot.agent.core.AgentEvent;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveRule;
import hcmute.edu.vn.doinbot.agent.proactive.Suggestion;
import hcmute.edu.vn.doinbot.data.dao.TaskDao;
import hcmute.edu.vn.doinbot.data.database.TaskDatabase;
import hcmute.edu.vn.doinbot.model.Task;

public class WeeklyReviewRuleTest {

    @Test
    public void evaluate_emitsSuggestion_whenReviewWindowAndEnoughSignal() {
        WeeklyReviewRule rule = new WeeklyReviewRule();
        long now = buildNowAtDayAndHour(Calendar.MONDAY, 9);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getAllTasksSync()).thenReturn(createUpcomingTasks(now, 4));
        when(taskDao.getOverdueIncompleteTasksSync(now)).thenReturn(createOverdueTasks(2, now));

        AgentEvent event = AgentEvent.now(AgentEvent.TYPE_PROACTIVE_TICK, "test", new JSONObject());
        ProactiveRule.RuleContext context = new ProactiveRule.RuleContext(
                mock(Context.class),
                database,
                null,
                now
        );

        Suggestion suggestion = rule.evaluate(event, context);

        assertNotNull(suggestion);
        assertEquals("WEEKLY_REVIEW", suggestion.getType());
        assertEquals("Can review ke hoach tuan", suggestion.getTitle());
    }

    @Test
    public void evaluate_returnsNull_whenNotReviewDay() {
        WeeklyReviewRule rule = new WeeklyReviewRule();
        long now = buildNowAtDayAndHour(Calendar.WEDNESDAY, 9);

        TaskDatabase database = mock(TaskDatabase.class);
        TaskDao taskDao = mock(TaskDao.class);
        when(database.taskDao()).thenReturn(taskDao);
        when(taskDao.getAllTasksSync()).thenReturn(createUpcomingTasks(now, 6));
        when(taskDao.getOverdueIncompleteTasksSync(now)).thenReturn(createOverdueTasks(3, now));

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

    private long buildNowAtDayAndHour(int dayOfWeek, int hour) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private List<Task> createUpcomingTasks(long now, int count) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Task task = new Task();
            task.setId(i + 1L);
            task.setTitle("Upcoming " + i);
            task.setPriority(2);
            task.setCompleted(false);
            task.setDueDate(now + ((i + 1L) * 24L * 60L * 60L * 1000L));
            tasks.add(task);
        }
        return tasks;
    }

    private List<Task> createOverdueTasks(int count, long now) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Task task = new Task();
            task.setId(100L + i);
            task.setTitle("Overdue " + i);
            task.setPriority(3);
            task.setCompleted(false);
            task.setDueDate(now - ((i + 1L) * 60L * 60L * 1000L));
            tasks.add(task);
        }
        return tasks;
    }
}
