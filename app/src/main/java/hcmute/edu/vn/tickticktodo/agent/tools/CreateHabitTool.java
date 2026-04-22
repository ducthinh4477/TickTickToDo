package hcmute.edu.vn.tickticktodo.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.ai.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;
import hcmute.edu.vn.tickticktodo.helper.HabitAlarmManager;
import hcmute.edu.vn.tickticktodo.model.Habit;

/** Agent tool: create a new habit, optionally with a daily reminder time. */
public class CreateHabitTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.CREATE_HABIT;
    }

    @Override
    public boolean isMutation() {
        return true;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description",
                "Create a new habit tracker entry. Optionally set a daily reminder time.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject props = new JSONObject();

        JSONObject nameProp = new JSONObject();
        safePut(nameProp, "type", "string");
        safePut(nameProp, "description", "Name of the habit (e.g. 'Uống thuốc', 'Tập gym')");
        safePut(props, "name", nameProp);

        JSONObject iconProp = new JSONObject();
        safePut(iconProp, "type", "string");
        safePut(iconProp, "description", "Emoji or icon name for the habit");
        safePut(props, "icon", iconProp);

        JSONObject hourProp = new JSONObject();
        safePut(hourProp, "type", "integer");
        safePut(hourProp, "minimum", 0);
        safePut(hourProp, "maximum", 23);
        safePut(hourProp, "description", "Hour of daily reminder (0-23). -1 = no reminder.");
        safePut(props, "reminderHour", hourProp);

        JSONObject minProp = new JSONObject();
        safePut(minProp, "type", "integer");
        safePut(minProp, "minimum", 0);
        safePut(minProp, "maximum", 59);
        safePut(minProp, "default", 0);
        safePut(props, "reminderMinute", minProp);

        JSONArray required = new JSONArray();
        required.put("name");
        safePut(parameters, "properties", props);
        safePut(parameters, "required", required);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();

        String name = args.optString("name", "").trim();
        if (name.isEmpty()) {
            return ToolResult.failure(call.getCallId(), getName(),
                    "INVALID_ARGUMENT", "name is required");
        }

        String icon = args.optString("icon", "✅").trim();
        if (icon.isEmpty()) icon = "✅";

        int reminderHour = args.optInt("reminderHour", -1);
        int reminderMinute = args.optInt("reminderMinute", 0);

        // Validate range
        if (reminderHour < -1 || reminderHour > 23) reminderHour = -1;
        reminderMinute = Math.max(0, Math.min(59, reminderMinute));

        Habit habit = new Habit(name, icon);
        habit.setReminderHour(reminderHour);
        habit.setReminderMinute(reminderMinute);

        long habitId = context.getDatabase().habitDao().insertHabit(habit);
        habit.setId(habitId);

        // Schedule daily alarm if a reminder time was set
        if (reminderHour >= 0) {
            HabitAlarmManager.scheduleHabitReminder(context.getApplication(), habit);
        }

        JSONObject result = new JSONObject();
        safePut(result, "id", habitId);
        safePut(result, "name", name);
        safePut(result, "icon", icon);
        safePut(result, "reminderHour", reminderHour);
        safePut(result, "reminderMinute", reminderMinute);
        safePut(result, "reminderSet", reminderHour >= 0);

        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private void safePut(JSONObject t, String k, Object v) {
        try { t.put(k, v); } catch (JSONException ignored) {}
    }
}
