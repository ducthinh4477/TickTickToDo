package hcmute.edu.vn.tickticktodo.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.ai.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;
import hcmute.edu.vn.tickticktodo.model.Habit;
import hcmute.edu.vn.tickticktodo.model.HabitLog;

/** Agent tool: list all habits with today's check-in status. */
public class ListHabitsTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.LIST_HABITS;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description",
                "List all habits with today's completion status and streak info.");
        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");
        safePut(parameters, "properties", new JSONObject());
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        List<Habit> habits = context.getDatabase().habitDao().getAllHabitsSync();

        long todayStart = startOfDayMillis();
        long todayEnd = todayStart + 24L * 60L * 60L * 1000L;

        JSONArray habitsJson = new JSONArray();
        if (habits != null) {
            for (Habit habit : habits) {
                // Check if completed today
                List<HabitLog> todayLogs = context.getDatabase().habitDao()
                        .getHabitLogsByRangeSync(habit.getId(), todayStart, todayEnd);
                boolean completedToday = false;
                if (todayLogs != null) {
                    for (HabitLog log : todayLogs) {
                        if (log.isCompleted()) { completedToday = true; break; }
                    }
                }

                JSONObject h = new JSONObject();
                safePut(h, "id", habit.getId());
                safePut(h, "name", habit.getName() == null ? "" : habit.getName());
                safePut(h, "icon", habit.getIcon() == null ? "" : habit.getIcon());
                safePut(h, "completedToday", completedToday);
                safePut(h, "reminderHour", habit.getReminderHour());
                safePut(h, "reminderMinute", habit.getReminderMinute());
                habitsJson.put(h);
            }
        }

        JSONObject result = new JSONObject();
        safePut(result, "count", habits == null ? 0 : habits.size());
        safePut(result, "habits", habitsJson);
        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private long startOfDayMillis() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void safePut(JSONObject t, String k, Object v) {
        try { t.put(k, v); } catch (JSONException ignored) {}
    }
}
