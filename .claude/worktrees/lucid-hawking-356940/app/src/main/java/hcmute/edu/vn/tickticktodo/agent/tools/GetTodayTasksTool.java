package hcmute.edu.vn.doinbot.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;

import hcmute.edu.vn.doinbot.agent.AgentExecutionContext;
import hcmute.edu.vn.doinbot.agent.AgentTool;
import hcmute.edu.vn.doinbot.ai.agent.AgentToolNames;
import hcmute.edu.vn.doinbot.core.ai.model.ToolCall;
import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;
import hcmute.edu.vn.doinbot.model.Task;

public class GetTodayTasksTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.GET_TODAY_TASKS;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Return tasks due today with optional completed items.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();
        JSONObject limitSchema = new JSONObject();
        safePut(limitSchema, "type", "integer");
        safePut(limitSchema, "minimum", 1);
        safePut(limitSchema, "maximum", 50);
        safePut(limitSchema, "default", 10);

        JSONObject includeCompletedSchema = new JSONObject();
        safePut(includeCompletedSchema, "type", "boolean");
        safePut(includeCompletedSchema, "default", false);

        safePut(properties, "limit", limitSchema);
        safePut(properties, "includeCompleted", includeCompletedSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();
        int limit = clamp(args.optInt("limit", 10), 1, 50);
        boolean includeCompleted = args.optBoolean("includeCompleted", false);

        long start = startOfDayMillis();
        long end = endOfDayMillis();

        List<Task> all = safeList(context.getDatabase().taskDao().getAllTasksSync());
        List<Task> today = new ArrayList<>();
        for (Task task : all) {
            if (task == null) {
                continue;
            }
            Long dueDate = task.getDueDate();
            if (dueDate == null || dueDate < start || dueDate >= end) {
                continue;
            }
            if (!includeCompleted && task.isCompleted()) {
                continue;
            }
            today.add(task);
        }

        today.sort(Comparator.comparingLong(task -> task.getDueDate() == null ? Long.MAX_VALUE : task.getDueDate()));

        JSONArray items = new JSONArray();
        int count = Math.min(limit, today.size());
        for (int i = 0; i < count; i++) {
            items.put(toTaskJson(today.get(i)));
        }

        JSONObject result = new JSONObject();
        safePut(result, "count", count);
        safePut(result, "tasks", items);
        safePut(result, "windowStartMillis", start);
        safePut(result, "windowEndMillis", end);

        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private JSONObject toTaskJson(Task task) {
        JSONObject json = new JSONObject();
        safePut(json, "id", task.getId());
        safePut(json, "title", task.getTitle() == null ? "" : task.getTitle());
        safePut(json, "description", task.getDescription() == null ? "" : task.getDescription());
        safePut(json, "dueDate", task.getDueDate());
        safePut(json, "priority", task.getPriority());
        safePut(json, "completed", task.isCompleted());
        return json;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<Task> safeList(List<Task> tasks) {
        return tasks == null ? new ArrayList<>() : tasks;
    }

    private long startOfDayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long endOfDayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
