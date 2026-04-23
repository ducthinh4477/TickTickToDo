package hcmute.edu.vn.doinbot.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.doinbot.agent.AgentExecutionContext;
import hcmute.edu.vn.doinbot.agent.AgentTool;
import hcmute.edu.vn.doinbot.ai.agent.AgentToolNames;
import hcmute.edu.vn.doinbot.core.ai.model.ToolCall;
import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;
import hcmute.edu.vn.doinbot.model.Habit;
import hcmute.edu.vn.doinbot.model.HabitLog;

/** Agent tool: mark a habit as completed for today. */
public class CheckInHabitTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.CHECKIN_HABIT;
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
                "Mark a habit as completed for today. Identify by id (preferred) or name.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");
        JSONObject props = new JSONObject();

        JSONObject idProp = new JSONObject();
        safePut(idProp, "type", "integer");
        safePut(idProp, "description", "Habit id");
        safePut(props, "id", idProp);

        JSONObject nameProp = new JSONObject();
        safePut(nameProp, "type", "string");
        safePut(nameProp, "description", "Habit name (partial match, case-insensitive)");
        safePut(props, "name", nameProp);

        safePut(parameters, "properties", props);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();
        long habitId = args.optLong("id", -1L);
        String nameQuery = args.optString("name", "").trim();

        if (habitId <= 0 && nameQuery.isEmpty()) {
            return ToolResult.failure(call.getCallId(), getName(),
                    "INVALID_ARGUMENT", "Provide id or name to identify the habit");
        }

        List<Habit> all = context.getDatabase().habitDao().getAllHabitsSync();
        if (all == null || all.isEmpty()) {
            return ToolResult.failure(call.getCallId(), getName(), "NOT_FOUND", "No habits found");
        }

        Habit target = null;
        if (habitId > 0) {
            for (Habit h : all) {
                if (h.getId() == habitId) { target = h; break; }
            }
        }
        if (target == null && !nameQuery.isEmpty()) {
            String lower = nameQuery.toLowerCase();
            for (Habit h : all) {
                String hName = h.getName() == null ? "" : h.getName().toLowerCase();
                if (hName.contains(lower)) { target = h; break; }
            }
        }
        if (target == null) {
            return ToolResult.failure(call.getCallId(), getName(), "NOT_FOUND",
                    "No habit matching '" + (habitId > 0 ? habitId : nameQuery) + "'");
        }

        long todayStart = startOfDayMillis();
        context.getDatabase().habitDao()
                .upsertHabitLog(new HabitLog(target.getId(), todayStart, true));

        JSONObject result = new JSONObject();
        safePut(result, "habitId", target.getId());
        safePut(result, "habitName", target.getName() == null ? "" : target.getName());
        safePut(result, "checkedInDate", todayStart);
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
