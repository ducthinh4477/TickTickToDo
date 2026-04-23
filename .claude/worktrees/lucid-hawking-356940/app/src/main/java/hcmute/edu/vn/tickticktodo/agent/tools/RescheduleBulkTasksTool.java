package hcmute.edu.vn.doinbot.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.doinbot.agent.AgentExecutionContext;
import hcmute.edu.vn.doinbot.agent.AgentTool;
import hcmute.edu.vn.doinbot.ai.agent.AgentToolNames;
import hcmute.edu.vn.doinbot.core.ai.model.ToolCall;
import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;
import hcmute.edu.vn.doinbot.helper.ReminderScheduler;
import hcmute.edu.vn.doinbot.model.Task;

public class RescheduleBulkTasksTool implements AgentTool {

    private static final String SCOPE_OVERDUE_INCOMPLETE = "OVERDUE_INCOMPLETE";
    private static final String SCOPE_TODAY_INCOMPLETE = "TODAY_INCOMPLETE";
    private static final String SCOPE_ALL_INCOMPLETE = "ALL_INCOMPLETE";

    @Override
    public String getName() {
        return AgentToolNames.RESCHEDULE_BULK_TASKS;
    }

    @Override
    public boolean isMutation() {
        return true;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Reschedule multiple tasks by IDs or filter conditions in one operation.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        JSONObject taskIdsSchema = new JSONObject();
        safePut(taskIdsSchema, "type", "array");
        JSONObject taskIdItemSchema = new JSONObject();
        safePut(taskIdItemSchema, "type", "integer");
        safePut(taskIdsSchema, "items", taskIdItemSchema);
        safePut(properties, "taskIds", taskIdsSchema);

        JSONObject newDueDateSchema = new JSONObject();
        safePut(newDueDateSchema, "type", "integer");
        safePut(properties, "newDueDateMillis", newDueDateSchema);

        JSONObject shiftDaysSchema = new JSONObject();
        safePut(shiftDaysSchema, "type", "integer");
        safePut(shiftDaysSchema, "minimum", -30);
        safePut(shiftDaysSchema, "maximum", 30);
        safePut(properties, "shiftDays", shiftDaysSchema);

        JSONObject scopeSchema = new JSONObject();
        safePut(scopeSchema, "type", "string");
        safePut(scopeSchema, "default", SCOPE_ALL_INCOMPLETE);
        safePut(properties, "scope", scopeSchema);

        JSONObject queryTextSchema = new JSONObject();
        safePut(queryTextSchema, "type", "string");
        safePut(properties, "queryText", queryTextSchema);

        JSONObject includeCompletedSchema = new JSONObject();
        safePut(includeCompletedSchema, "type", "boolean");
        safePut(includeCompletedSchema, "default", false);
        safePut(properties, "includeCompleted", includeCompletedSchema);

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

        JSONObject limitSchema = new JSONObject();
        safePut(limitSchema, "type", "integer");
        safePut(limitSchema, "minimum", 1);
        safePut(limitSchema, "maximum", 200);
        safePut(limitSchema, "default", 50);
        safePut(properties, "limit", limitSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();

        Long targetDueDate = resolveTargetDueDate(args, context.getNowMillis());
        if (targetDueDate == null || targetDueDate <= 0L) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "INVALID_ARGUMENT",
                    "newDueDateMillis (or shiftDays) is required"
            );
        }

        List<Task> candidates = collectCandidates(args, context);
        boolean includeCompleted = args.optBoolean("includeCompleted", false);

        JSONArray rescheduledTasks = new JSONArray();
        int rescheduledCount = 0;
        int skippedCompleted = 0;

        for (Task task : candidates) {
            if (task == null) {
                continue;
            }

            if (task.isCompleted() && !includeCompleted) {
                skippedCompleted++;
                continue;
            }

            task.setDueDate(targetDueDate);
            context.getDatabase().taskDao().update(task);

            if (!task.isCompleted()) {
                ReminderScheduler.scheduleReminder(context.getApplication(), task);
            }

            rescheduledCount++;
            rescheduledTasks.put(toTaskJson(task));
        }

        JSONObject result = new JSONObject();
        safePut(result, "targetDueDateMillis", targetDueDate);
        safePut(result, "candidateCount", candidates.size());
        safePut(result, "rescheduledCount", rescheduledCount);
        safePut(result, "skippedCompletedCount", skippedCompleted);
        safePut(result, "tasks", rescheduledTasks);

        return ToolResult.success(call.getCallId(), getName(), result);
    }

    private List<Task> collectCandidates(JSONObject args, AgentExecutionContext context) {
        List<Long> ids = parseTaskIds(args.optJSONArray("taskIds"));
        if (!ids.isEmpty()) {
            return tasksById(ids, context);
        }

        boolean includeCompleted = args.optBoolean("includeCompleted", false);
        String scope = args.optString("scope", SCOPE_ALL_INCOMPLETE).trim().toUpperCase(Locale.ROOT);
        String queryText = args.optString("queryText", "").trim().toLowerCase(Locale.ROOT);
        int priorityMin = clamp(args.optInt("priorityMin", 0), 0, 3);
        int priorityMax = clamp(args.optInt("priorityMax", 3), 0, 3);
        int limit = clamp(args.optInt("limit", 50), 1, 200);

        if (priorityMin > priorityMax) {
            int tmp = priorityMin;
            priorityMin = priorityMax;
            priorityMax = tmp;
        }

        long now = context.getNowMillis();
        long startOfDay = startOfDayMillis(now);
        long endOfDay = endOfDayMillis(now);

        List<Task> allTasks = safeList(context.getDatabase().taskDao().getAllTasksSync());
        List<Task> filtered = new ArrayList<>();

        for (Task task : allTasks) {
            if (task == null) {
                continue;
            }

            if (!includeCompleted && task.isCompleted()) {
                continue;
            }

            if (!matchesScope(task, scope, now, startOfDay, endOfDay)) {
                continue;
            }

            if (!queryText.isEmpty() && !containsText(task, queryText)) {
                continue;
            }

            int priority = task.getPriority();
            if (priority < priorityMin || priority > priorityMax) {
                continue;
            }

            filtered.add(task);
        }

        filtered.sort(Comparator
                .comparingLong((Task task) -> task.getDueDate() == null ? Long.MAX_VALUE : task.getDueDate())
                .thenComparing((a, b) -> Integer.compare(b.getPriority(), a.getPriority())));

        if (filtered.size() > limit) {
            return new ArrayList<>(filtered.subList(0, limit));
        }
        return filtered;
    }

    private boolean matchesScope(Task task,
                                 String scope,
                                 long now,
                                 long startOfDay,
                                 long endOfDay) {
        Long dueDate = task.getDueDate();

        if (SCOPE_OVERDUE_INCOMPLETE.equals(scope)) {
            return dueDate != null && dueDate > 0L && dueDate < now && !task.isCompleted();
        }

        if (SCOPE_TODAY_INCOMPLETE.equals(scope)) {
            return dueDate != null
                    && dueDate >= startOfDay
                    && dueDate < endOfDay
                    && !task.isCompleted();
        }

        if (SCOPE_ALL_INCOMPLETE.equals(scope)) {
            return !task.isCompleted();
        }

        return true;
    }

    private boolean containsText(Task task, String queryText) {
        String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase(Locale.ROOT);
        String description = task.getDescription() == null ? "" : task.getDescription().toLowerCase(Locale.ROOT);
        return title.contains(queryText) || description.contains(queryText);
    }

    private List<Task> tasksById(List<Long> ids, AgentExecutionContext context) {
        List<Task> tasks = new ArrayList<>();
        for (Long id : ids) {
            if (id == null || id <= 0L) {
                continue;
            }
            Task task = context.getDatabase().taskDao().getTaskByIdSync(id);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    private List<Long> parseTaskIds(JSONArray rawIds) {
        List<Long> ids = new ArrayList<>();
        if (rawIds == null) {
            return ids;
        }

        for (int i = 0; i < rawIds.length(); i++) {
            Object raw = rawIds.opt(i);
            if (raw instanceof Number) {
                ids.add(((Number) raw).longValue());
            } else if (raw instanceof String) {
                String text = ((String) raw).trim();
                if (text.isEmpty()) {
                    continue;
                }
                try {
                    ids.add(Long.parseLong(text));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return ids;
    }

    private Long resolveTargetDueDate(JSONObject args, long nowMillis) {
        if (args.has("newDueDateMillis")) {
            Object raw = args.opt("newDueDateMillis");
            if (raw instanceof Number) {
                return ((Number) raw).longValue();
            }
            if (raw instanceof String) {
                try {
                    return Long.parseLong(((String) raw).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        if (args.has("shiftDays")) {
            int shiftDays = args.optInt("shiftDays", 0);
            return nowMillis + (shiftDays * 24L * 60L * 60L * 1000L);
        }

        return null;
    }

    private long startOfDayMillis(long nowMillis) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long endOfDayMillis(long nowMillis) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23);
        calendar.set(java.util.Calendar.MINUTE, 59);
        calendar.set(java.util.Calendar.SECOND, 59);
        calendar.set(java.util.Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private JSONObject toTaskJson(Task task) {
        JSONObject json = new JSONObject();
        safePut(json, "id", task.getId());
        safePut(json, "title", task.getTitle() == null ? "" : task.getTitle());
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

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
