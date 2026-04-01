package hcmute.edu.vn.tickticktodo.agent;

import android.app.Application;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.Task;

public class AgentContextAssembler {

    private static final long THREE_DAYS_MILLIS = 3L * 24L * 60L * 60L * 1000L;

    private final TaskDatabase database;

    public AgentContextAssembler(Application application) {
        this.database = TaskDatabase.getInstance(application);
    }

    public String buildTieredContextBlock(String userMessage) {
        return buildTieredContextJson(userMessage).toString();
    }

    public JSONObject buildTieredContextJson(String userMessage) {
        JSONObject context = new JSONObject();
        safePut(context, "tier0", buildTier0Snapshot());
        safePut(context, "tier1", buildTier1Snapshot(userMessage));
        return context;
    }

    private JSONObject buildTier0Snapshot() {
        JSONObject tier0 = new JSONObject();
        long now = System.currentTimeMillis();
        long startOfDay = startOfDayMillis();
        long endOfDay = endOfDayMillis();

        List<Task> todayIncomplete = safeList(database.taskDao().getIncompleteTasksForDaySync(startOfDay, endOfDay));
        List<Task> overdue = safeList(database.taskDao().getOverdueIncompleteTasksSync(now));

        safePut(tier0, "nowMillis", now);
        safePut(tier0, "timezone", TimeZone.getDefault().getID());
        safePut(tier0, "todayIncompleteCount", todayIncomplete.size());
        safePut(tier0, "overdueCount", overdue.size());
        safePut(tier0, "activeFocusSession", false);
        safePut(tier0, "topHighPriorityToday", toTaskArray(limitByPriority(todayIncomplete, 3)));

        return tier0;
    }

    private JSONObject buildTier1Snapshot(String userMessage) {
        JSONObject tier1 = new JSONObject();
        long now = System.currentTimeMillis();

        List<Task> allTasks = safeList(database.taskDao().getAllTasksSync());
        List<Task> overdue = safeList(database.taskDao().getOverdueIncompleteTasksSync(now));

        List<Task> dueSoon = new ArrayList<>();
        List<Task> recentCompleted = new ArrayList<>();
        for (Task task : allTasks) {
            if (task == null) {
                continue;
            }

            Long dueDate = task.getDueDate();
            if (!task.isCompleted() && dueDate != null && dueDate >= now && dueDate <= now + THREE_DAYS_MILLIS) {
                dueSoon.add(task);
            }

            Long completedDate = task.getCompletedDate();
            if (task.isCompleted() && completedDate != null && completedDate >= now - (7L * 24L * 60L * 60L * 1000L)) {
                recentCompleted.add(task);
            }
        }

        dueSoon.sort(Comparator.comparingLong(task -> task.getDueDate() == null ? Long.MAX_VALUE : task.getDueDate()));
        recentCompleted.sort((a, b) -> Long.compare(
                b.getCompletedDate() == null ? 0L : b.getCompletedDate(),
                a.getCompletedDate() == null ? 0L : a.getCompletedDate()
        ));

        safePut(tier1, "userMessage", userMessage == null ? "" : userMessage);
        safePut(tier1, "overdueTasks", toTaskArray(limit(overdue, 8)));
        safePut(tier1, "dueSoonTasks", toTaskArray(limit(dueSoon, 8)));
        safePut(tier1, "recentCompletedTasks", toTaskArray(limit(recentCompleted, 6)));
        safePut(tier1, "recentHabits", new JSONArray());

        return tier1;
    }

    private JSONArray toTaskArray(List<Task> tasks) {
        JSONArray array = new JSONArray();
        for (Task task : tasks) {
            array.put(toTaskJson(task));
        }
        return array;
    }

    private JSONObject toTaskJson(Task task) {
        JSONObject json = new JSONObject();
        safePut(json, "id", task.getId());
        safePut(json, "title", task.getTitle() == null ? "" : task.getTitle());
        safePut(json, "description", task.getDescription() == null ? "" : task.getDescription());
        safePut(json, "dueDate", task.getDueDate());
        safePut(json, "priority", task.getPriority());
        safePut(json, "completed", task.isCompleted());
        safePut(json, "source", task.getSource() == null ? "" : task.getSource());
        return json;
    }

    private List<Task> limitByPriority(List<Task> tasks, int max) {
        List<Task> copy = new ArrayList<>(tasks);
        copy.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return limit(copy, max);
    }

    private List<Task> limit(List<Task> tasks, int max) {
        if (tasks.size() <= max) {
            return tasks;
        }
        return new ArrayList<>(tasks.subList(0, max));
    }

    private List<Task> safeList(List<Task> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private long startOfDayMillis() {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long endOfDayMillis() {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
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
