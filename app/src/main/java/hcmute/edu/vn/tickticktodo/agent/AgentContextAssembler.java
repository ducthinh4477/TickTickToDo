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

import hcmute.edu.vn.tickticktodo.agent.context.ContextAgent;
import hcmute.edu.vn.tickticktodo.agent.context.ContextSnapshot;
import hcmute.edu.vn.tickticktodo.agent.integration.IntegrationFacade;
import hcmute.edu.vn.tickticktodo.agent.profile.ProfileAgent;
import hcmute.edu.vn.tickticktodo.helper.AppRuntimeState;
import hcmute.edu.vn.tickticktodo.helper.UserStatsManager;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.ActivityLog;
import hcmute.edu.vn.tickticktodo.model.ChatHistoryMessage;
import hcmute.edu.vn.tickticktodo.model.ChatSession;
import hcmute.edu.vn.tickticktodo.model.CountdownEvent;
import hcmute.edu.vn.tickticktodo.model.Habit;
import hcmute.edu.vn.tickticktodo.model.HabitLog;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.ui.countdown.TimerService;

public class AgentContextAssembler {

    private static final long THREE_DAYS_MILLIS = 3L * 24L * 60L * 60L * 1000L;
    private static final long SEVEN_DAYS_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    private static final long TWENTY_EIGHT_DAYS_MILLIS = 28L * 24L * 60L * 60L * 1000L;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private static final int MAX_TOP_PRIORITY_TODAY = 3;
    private static final int MAX_OVERDUE_TASKS = 8;
    private static final int MAX_DUE_SOON_TASKS = 8;
    private static final int MAX_RECENT_COMPLETED_TASKS = 6;
    private static final int MAX_RECENT_ACTIVITY_LOGS = 10;
    private static final int MAX_RECENT_CHAT_TURNS = 8;
    private static final int MAX_RECENT_HABITS = 4;
    private static final int MAX_UPCOMING_COUNTDOWN_EVENTS = 3;
    private static final int MAX_PAST_COUNTDOWN_EVENTS = 2;
    private static final int MAX_CHAT_CONTENT_CHARS = 180;
    private static final int MAX_ACTIVITY_TEXT_CHARS = 64;

    private final Application application;
    private final TaskDatabase database;
    private final ContextAgent contextAgent;
    private final ProfileAgent profileAgent;
    private final IntegrationFacade integrationFacade;

    public AgentContextAssembler(Application application) {
        this.application = application;
        this.database = TaskDatabase.getInstance(application);
        this.contextAgent = ContextAgent.getInstance(application);
        this.profileAgent = ProfileAgent.getInstance(application);
        this.integrationFacade = IntegrationFacade.getInstance(application);
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
        boolean isFocusRunning = TimerService.isTimerRunning();

        safePut(tier0, "nowMillis", now);
        safePut(tier0, "timezone", TimeZone.getDefault().getID());
        safePut(tier0, "appState", buildAppStateSnapshot(now));
        safePut(tier0, "todayIncompleteCount", todayIncomplete.size());
        safePut(tier0, "overdueCount", overdue.size());
        safePut(tier0, "activeFocusSession", isFocusRunning);
        safePut(tier0, "topHighPriorityToday", toTaskArray(limitByPriority(todayIncomplete, MAX_TOP_PRIORITY_TODAY)));
        safePut(tier0, "statsSnapshot", buildStatsSnapshot());
        safePut(tier0, "contextSnapshot", buildCompactContextSnapshot());
        safePut(tier0, "personaSummary", buildPersonaSummary());
        safePut(tier0, "integrationSummary", buildIntegrationSummary(now));

        return tier0;
    }

    private JSONObject buildIntegrationSummary(long nowMillis) {
        try {
            return integrationFacade.buildQuickSummaryJson(nowMillis);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private JSONObject buildCompactContextSnapshot() {
        ContextSnapshot snapshot = contextAgent.getLatestSnapshot();
        if (snapshot == null) {
            return new JSONObject();
        }
        return snapshot.toCompactJson();
    }

    private String buildPersonaSummary() {
        try {
            return profileAgent.buildPersonaSummary();
        } catch (Exception ignored) {
            return "Profile not ready";
        }
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
            if (task.isCompleted() && completedDate != null && completedDate >= now - SEVEN_DAYS_MILLIS) {
                recentCompleted.add(task);
            }
        }

        dueSoon.sort(Comparator.comparingLong(task -> task.getDueDate() == null ? Long.MAX_VALUE : task.getDueDate()));
        recentCompleted.sort((a, b) -> Long.compare(
                b.getCompletedDate() == null ? 0L : b.getCompletedDate(),
                a.getCompletedDate() == null ? 0L : a.getCompletedDate()
        ));

        safePut(tier1, "userMessage", userMessage == null ? "" : userMessage);
        safePut(tier1, "overdueTasks", toTaskArray(limit(overdue, MAX_OVERDUE_TASKS)));
        safePut(tier1, "dueSoonTasks", toTaskArray(limit(dueSoon, MAX_DUE_SOON_TASKS)));
        safePut(tier1, "recentCompletedTasks", toTaskArray(limit(recentCompleted, MAX_RECENT_COMPLETED_TASKS)));
        safePut(tier1, "recentActivityLogs", buildRecentActivityLogs());
        safePut(tier1, "recentChatMemory", buildRecentChatSummary());
        safePut(tier1, "recentCountdownEvents", buildRecentCountdownEvents(now));
        safePut(tier1, "recentHabits", buildRecentHabitSummary(now));

        return tier1;
    }

    private JSONObject buildAppStateSnapshot(long nowMillis) {
        AppRuntimeState.Snapshot snapshot = AppRuntimeState.getSnapshot(application);

        JSONObject appState = new JSONObject();
        safePut(appState, "currentScreen", snapshot.currentScreen);
        safePut(appState, "lastResumedAt", snapshot.lastResumedAt);
        safePut(appState, "appInForeground", snapshot.appInForeground);
        safePut(appState, "startedActivities", snapshot.startedActivities);
        safePut(appState, "isFocusTimerRunning", TimerService.isTimerRunning());
        if (snapshot.lastResumedAt > 0L) {
            safePut(appState, "millisSinceLastResume", Math.max(0L, nowMillis - snapshot.lastResumedAt));
        }
        return appState;
    }

    private JSONObject buildStatsSnapshot() {
        UserStatsManager.Stats stats = UserStatsManager.getInstance(application).getStats();

        JSONObject statsJson = new JSONObject();
        safePut(statsJson, "currentXP", stats.currentXP);
        safePut(statsJson, "level", stats.level);
        safePut(statsJson, "currentStreak", stats.currentStreak);
        safePut(statsJson, "xpInCurrentLevel", stats.getXpInCurrentLevel());
        return statsJson;
    }

    private JSONArray buildRecentActivityLogs() {
        List<ActivityLog> logs = safeList(database.activityLogDao().getRecentLogsSync(MAX_RECENT_ACTIVITY_LOGS));

        JSONArray array = new JSONArray();
        for (ActivityLog log : logs) {
            if (log == null) {
                continue;
            }

            JSONObject item = new JSONObject();
            safePut(item, "action", sanitizeText(log.action, MAX_ACTIVITY_TEXT_CHARS));
            safePut(item, "taskTitle", sanitizeText(log.taskTitle, MAX_ACTIVITY_TEXT_CHARS));
            safePut(item, "timestamp", log.timestamp);
            array.put(item);
        }
        return array;
    }

    private JSONArray buildRecentChatSummary() {
        JSONArray summary = new JSONArray();

        ChatSession latestSession = database.chatHistoryDao().getLatestSessionSync();
        if (latestSession == null) {
            return summary;
        }

        List<ChatHistoryMessage> messages = safeList(database.chatHistoryDao().getMessagesForSessionSync(latestSession.id));
        int fromIndex = Math.max(0, messages.size() - MAX_RECENT_CHAT_TURNS);

        for (int i = fromIndex; i < messages.size(); i++) {
            ChatHistoryMessage row = messages.get(i);
            if (row == null) {
                continue;
            }

            JSONObject message = new JSONObject();
            safePut(message, "role", row.role == null ? "" : row.role);
            safePut(message, "content", sanitizeText(row.content, MAX_CHAT_CONTENT_CHARS));
            safePut(message, "createdAt", row.createdAt);
            summary.put(message);
        }

        return summary;
    }

    private JSONArray buildRecentCountdownEvents(long nowMillis) {
        JSONArray events = new JSONArray();

        List<CountdownEvent> upcoming = safeList(
                database.countdownEventDao().getUpcomingEventsSync(nowMillis, MAX_UPCOMING_COUNTDOWN_EVENTS)
        );
        for (CountdownEvent event : upcoming) {
            events.put(toCountdownEventJson(event, "upcoming", nowMillis));
        }

        List<CountdownEvent> recentPast = safeList(
                database.countdownEventDao().getRecentPastEventsSync(nowMillis, MAX_PAST_COUNTDOWN_EVENTS)
        );
        for (CountdownEvent event : recentPast) {
            events.put(toCountdownEventJson(event, "past", nowMillis));
        }

        return events;
    }

    private JSONObject toCountdownEventJson(CountdownEvent event, String direction, long nowMillis) {
        JSONObject json = new JSONObject();
        if (event == null) {
            return json;
        }

        safePut(json, "id", event.getId());
        safePut(json, "title", event.getTitle() == null ? "" : event.getTitle());
        safePut(json, "dateMillis", event.getDateMillis());
        safePut(json, "direction", direction);
        safePut(json, "daysFromNow", (event.getDateMillis() - nowMillis) / DAY_MILLIS);
        return json;
    }

    private JSONArray buildRecentHabitSummary(long nowMillis) {
        List<Habit> habits = safeList(database.habitDao().getAllHabitsSync());

        List<HabitProgress> progressList = new ArrayList<>();
        long startWindow = nowMillis - TWENTY_EIGHT_DAYS_MILLIS;
        long sevenDaysAgo = nowMillis - SEVEN_DAYS_MILLIS;

        for (Habit habit : habits) {
            if (habit == null) {
                continue;
            }

            List<HabitLog> logs = safeList(
                    database.habitDao().getHabitLogsByRangeSync(habit.getId(), startWindow, nowMillis + DAY_MILLIS)
            );

            int completedLast28Days = 0;
            int completedLast7Days = 0;
            for (HabitLog log : logs) {
                if (log == null || !log.isCompleted()) {
                    continue;
                }

                completedLast28Days++;
                if (log.getDateMillis() >= sevenDaysAgo) {
                    completedLast7Days++;
                }
            }

            if (completedLast28Days == 0 && completedLast7Days == 0) {
                continue;
            }

            progressList.add(new HabitProgress(habit, completedLast7Days, completedLast28Days));
        }

        progressList.sort((a, b) -> {
            int byLast7 = Integer.compare(b.completedLast7Days, a.completedLast7Days);
            if (byLast7 != 0) {
                return byLast7;
            }
            return Integer.compare(b.completedLast28Days, a.completedLast28Days);
        });

        JSONArray habitsJson = new JSONArray();
        int count = Math.min(MAX_RECENT_HABITS, progressList.size());
        for (int i = 0; i < count; i++) {
            HabitProgress progress = progressList.get(i);
            JSONObject habitJson = new JSONObject();
            safePut(habitJson, "id", progress.habit.getId());
            safePut(habitJson, "name", progress.habit.getName() == null ? "" : progress.habit.getName());
            safePut(habitJson, "icon", progress.habit.getIcon() == null ? "" : progress.habit.getIcon());
            safePut(habitJson, "completedLast7Days", progress.completedLast7Days);
            safePut(habitJson, "completedLast28Days", progress.completedLast28Days);
            safePut(habitJson, "consistencyScore", Math.round((progress.completedLast28Days * 100f) / 28f));
            habitsJson.put(habitJson);
        }

        return habitsJson;
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

    private <T> List<T> safeList(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private String sanitizeText(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        String clean = text.replace('\n', ' ').trim();
        if (clean.length() <= maxLength) {
            return clean;
        }
        return clean.substring(0, maxLength) + "...";
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

    private static class HabitProgress {
        final Habit habit;
        final int completedLast7Days;
        final int completedLast28Days;

        HabitProgress(Habit habit, int completedLast7Days, int completedLast28Days) {
            this.habit = habit;
            this.completedLast7Days = completedLast7Days;
            this.completedLast28Days = completedLast28Days;
        }
    }
}
