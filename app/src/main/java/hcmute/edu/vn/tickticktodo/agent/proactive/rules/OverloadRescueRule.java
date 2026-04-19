package hcmute.edu.vn.tickticktodo.agent.proactive.rules;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.context.ContextSnapshot;
import hcmute.edu.vn.tickticktodo.agent.core.AgentEvent;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveConfig;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveRule;
import hcmute.edu.vn.tickticktodo.agent.proactive.Suggestion;
import hcmute.edu.vn.tickticktodo.model.Task;

public class OverloadRescueRule implements ProactiveRule {

    @Override
    public String getRuleId() {
        return "OVERLOAD_RESCUE";
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
        ContextSnapshot snapshot = context.getSnapshot();
        if (snapshot != null && !snapshot.isCharging()
                && snapshot.getBatteryLevel() >= 0
                && snapshot.getBatteryLevel() < ProactiveConfig.OVERLOAD_RESCUE_BATTERY_MIN) {
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

        if (todayCount < ProactiveConfig.OVERLOAD_RESCUE_TASK_COUNT
                && overdueCount < ProactiveConfig.OVERLOAD_RESCUE_OVERDUE_COUNT) {
            return null;
        }

        float priority = Math.min(1.0f, 0.78f + (todayCount * 0.02f) + (overdueCount * 0.03f));

        return new Suggestion(
                null,
                getRuleId(),
                "Lich dang qua tai, nen kich hoat che do cuu hoa",
                "Con " + todayCount + " task hom nay va " + overdueCount
                        + " task qua han. Nen tao phuong an giam tai ngay.",
                0.83f,
                priority,
                now,
                now + ProactiveConfig.OVERLOAD_RESCUE_TTL_MILLIS,
                true,
                Suggestion.STATUS_NEW
        );
    }
}
