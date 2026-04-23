package hcmute.edu.vn.doinbot.agent.proactive.rules;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.doinbot.agent.core.AgentEvent;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveConfig;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveRule;
import hcmute.edu.vn.doinbot.agent.proactive.Suggestion;
import hcmute.edu.vn.doinbot.model.Task;

public class WeeklyReviewRule implements ProactiveRule {

    private static final long WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    @Override
    public String getRuleId() {
        return "WEEKLY_REVIEW";
    }

    @Override
    public boolean supports(AgentEvent event) {
        if (event == null) {
            return false;
        }
        String type = event.getType();
        return AgentEvent.TYPE_PROACTIVE_TICK.equals(type)
                || AgentEvent.TYPE_CONTEXT_REFRESHED.equals(type)
                || AgentEvent.TYPE_TASK_CREATED.equals(type)
                || AgentEvent.TYPE_TASK_UPDATED.equals(type)
                || AgentEvent.TYPE_TASK_RESCHEDULED.equals(type);
    }

    @Override
    public Suggestion evaluate(AgentEvent event, RuleContext context) {
        long now = context.getNowMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);

        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        if (!isWeeklyReviewDay(dayOfWeek)) {
            return null;
        }

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (!isWeeklyReviewHour(hour)) {
            return null;
        }

        List<Task> allTasks = context.getDatabase().taskDao().getAllTasksSync();
        List<Task> overdue = context.getDatabase().taskDao().getOverdueIncompleteTasksSync(now);

        int overdueCount = overdue == null ? 0 : overdue.size();
        int upcomingWeekCount = countUpcomingWeekTasks(allTasks, now);
        int planningSignal = overdueCount + upcomingWeekCount;

        if (planningSignal < ProactiveConfig.WEEKLY_REVIEW_MIN_ITEMS) {
            return null;
        }

        float priority = Math.min(1.0f, 0.70f + (planningSignal * 0.015f));
        String dayLabel = (dayOfWeek == Calendar.MONDAY) ? "dau tuan" : "cuoi tuan";

        return new Suggestion(
                null,
                getRuleId(),
                "Can review ke hoach tuan",
                "Thoi diem " + dayLabel + ": " + overdueCount + " qua han, "
                        + upcomingWeekCount + " task sap den han trong 7 ngay toi.",
                0.81f,
                priority,
                now,
                now + ProactiveConfig.WEEKLY_REVIEW_TTL_MILLIS,
                true,
                Suggestion.STATUS_NEW
        );
    }

    private boolean isWeeklyReviewDay(int dayOfWeek) {
        return dayOfWeek == Calendar.MONDAY || dayOfWeek == Calendar.SUNDAY;
    }

    private boolean isWeeklyReviewHour(int hour) {
        return ProactiveConfig.isHourInRangeInclusive(
                hour,
                ProactiveConfig.WEEKLY_REVIEW_MORNING_START_HOUR,
                ProactiveConfig.WEEKLY_REVIEW_MORNING_END_HOUR
        )
                || ProactiveConfig.isHourInRangeInclusive(
                hour,
                ProactiveConfig.WEEKLY_REVIEW_EVENING_START_HOUR,
                ProactiveConfig.WEEKLY_REVIEW_EVENING_END_HOUR
        );
    }

    private int countUpcomingWeekTasks(List<Task> tasks, long now) {
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        long weekEnd = now + WEEK_MILLIS;
        int count = 0;
        for (Task task : tasks) {
            if (task == null || task.isCompleted()) {
                continue;
            }

            Long dueDate = task.getDueDate();
            if (dueDate == null) {
                continue;
            }

            if (dueDate >= now && dueDate <= weekEnd) {
                count++;
            }
        }
        return count;
    }
}
