package hcmute.edu.vn.doinbot.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.doinbot.agent.AgentExecutionContext;
import hcmute.edu.vn.doinbot.agent.AgentTool;
import hcmute.edu.vn.doinbot.ai.agent.AgentToolNames;
import hcmute.edu.vn.doinbot.core.ai.model.ToolCall;
import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;
import hcmute.edu.vn.doinbot.model.Task;

public class CompleteTaskTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.COMPLETE_TASK_TOOL;
    }

    @Override
    public boolean isMutation() {
        return true;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Mark one task as completed by id or title.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        JSONObject idSchema = new JSONObject();
        safePut(idSchema, "type", "integer");
        safePut(properties, "id", idSchema);

        JSONObject titleSchema = new JSONObject();
        safePut(titleSchema, "type", "string");
        safePut(properties, "title", titleSchema);

        JSONObject matchModeSchema = new JSONObject();
        safePut(matchModeSchema, "type", "string");
        JSONArray enumValues = new JSONArray();
        enumValues.put("EXACT");
        enumValues.put("CONTAINS");
        safePut(matchModeSchema, "enum", enumValues);
        safePut(matchModeSchema, "default", "CONTAINS");
        safePut(properties, "matchMode", matchModeSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);

        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();

        long taskId = args.optLong("id", -1L);
        String title = args.optString("title", "").trim();
        String matchMode = normalizeMatchMode(args.optString("matchMode", "CONTAINS"));

        if (taskId <= 0L && title.isEmpty()) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "INVALID_ARGUMENT",
                    "Either id or title is required"
            );
        }

        List<Task> allTasks = safeList(context.getDatabase().taskDao().getAllTasksSync());
        Task target = null;

        if (taskId > 0L) {
            for (Task task : allTasks) {
                if (task != null && task.getId() == taskId) {
                    target = task;
                    break;
                }
            }
        }

        if (target == null && !title.isEmpty()) {
            String normalizedTitle = title.toLowerCase(Locale.ROOT);
            if ("EXACT".equals(matchMode)) {
                for (Task task : allTasks) {
                    if (task == null || task.getTitle() == null) {
                        continue;
                    }
                    if (task.getTitle().trim().equalsIgnoreCase(title)) {
                        target = task;
                        break;
                    }
                }
            } else {
                for (Task task : allTasks) {
                    if (task == null || task.getTitle() == null) {
                        continue;
                    }
                    String taskTitle = task.getTitle().toLowerCase(Locale.ROOT);
                    if (taskTitle.contains(normalizedTitle) || normalizedTitle.contains(taskTitle)) {
                        target = task;
                        break;
                    }
                }
            }
        }

        if (target == null) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "TASK_NOT_FOUND",
                    "Unable to find matching task"
            );
        }

        boolean alreadyCompleted = target.isCompleted();
        if (!alreadyCompleted) {
            context.getDatabase().taskDao().markTaskAsCompletedWithDate(
                    target.getId(),
                    true,
                    context.getNowMillis()
            );
        }

        JSONObject result = new JSONObject();
        safePut(result, "alreadyCompleted", alreadyCompleted);
        safePut(result, "task", toTaskJson(target, true));
        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private JSONObject toTaskJson(Task task, boolean completedOverride) {
        JSONObject json = new JSONObject();
        safePut(json, "id", task.getId());
        safePut(json, "title", task.getTitle() == null ? "" : task.getTitle());
        safePut(json, "description", task.getDescription() == null ? "" : task.getDescription());
        safePut(json, "dueDate", task.getDueDate());
        safePut(json, "priority", task.getPriority());
        safePut(json, "completed", completedOverride);
        return json;
    }

    private String normalizeMatchMode(String rawMode) {
        if (rawMode == null) {
            return "CONTAINS";
        }
        String value = rawMode.trim().toUpperCase(Locale.ROOT);
        return "EXACT".equals(value) ? "EXACT" : "CONTAINS";
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
