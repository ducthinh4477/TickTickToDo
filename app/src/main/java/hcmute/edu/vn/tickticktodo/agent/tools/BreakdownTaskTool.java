package hcmute.edu.vn.tickticktodo.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;
import hcmute.edu.vn.tickticktodo.helper.AiTaskBreakdownHelper;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.model.Subtask;
import hcmute.edu.vn.tickticktodo.model.Task;

public class BreakdownTaskTool implements AgentTool {

    private static final int DEFAULT_MAX_STEPS = 5;
    private static final long DEFAULT_TIMEOUT_MILLIS = 35000L;

    @Override
    public String getName() {
        return AgentToolNames.BREAKDOWN_TASK_TOOL;
    }

    @Override
    public boolean isMutation() {
        return true;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Use AI to break one task into actionable subtasks and optionally save them as pending steps.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        JSONObject taskIdSchema = new JSONObject();
        safePut(taskIdSchema, "type", "integer");
        safePut(properties, "taskId", taskIdSchema);

        JSONObject taskTitleSchema = new JSONObject();
        safePut(taskTitleSchema, "type", "string");
        safePut(properties, "taskTitle", taskTitleSchema);

        JSONObject maxStepsSchema = new JSONObject();
        safePut(maxStepsSchema, "type", "integer");
        safePut(maxStepsSchema, "minimum", 1);
        safePut(maxStepsSchema, "maximum", 8);
        safePut(maxStepsSchema, "default", DEFAULT_MAX_STEPS);
        safePut(properties, "maxSteps", maxStepsSchema);

        JSONObject autoApplySchema = new JSONObject();
        safePut(autoApplySchema, "type", "boolean");
        safePut(autoApplySchema, "default", true);
        safePut(properties, "autoApply", autoApplySchema);

        JSONObject timeoutSchema = new JSONObject();
        safePut(timeoutSchema, "type", "integer");
        safePut(timeoutSchema, "minimum", 10000);
        safePut(timeoutSchema, "maximum", 90000);
        safePut(timeoutSchema, "default", DEFAULT_TIMEOUT_MILLIS);
        safePut(properties, "timeoutMillis", timeoutSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();

        long taskId = args.optLong("taskId", -1L);
        String taskTitle = args.optString("taskTitle", "").trim();
        int maxSteps = clamp(args.optInt("maxSteps", DEFAULT_MAX_STEPS), 1, 8);
        boolean autoApply = args.optBoolean("autoApply", true);
        long timeoutMillis = clampLong(args.optLong("timeoutMillis", DEFAULT_TIMEOUT_MILLIS), 10000L, 90000L);

        Task targetTask = resolveTask(context, taskId, taskTitle);
        if (targetTask == null) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "TASK_NOT_FOUND",
                    "Không tìm thấy task để breakdown."
            );
        }

        String prompt = AiTaskBreakdownHelper.buildPrompt(targetTask.getTitle());

        try {
            String rawResponse = GeminiManager.getInstance().generateResponseBlocking(prompt, timeoutMillis);
            List<String> parsedSteps = AiTaskBreakdownHelper.parseSteps(rawResponse);
            List<String> limitedSteps = limitSteps(parsedSteps, maxSteps);

            int createdSubtasksCount = 0;
            if (autoApply) {
                createdSubtasksCount = applySubtasks(context, targetTask.getId(), limitedSteps);
            }

            JSONObject result = new JSONObject();
            safePut(result, "taskId", targetTask.getId());
            safePut(result, "taskTitle", targetTask.getTitle() == null ? "" : targetTask.getTitle());
            safePut(result, "applied", autoApply);
            safePut(result, "createdSubtasksCount", createdSubtasksCount);
            safePut(result, "steps", toStepArray(limitedSteps));

            return ToolResult.success(call.getCallId(), getName(), result);
        } catch (JSONException jsonException) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "BREAKDOWN_PARSE_FAILED",
                    "AI trả về dữ liệu không hợp lệ để tách task."
            );
        } catch (Exception e) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "BREAKDOWN_FAILED",
                    e.getMessage() == null ? "Không thể tách task lúc này." : e.getMessage()
            );
        }
    }

    private Task resolveTask(AgentExecutionContext context, long taskId, String taskTitle) {
        if (taskId > 0L) {
            Task byId = context.getDatabase().taskDao().getTaskByIdSync(taskId);
            if (byId != null) {
                return byId;
            }
        }

        if (taskTitle == null || taskTitle.trim().isEmpty()) {
            return null;
        }

        List<Task> allTasks = safeList(context.getDatabase().taskDao().getAllTasksSync());
        String query = taskTitle.trim().toLowerCase(Locale.ROOT);
        Task fuzzyCandidate = null;

        for (Task task : allTasks) {
            if (task == null) {
                continue;
            }

            String title = task.getTitle() == null ? "" : task.getTitle().trim().toLowerCase(Locale.ROOT);
            if (title.equals(query)) {
                return task;
            }

            if (fuzzyCandidate == null && (title.contains(query) || query.contains(title))) {
                fuzzyCandidate = task;
            }
        }

        return fuzzyCandidate;
    }

    private int applySubtasks(AgentExecutionContext context, long taskId, List<String> steps) {
        context.getDatabase().subtaskDao().deleteUnapprovedByTaskId(taskId);

        Integer maxOrderIndex = context.getDatabase().subtaskDao().getMaxOrderIndex(taskId);
        int nextOrderIndex = maxOrderIndex == null ? 0 : maxOrderIndex + 1;

        List<Subtask> pendingSubtasks = new ArrayList<>();
        for (String step : steps) {
            if (step == null) {
                continue;
            }
            String cleanStep = step.trim();
            if (cleanStep.isEmpty()) {
                continue;
            }

            pendingSubtasks.add(new Subtask(taskId, cleanStep, false, false, 0, nextOrderIndex++));
        }

        if (!pendingSubtasks.isEmpty()) {
            context.getDatabase().subtaskDao().insertAll(pendingSubtasks);
        }

        context.getDatabase().taskDao().touchTask(taskId);
        return pendingSubtasks.size();
    }

    private List<String> limitSteps(List<String> steps, int maxSteps) {
        if (steps == null || steps.isEmpty()) {
            return new ArrayList<>();
        }
        if (steps.size() <= maxSteps) {
            return new ArrayList<>(steps);
        }
        return new ArrayList<>(steps.subList(0, maxSteps));
    }

    private JSONArray toStepArray(List<String> steps) {
        JSONArray array = new JSONArray();
        if (steps == null) {
            return array;
        }
        for (String step : steps) {
            array.put(step == null ? "" : step);
        }
        return array;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}