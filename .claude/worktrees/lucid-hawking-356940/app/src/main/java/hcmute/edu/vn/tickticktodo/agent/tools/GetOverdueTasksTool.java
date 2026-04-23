package hcmute.edu.vn.doinbot.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import hcmute.edu.vn.doinbot.agent.AgentExecutionContext;
import hcmute.edu.vn.doinbot.agent.AgentTool;
import hcmute.edu.vn.doinbot.ai.agent.AgentToolNames;
import hcmute.edu.vn.doinbot.core.ai.model.ToolCall;
import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;
import hcmute.edu.vn.doinbot.model.Task;

public class GetOverdueTasksTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.GET_OVERDUE_TASKS;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Return overdue and incomplete tasks with lateness metadata.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();
        JSONObject limitSchema = new JSONObject();
        safePut(limitSchema, "type", "integer");
        safePut(limitSchema, "minimum", 1);
        safePut(limitSchema, "maximum", 50);
        safePut(limitSchema, "default", 10);
        safePut(properties, "limit", limitSchema);
        safePut(parameters, "properties", properties);

        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        int limit = clamp(call.getArguments().optInt("limit", 10), 1, 50);
        long now = context.getNowMillis();

        List<Task> overdue = safeList(context.getDatabase().taskDao().getOverdueIncompleteTasksSync(now));
        JSONArray items = new JSONArray();

        int count = Math.min(limit, overdue.size());
        for (int i = 0; i < count; i++) {
            Task task = overdue.get(i);
            long dueDate = task.getDueDate() == null ? 0L : task.getDueDate();
            long latenessDays = dueDate <= 0L ? 0L : Math.max(1L, (now - dueDate) / (24L * 60L * 60L * 1000L));

            JSONObject taskJson = new JSONObject();
            safePut(taskJson, "id", task.getId());
            safePut(taskJson, "title", task.getTitle() == null ? "" : task.getTitle());
            safePut(taskJson, "dueDate", task.getDueDate());
            safePut(taskJson, "priority", task.getPriority());
            safePut(taskJson, "latenessDays", latenessDays);
            items.put(taskJson);
        }

        JSONObject result = new JSONObject();
        safePut(result, "count", count);
        safePut(result, "tasks", items);
        safePut(result, "nowMillis", now);

        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<Task> safeList(List<Task> tasks) {
        return tasks == null ? new ArrayList<>() : tasks;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
