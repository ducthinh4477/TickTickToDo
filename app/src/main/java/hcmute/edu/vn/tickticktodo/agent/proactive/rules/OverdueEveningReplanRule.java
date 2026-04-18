package hcmute.edu.vn.tickticktodo.agent.proactive.rules;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveConfig;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.Suggestion;
import hcmute.edu.vn.tickticktodo.model.Task;

public class OverdueEveningReplanRule implements ProactiveRule {

    @Override
    public String getRuleId() {
        return "OVERDUE_EVENING_REPLAN";
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
                || AgentEvent.TYPE_TASK_COMPLETED.equals(type)
                || AgentEvent.TYPE_TASK_RESCHEDULED.equals(type);
    }

    @Override
    public Suggestion evaluate(AgentEvent event, RuleContext context) {
        long now = context.getNowMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (!ProactiveConfig.isHourInRangeInclusive(
                hour,
                ProactiveConfig.EVENING_START_HOUR,
                ProactiveConfig.EVENING_END_HOUR
        )) {
            return null;
        }

        List<Task> overdueTasks = context.getDatabase().taskDao().getOverdueIncompleteTasksSync(now);
        if (overdueTasks == null || overdueTasks.size() < ProactiveConfig.MIN_OVERDUE_FOR_REPLAN) {
            return null;
        }

        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(now);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 1);

        List<Task> todayIncomplete = context.getDatabase()
                .taskDao()
                .getIncompleteTasksForDaySync(start.getTimeInMillis(), end.getTimeInMillis());

        int overdueCount = overdueTasks.size();
        int todayIncompleteCount = todayIncomplete == null ? 0 : todayIncomplete.size();
        if (!shouldTrigger(hour, overdueCount, todayIncompleteCount)) {
            return null;
        }

        return buildSuggestion(overdueCount, todayIncompleteCount, now);
        }

        boolean shouldTrigger(int hourOfDay, int overdueCount, int todayIncompleteCount) {
        return ProactiveConfig.isHourInRangeInclusive(
            hourOfDay,
            ProactiveConfig.EVENING_START_HOUR,
            ProactiveConfig.EVENING_END_HOUR
        )
            && overdueCount >= ProactiveConfig.MIN_OVERDUE_FOR_REPLAN
            && todayIncompleteCount > 0;
        }

        Suggestion buildSuggestion(int overdueCount, int todayIncompleteCount, long now) {
        float priority = Math.min(1.0f, 0.78f + (overdueCount * 0.04f));

        return new Suggestion(
            null,
            getRuleId(),
            "Buoi toi nen replan task qua han",
            "Co " + overdueCount + " task qua han va " + todayIncompleteCount + " task con lai hom nay.",
            0.80f,
            priority,
            now,
            now + ProactiveConfig.OVERDUE_REPLAN_TTL_MILLIS,
            true,
            Suggestion.STATUS_NEW
        );
    }
}
