package hcmute.edu.vn.tickticktodo.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.agent.model.ToolCall;
import hcmute.edu.vn.tickticktodo.agent.model.ToolResult;
import hcmute.edu.vn.tickticktodo.model.Task;

public class FindTasksTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.FIND_TASKS;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Find tasks by free-text and optional date/priority filters.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        JSONObject querySchema = new JSONObject();
        safePut(querySchema, "type", "string");
        safePut(properties, "queryText", querySchema);

        JSONObject fromSchema = new JSONObject();
        safePut(fromSchema, "type", "integer");
        safePut(properties, "fromDateMillis", fromSchema);

        JSONObject toSchema = new JSONObject();
        safePut(toSchema, "type", "integer");
        safePut(properties, "toDateMillis", toSchema);

        JSONObject priorityMinSchema = new JSONObject();
        safePut(priorityMinSchema, "type", "integer");
        safePut(priorityMinSchema, "minimum", 0);
        safePut(priorityMinSchema, "maximum", 3);
        safePut(properties, "priorityMin", priorityMinSchema);

        JSONObject priorityMaxSchema = new JSONObject();
        safePut(priorityMaxSchema, "type", "integer");
        safePut(priorityMaxSchema, "minimum", 0);
        safePut(priorityMaxSchema, "maximum", 3);
        safePut(properties, "priorityMax", priorityMaxSchema);

        JSONObject includeCompletedSchema = new JSONObject();
        safePut(includeCompletedSchema, "type", "boolean");
        safePut(includeCompletedSchema, "default", false);
        safePut(properties, "includeCompleted", includeCompletedSchema);

        JSONObject limitSchema = new JSONObject();
        safePut(limitSchema, "type", "integer");
        safePut(limitSchema, "minimum", 1);
        safePut(limitSchema, "maximum", 100);
        safePut(limitSchema, "default", 20);
        safePut(properties, "limit", limitSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();

        String queryText = args.optString("queryText", "").trim().toLowerCase(Locale.ROOT);
        boolean includeCompleted = args.optBoolean("includeCompleted", false);
        int priorityMin = clamp(args.optInt("priorityMin", 0), 0, 3);
        int priorityMax = clamp(args.optInt("priorityMax", 3), 0, 3);
        int limit = clamp(args.optInt("limit", 20), 1, 100);

        boolean hasFrom = args.has("fromDateMillis");
        boolean hasTo = args.has("toDateMillis");
        long from = args.optLong("fromDateMillis", Long.MIN_VALUE);
        long to = args.optLong("toDateMillis", Long.MAX_VALUE);

        if (priorityMin > priorityMax) {
            int tmp = priorityMin;
            priorityMin = priorityMax;
            priorityMax = tmp;
        }

        List<Task> allTasks = safeList(context.getDatabase().taskDao().getAllTasksSync());
        List<Task> filtered = new ArrayList<>();

        for (Task task : allTasks) {
            if (task == null) {
                continue;
            }

            if (!includeCompleted && task.isCompleted()) {
                continue;
            }

            int priority = task.getPriority();
            if (priority < priorityMin || priority > priorityMax) {
                continue;
            }

            if (!queryText.isEmpty() && !containsText(task, queryText)) {
                continue;
            }

            Long dueDate = task.getDueDate();
            if (hasFrom || hasTo) {
                if (dueDate == null) {
                    continue;
                }
                if (hasFrom && dueDate < from) {
                    continue;
                }
                if (hasTo && dueDate > to) {
                    continue;
                }
            }

            filtered.add(task);
        }

        filtered.sort(Comparator
                .comparingLong((Task task) -> task.getDueDate() == null ? Long.MAX_VALUE : task.getDueDate())
                .thenComparing((a, b) -> Integer.compare(b.getPriority(), a.getPriority())));

        JSONArray items = new JSONArray();
        int count = Math.min(limit, filtered.size());
        for (int i = 0; i < count; i++) {
            Task task = filtered.get(i);
            JSONObject taskJson = new JSONObject();
            safePut(taskJson, "id", task.getId());
            safePut(taskJson, "title", task.getTitle() == null ? "" : task.getTitle());
            safePut(taskJson, "description", task.getDescription() == null ? "" : task.getDescription());
            safePut(taskJson, "dueDate", task.getDueDate());
            safePut(taskJson, "priority", task.getPriority());
            safePut(taskJson, "completed", task.isCompleted());
            items.put(taskJson);
        }

        JSONObject result = new JSONObject();
        safePut(result, "count", count);
        safePut(result, "tasks", items);

        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private boolean containsText(Task task, String text) {
        String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase(Locale.ROOT);
        String description = task.getDescription() == null ? "" : task.getDescription().toLowerCase(Locale.ROOT);
        return title.contains(text) || description.contains(text);
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
