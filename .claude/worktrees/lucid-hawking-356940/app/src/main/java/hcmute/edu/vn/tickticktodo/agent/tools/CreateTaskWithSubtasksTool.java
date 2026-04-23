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

public class CreateTaskWithSubtasksTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.CREATE_TASK_WITH_SUBTASKS;
    }

    @Override
    public boolean isMutation() {
        return true;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Create one parent task and optional subtasks in a single mutation call.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        JSONObject titleSchema = new JSONObject();
        safePut(titleSchema, "type", "string");
        safePut(properties, "title", titleSchema);

        JSONObject descriptionSchema = new JSONObject();
        safePut(descriptionSchema, "type", "string");
        safePut(properties, "description", descriptionSchema);

        JSONObject dueDateSchema = new JSONObject();
        safePut(dueDateSchema, "type", "integer");
        safePut(properties, "dueDate", dueDateSchema);

        JSONObject prioritySchema = new JSONObject();
        safePut(prioritySchema, "type", "integer");
        safePut(prioritySchema, "minimum", 0);
        safePut(prioritySchema, "maximum", 3);
        safePut(prioritySchema, "default", 1);
        safePut(properties, "priority", prioritySchema);

        JSONObject listIdSchema = new JSONObject();
        safePut(listIdSchema, "type", "integer");
        safePut(properties, "listId", listIdSchema);

        JSONObject subtasksSchema = new JSONObject();
        safePut(subtasksSchema, "type", "array");
        JSONObject subtaskItemSchema = new JSONObject();
        safePut(subtaskItemSchema, "type", "object");

        JSONObject subtaskProperties = new JSONObject();
        JSONObject subtaskTitleSchema = new JSONObject();
        safePut(subtaskTitleSchema, "type", "string");
        safePut(subtaskProperties, "title", subtaskTitleSchema);

        JSONObject subtaskDescriptionSchema = new JSONObject();
        safePut(subtaskDescriptionSchema, "type", "string");
        safePut(subtaskProperties, "description", subtaskDescriptionSchema);

        JSONObject subtaskDueDateSchema = new JSONObject();
        safePut(subtaskDueDateSchema, "type", "integer");
        safePut(subtaskProperties, "dueDate", subtaskDueDateSchema);

        JSONObject subtaskPrioritySchema = new JSONObject();
        safePut(subtaskPrioritySchema, "type", "integer");
        safePut(subtaskPrioritySchema, "minimum", 0);
        safePut(subtaskPrioritySchema, "maximum", 3);
        safePut(subtaskProperties, "priority", subtaskPrioritySchema);

        safePut(subtaskItemSchema, "properties", subtaskProperties);
        safePut(subtasksSchema, "items", subtaskItemSchema);
        safePut(properties, "subtasks", subtasksSchema);

        JSONArray required = new JSONArray();
        required.put("title");

        safePut(parameters, "properties", properties);
        safePut(parameters, "required", required);
        safePut(schema, "parameters", parameters);

        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();

        String parentTitle = args.optString("title", "").trim();
        if (parentTitle.isEmpty()) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "INVALID_ARGUMENT",
                    "title is required"
            );
        }

        String parentDescription = args.optString("description", "").trim();
        Long parentDueDate = readOptionalMillis(args, "dueDate");
        int parentPriority = clamp(args.optInt("priority", 1), 0, 3);
        Long listId = readOptionalPositiveLong(args, "listId");

        Task parentTask = new Task();
        parentTask.setTitle(parentTitle);
        parentTask.setDescription(parentDescription);
        parentTask.setPriority(parentPriority);
        parentTask.setListId(listId);
        parentTask.setSource("AI_AGENT");
        if (parentDueDate != null && parentDueDate > 0L) {
            parentTask.setDueDate(parentDueDate);
        }

        long parentId = context.getDatabase().taskDao().insert(parentTask);
        parentTask.setId(parentId);
        ReminderScheduler.scheduleReminder(context.getApplication(), parentTask);

        JSONArray requestedSubtasks = args.optJSONArray("subtasks");
        List<Task> createdSubtasks = new ArrayList<>();

        if (requestedSubtasks != null) {
            for (int i = 0; i < requestedSubtasks.length(); i++) {
                JSONObject subtaskSpec = toSubtaskObject(requestedSubtasks, i);
                if (subtaskSpec == null) {
                    continue;
                }

                String subtaskTitle = subtaskSpec.optString("title", "").trim();
                if (subtaskTitle.isEmpty()) {
                    continue;
                }

                String rawSubtaskDescription = subtaskSpec.optString("description", "").trim();
                String subtaskDescription = rawSubtaskDescription.isEmpty()
                        ? "Subtask of: " + parentTitle
                        : rawSubtaskDescription + "\nSubtask of: " + parentTitle;

                Long subtaskDueDate = readOptionalMillis(subtaskSpec, "dueDate");
                if (subtaskDueDate == null) {
                    subtaskDueDate = parentDueDate;
                }

                int subtaskPriority = clamp(subtaskSpec.optInt("priority", parentPriority), 0, 3);

                Task subtask = new Task();
                subtask.setTitle(subtaskTitle);
                subtask.setDescription(subtaskDescription);
                subtask.setPriority(subtaskPriority);
                subtask.setListId(listId);
                subtask.setSource("AI_AGENT");
                if (subtaskDueDate != null && subtaskDueDate > 0L) {
                    subtask.setDueDate(subtaskDueDate);
                }

                long subtaskId = context.getDatabase().taskDao().insert(subtask);
                subtask.setId(subtaskId);
                ReminderScheduler.scheduleReminder(context.getApplication(), subtask);
                createdSubtasks.add(subtask);
            }
        }

        JSONObject result = new JSONObject();
        safePut(result, "parentTask", toTaskJson(parentTask));
        safePut(result, "requestedSubtasks", requestedSubtasks == null ? 0 : requestedSubtasks.length());
        safePut(result, "createdSubtasksCount", createdSubtasks.size());

        JSONArray createdSubtasksJson = new JSONArray();
        for (Task subtask : createdSubtasks) {
            createdSubtasksJson.put(toTaskJson(subtask));
        }
        safePut(result, "createdSubtasks", createdSubtasksJson);

        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private JSONObject toSubtaskObject(JSONArray subtasks, int index) {
        Object raw = subtasks.opt(index);
        if (raw instanceof JSONObject) {
            return (JSONObject) raw;
        }

        if (raw instanceof String) {
            String title = ((String) raw).trim();
            if (title.isEmpty()) {
                return null;
            }
            JSONObject object = new JSONObject();
            safePut(object, "title", title);
            return object;
        }

        return null;
    }

    private JSONObject toTaskJson(Task task) {
        JSONObject json = new JSONObject();
        safePut(json, "id", task.getId());
        safePut(json, "title", task.getTitle() == null ? "" : task.getTitle());
        safePut(json, "description", task.getDescription() == null ? "" : task.getDescription());
        safePut(json, "dueDate", task.getDueDate());
        safePut(json, "priority", task.getPriority());
        safePut(json, "listId", task.getListId());
        safePut(json, "completed", task.isCompleted());
        return json;
    }

    private Long readOptionalMillis(JSONObject source, String key) {
        if (source == null || !source.has(key)) {
            return null;
        }

        Object raw = source.opt(key);
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }

        if (raw instanceof String) {
            String text = ((String) raw).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private Long readOptionalPositiveLong(JSONObject source, String key) {
        Long value = readOptionalMillis(source, key);
        if (value == null || value <= 0L) {
            return null;
        }
        return value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
