package hcmute.edu.vn.tickticktodo.agent.proactive.rules;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveConfig;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.Suggestion;
import hcmute.edu.vn.tickticktodo.model.Task;

public class MiddayOverloadRule implements ProactiveRule {

    @Override
    public String getRuleId() {
        return "MIDDAY_OVERLOAD";
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
                ProactiveConfig.MIDDAY_START_HOUR,
                ProactiveConfig.MIDDAY_END_HOUR
        )) {
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
        List<Task> overdue = context.getDatabase().taskDao().getOverdueIncompleteTasksSync(now);

        int todayCount = todayIncomplete == null ? 0 : todayIncomplete.size();
        int overdueCount = overdue == null ? 0 : overdue.size();

        if (!shouldTrigger(hour, todayCount, overdueCount)) {
            return null;
        }

        return buildSuggestion(todayCount, overdueCount, now);
    }

    boolean shouldTrigger(int hourOfDay, int todayCount, int overdueCount) {
        if (!ProactiveConfig.isHourInRangeInclusive(
                hourOfDay,
                ProactiveConfig.MIDDAY_START_HOUR,
                ProactiveConfig.MIDDAY_END_HOUR
        )) {
            return false;
        }

        return todayCount >= ProactiveConfig.MIDDAY_OVERLOAD_TASK_COUNT
                || (todayCount >= ProactiveConfig.MIDDAY_OVERLOAD_TASK_COUNT_WITH_OVERDUE
                && overdueCount >= ProactiveConfig.MIDDAY_OVERLOAD_OVERDUE_COUNT);
    }

    Suggestion buildSuggestion(int todayCount, int overdueCount, long now) {
        float priority = Math.min(1.0f, 0.70f + (todayCount * 0.03f));

        return new Suggestion(
                null,
                getRuleId(),
                "Buoi trua dang qua tai, nen chia nho cong viec",
                "Hien con " + todayCount + " task hom nay va " + overdueCount + " task qua han.",
                0.75f,
                priority,
                now,
                now + ProactiveConfig.MIDDAY_OVERLOAD_TTL_MILLIS,
                true,
                Suggestion.STATUS_NEW
        );
    }
}
