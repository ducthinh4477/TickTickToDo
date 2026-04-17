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
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;
import hcmute.edu.vn.tickticktodo.model.Task;

public class EisenhowerSortTool implements AgentTool {

    private static final String Q1_URGENT_IMPORTANT = "URGENT_IMPORTANT";
    private static final String Q2_IMPORTANT_NOT_URGENT = "IMPORTANT_NOT_URGENT";
    private static final String Q3_URGENT_NOT_IMPORTANT = "URGENT_NOT_IMPORTANT";
    private static final String Q4_NOT_URGENT_NOT_IMPORTANT = "NOT_URGENT_NOT_IMPORTANT";

    @Override
    public String getName() {
        return AgentToolNames.EISENHOWER_SORT_TOOL;
    }

    @Override
    public boolean isMutation() {
        return true;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Classify tasks into Eisenhower quadrants and map them to TickTick priority values.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        JSONObject assignmentsSchema = new JSONObject();
        safePut(assignmentsSchema, "type", "array");
        JSONObject assignmentItem = new JSONObject();
        safePut(assignmentItem, "type", "object");
        JSONObject assignmentProperties = new JSONObject();
        JSONObject taskIdSchema = new JSONObject();
        safePut(taskIdSchema, "type", "integer");
        JSONObject quadrantSchema = new JSONObject();
        safePut(quadrantSchema, "type", "string");
        safePut(assignmentProperties, "taskId", taskIdSchema);
        safePut(assignmentProperties, "quadrant", quadrantSchema);
        safePut(assignmentItem, "properties", assignmentProperties);
        safePut(assignmentsSchema, "items", assignmentItem);
        safePut(properties, "assignments", assignmentsSchema);

        JSONObject taskIdsSchema = new JSONObject();
        safePut(taskIdsSchema, "type", "array");
        JSONObject taskIdsItem = new JSONObject();
        safePut(taskIdsItem, "type", "integer");
        safePut(taskIdsSchema, "items", taskIdsItem);
        safePut(properties, "taskIds", taskIdsSchema);

        JSONObject bulkQuadrantSchema = new JSONObject();
        safePut(bulkQuadrantSchema, "type", "string");
        safePut(properties, "quadrant", bulkQuadrantSchema);

        JSONObject querySchema = new JSONObject();
        safePut(querySchema, "type", "string");
        safePut(properties, "queryText", querySchema);

        JSONObject includeCompletedSchema = new JSONObject();
        safePut(includeCompletedSchema, "type", "boolean");
        safePut(includeCompletedSchema, "default", false);
        safePut(properties, "includeCompleted", includeCompletedSchema);

        JSONObject limitSchema = new JSONObject();
        safePut(limitSchema, "type", "integer");
        safePut(limitSchema, "minimum", 1);
        safePut(limitSchema, "maximum", 100);
        safePut(limitSchema, "default", 50);
        safePut(properties, "limit", limitSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();
        boolean includeCompleted = args.optBoolean("includeCompleted", false);

        JSONArray assignments = args.optJSONArray("assignments");
        if (assignments != null && assignments.length() > 0) {
            return executeExplicitAssignments(call, context, assignments, includeCompleted);
        }

        String bulkQuadrant = normalizeQuadrant(args.optString("quadrant", ""));
        if (bulkQuadrant.isEmpty()) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "INVALID_ARGUMENT",
                    "Can cung cap assignments hoac quadrant hop le."
            );
        }

        List<Task> targets = collectBulkTargets(args, context);
        int newPriority = priorityForQuadrant(bulkQuadrant);

        int updatedCount = 0;
        int skippedCompletedCount = 0;
        JSONArray updatedTasks = new JSONArray();

        for (Task task : targets) {
            if (task == null) {
                continue;
            }

            if (task.isCompleted() && !includeCompleted) {
                skippedCompletedCount++;
                continue;
            }

            int oldPriority = task.getPriority();
            if (oldPriority != newPriority) {
                task.setPriority(newPriority);
                context.getDatabase().taskDao().update(task);
            }

            updatedCount++;

            JSONObject row = new JSONObject();
            safePut(row, "id", task.getId());
            safePut(row, "title", task.getTitle() == null ? "" : task.getTitle());
            safePut(row, "oldPriority", oldPriority);
            safePut(row, "newPriority", newPriority);
            safePut(row, "quadrant", bulkQuadrant);
            updatedTasks.put(row);
        }

        JSONObject result = new JSONObject();
        safePut(result, "quadrant", bulkQuadrant);
        safePut(result, "targetCount", targets.size());
        safePut(result, "updatedCount", updatedCount);
        safePut(result, "skippedCompletedCount", skippedCompletedCount);
        safePut(result, "tasks", updatedTasks);

        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private ToolResult executeExplicitAssignments(ToolCall call,
                                                  AgentExecutionContext context,
                                                  JSONArray assignments,
                                                  boolean includeCompleted) {
        int updatedCount = 0;
        int skippedCompletedCount = 0;
        int invalidAssignmentsCount = 0;

        JSONArray updatedTasks = new JSONArray();

        for (int i = 0; i < assignments.length(); i++) {
            JSONObject assignment = assignments.optJSONObject(i);
            if (assignment == null) {
                invalidAssignmentsCount++;
                continue;
            }

            long taskId = assignment.optLong("taskId", -1L);
            String quadrant = normalizeQuadrant(assignment.optString("quadrant", ""));
            if (taskId <= 0L || quadrant.isEmpty()) {
                invalidAssignmentsCount++;
                continue;
            }

            Task task = context.getDatabase().taskDao().getTaskByIdSync(taskId);
            if (task == null) {
                invalidAssignmentsCount++;
                continue;
            }

            if (task.isCompleted() && !includeCompleted) {
                skippedCompletedCount++;
                continue;
            }

            int oldPriority = task.getPriority();
            int newPriority = priorityForQuadrant(quadrant);
            if (oldPriority != newPriority) {
                task.setPriority(newPriority);
                context.getDatabase().taskDao().update(task);
            }

            updatedCount++;

            JSONObject row = new JSONObject();
            safePut(row, "id", task.getId());
            safePut(row, "title", task.getTitle() == null ? "" : task.getTitle());
            safePut(row, "oldPriority", oldPriority);
            safePut(row, "newPriority", newPriority);
            safePut(row, "quadrant", quadrant);
            updatedTasks.put(row);
        }

        JSONObject result = new JSONObject();
        safePut(result, "updatedCount", updatedCount);
        safePut(result, "skippedCompletedCount", skippedCompletedCount);
        safePut(result, "invalidAssignmentsCount", invalidAssignmentsCount);
        safePut(result, "tasks", updatedTasks);

        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private List<Task> collectBulkTargets(JSONObject args, AgentExecutionContext context) {
        List<Long> taskIds = parseTaskIds(args.optJSONArray("taskIds"));
        if (!taskIds.isEmpty()) {
            return getTasksByIds(taskIds, context);
        }

        String queryText = args.optString("queryText", "").trim().toLowerCase(Locale.ROOT);
        if (queryText.isEmpty()) {
            return new ArrayList<>();
        }

        int limit = clamp(args.optInt("limit", 50), 1, 100);
        List<Task> allTasks = safeList(context.getDatabase().taskDao().getAllTasksSync());
        List<Task> matched = new ArrayList<>();

        for (Task task : allTasks) {
            if (task == null) {
                continue;
            }

            if (containsText(task, queryText)) {
                matched.add(task);
            }
        }

        matched.sort(Comparator
                .comparingLong((Task task) -> task.getDueDate() == null ? Long.MAX_VALUE : task.getDueDate())
                .thenComparing((a, b) -> Integer.compare(b.getPriority(), a.getPriority())));

        if (matched.size() > limit) {
            return new ArrayList<>(matched.subList(0, limit));
        }
        return matched;
    }

    private List<Task> getTasksByIds(List<Long> taskIds, AgentExecutionContext context) {
        List<Task> tasks = new ArrayList<>();
        for (Long taskId : taskIds) {
            if (taskId == null || taskId <= 0L) {
                continue;
            }

            Task task = context.getDatabase().taskDao().getTaskByIdSync(taskId);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    private List<Long> parseTaskIds(JSONArray taskIdsArray) {
        List<Long> taskIds = new ArrayList<>();
        if (taskIdsArray == null) {
            return taskIds;
        }

        for (int i = 0; i < taskIdsArray.length(); i++) {
            Object raw = taskIdsArray.opt(i);
            if (raw instanceof Number) {
                taskIds.add(((Number) raw).longValue());
            } else if (raw instanceof String) {
                String text = ((String) raw).trim();
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    taskIds.add(Long.parseLong(text));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return taskIds;
    }

    private String normalizeQuadrant(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "Q1":
            case "1":
            case Q1_URGENT_IMPORTANT:
                return Q1_URGENT_IMPORTANT;
            case "Q2":
            case "2":
            case Q2_IMPORTANT_NOT_URGENT:
                return Q2_IMPORTANT_NOT_URGENT;
            case "Q3":
            case "3":
            case Q3_URGENT_NOT_IMPORTANT:
                return Q3_URGENT_NOT_IMPORTANT;
            case "Q4":
            case "4":
            case "NEITHER":
            case Q4_NOT_URGENT_NOT_IMPORTANT:
                return Q4_NOT_URGENT_NOT_IMPORTANT;
            default:
                return "";
        }
    }

    private int priorityForQuadrant(String quadrant) {
        switch (quadrant) {
            case Q1_URGENT_IMPORTANT:
                return 3;
            case Q2_IMPORTANT_NOT_URGENT:
                return 2;
            case Q3_URGENT_NOT_IMPORTANT:
                return 1;
            case Q4_NOT_URGENT_NOT_IMPORTANT:
            default:
                return 0;
        }
    }

    private boolean containsText(Task task, String queryText) {
        String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase(Locale.ROOT);
        String description = task.getDescription() == null ? "" : task.getDescription().toLowerCase(Locale.ROOT);
        return title.contains(queryText) || description.contains(queryText);
    }

    private int clamp(int value, int min, int max) {
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