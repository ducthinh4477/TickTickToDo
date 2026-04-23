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
import hcmute.edu.vn.doinbot.helper.ReminderScheduler;
import hcmute.edu.vn.doinbot.model.Task;

/**
 * Creates multiple independent tasks in a single call.
 * Use when the user lists several tasks at once (e.g. "Thêm 3 việc: A, B, C vào chiều nay").
 */
public class CreateMultipleTasksTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.CREATE_MULTIPLE_TASKS;
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
                "Create multiple independent tasks in one call. " +
                "Use when the user lists several tasks at the same time.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        // tasks array
        JSONObject tasksSchema = new JSONObject();
        safePut(tasksSchema, "type", "array");
        safePut(tasksSchema, "description", "Array of tasks to create");

        JSONObject taskItem = new JSONObject();
        safePut(taskItem, "type", "object");

        JSONObject taskProps = new JSONObject();
        JSONObject titleProp = new JSONObject(); safePut(titleProp, "type", "string");
        JSONObject descProp = new JSONObject(); safePut(descProp, "type", "string");
        JSONObject dueDateProp = new JSONObject(); safePut(dueDateProp, "type", "integer");
        JSONObject priorityProp = new JSONObject();
        safePut(priorityProp, "type", "integer");
        safePut(priorityProp, "minimum", 0);
        safePut(priorityProp, "maximum", 3);
        safePut(priorityProp, "default", 1);
        JSONObject listIdProp = new JSONObject(); safePut(listIdProp, "type", "integer");

        safePut(taskProps, "title", titleProp);
        safePut(taskProps, "description", descProp);
        safePut(taskProps, "dueDate", dueDateProp);
        safePut(taskProps, "priority", priorityProp);
        safePut(taskProps, "listId", listIdProp);

        JSONArray required = new JSONArray();
        required.put("title");
        safePut(taskItem, "properties", taskProps);
        safePut(taskItem, "required", required);
        safePut(tasksSchema, "items", taskItem);
        safePut(properties, "tasks", tasksSchema);

        JSONArray topRequired = new JSONArray();
        topRequired.put("tasks");
        safePut(parameters, "properties", properties);
        safePut(parameters, "required", topRequired);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();
        JSONArray tasksArray = args.optJSONArray("tasks");

        if (tasksArray == null || tasksArray.length() == 0) {
            return ToolResult.failure(call.getCallId(), getName(),
                    "INVALID_ARGUMENT", "tasks array is required and must not be empty");
        }

        List<JSONObject> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (int i = 0; i < tasksArray.length(); i++) {
            Object raw = tasksArray.opt(i);
            JSONObject spec = null;
            if (raw instanceof JSONObject) {
                spec = (JSONObject) raw;
            } else if (raw instanceof String) {
                // Allow plain string titles as a convenience
                spec = new JSONObject();
                safePut(spec, "title", raw);
            }

            if (spec == null) continue;

            String title = spec.optString("title", "").trim();
            if (title.isEmpty()) {
                skipped.add("(empty title at index " + i + ")");
                continue;
            }

            String description = spec.optString("description", "").trim();
            Long dueDate = readOptionalMillis(spec, "dueDate");
            int priority = clamp(spec.optInt("priority", 1), 0, 3);
            Long listId = readOptionalPositiveLong(spec, "listId");

            Task task = new Task();
            task.setTitle(title);
            task.setDescription(description);
            task.setPriority(priority);
            task.setListId(listId);
            task.setSource("AI_AGENT");
            if (dueDate != null && dueDate > 0L) {
                task.setDueDate(dueDate);
            }

            long id = context.getDatabase().taskDao().insert(task);
            task.setId(id);
            ReminderScheduler.scheduleReminder(context.getApplication(), task);

            JSONObject taskJson = new JSONObject();
            safePut(taskJson, "id", id);
            safePut(taskJson, "title", title);
            safePut(taskJson, "dueDate", dueDate);
            safePut(taskJson, "priority", priority);
            created.add(taskJson);
        }

        JSONObject result = new JSONObject();
        safePut(result, "requestedCount", tasksArray.length());
        safePut(result, "createdCount", created.size());
        safePut(result, "skippedCount", skipped.size());

        JSONArray createdJson = new JSONArray();
        for (JSONObject t : created) createdJson.put(t);
        safePut(result, "createdTasks", createdJson);

        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private Long readOptionalMillis(JSONObject source, String key) {
        if (source == null || !source.has(key)) return null;
        Object raw = source.opt(key);
        if (raw instanceof Number) return ((Number) raw).longValue();
        if (raw instanceof String) {
            try { return Long.parseLong(((String) raw).trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Long readOptionalPositiveLong(JSONObject source, String key) {
        Long v = readOptionalMillis(source, key);
        return (v == null || v <= 0L) ? null : v;
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private void safePut(JSONObject target, String key, Object value) {
        try { target.put(key, value); } catch (JSONException ignored) {}
    }
}
