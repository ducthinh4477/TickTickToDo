package hcmute.edu.vn.doinbot.agent.proactive.rules;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.doinbot.agent.core.AgentEvent;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveConfig;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveRule;
import hcmute.edu.vn.doinbot.agent.proactive.Suggestion;
import hcmute.edu.vn.doinbot.model.Task;

public class DailyStartPlanningRule implements ProactiveRule {

    @Override
    public String getRuleId() {
        return "DAILY_START_PLANNING";
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
                || AgentEvent.TYPE_TASK_RESCHEDULED.equals(type)
                || AgentEvent.TYPE_TASK_COMPLETED.equals(type);
    }

    @Override
    public Suggestion evaluate(AgentEvent event, RuleContext context) {
        long now = context.getNowMillis();

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        if (!ProactiveConfig.isHourInRangeInclusive(
                hour,
                ProactiveConfig.MORNING_START_HOUR,
                ProactiveConfig.MORNING_END_HOUR
        )) {
            return null;
        }

        Calendar dayStart = Calendar.getInstance();
        dayStart.setTimeInMillis(now);
        dayStart.set(Calendar.HOUR_OF_DAY, 0);
        dayStart.set(Calendar.MINUTE, 0);
        dayStart.set(Calendar.SECOND, 0);
        dayStart.set(Calendar.MILLISECOND, 0);

        Calendar dayEnd = (Calendar) dayStart.clone();
        dayEnd.add(Calendar.DAY_OF_MONTH, 1);

        List<Task> todayIncomplete = context.getDatabase()
                .taskDao()
                .getIncompleteTasksForDaySync(dayStart.getTimeInMillis(), dayEnd.getTimeInMillis());
        List<Task> overdue = context.getDatabase().taskDao().getOverdueIncompleteTasksSync(now);

        int todayCount = todayIncomplete == null ? 0 : todayIncomplete.size();
        int overdueCount = overdue == null ? 0 : overdue.size();

        if (todayCount < ProactiveConfig.MORNING_PLANNING_MIN_TASKS && overdueCount <= 0) {
            return null;
        }

        float priority = Math.min(1.0f, 0.66f + (todayCount * 0.02f) + (overdueCount * 0.03f));

        return new Suggestion(
                null,
                getRuleId(),
                "Buoi sang nen chot ke hoach trong ngay",
                "Hom nay co " + todayCount + " task chua xong va " + overdueCount + " task qua han.",
                0.79f,
                priority,
                now,
                now + ProactiveConfig.MORNING_PLANNING_TTL_MILLIS,
                false,
                Suggestion.STATUS_NEW
        );
    }
}
