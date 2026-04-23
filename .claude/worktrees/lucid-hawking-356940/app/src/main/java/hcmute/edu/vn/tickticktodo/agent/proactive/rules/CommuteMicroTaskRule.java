package hcmute.edu.vn.doinbot.agent.proactive.rules;

import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.doinbot.agent.context.ContextSnapshot;
import hcmute.edu.vn.doinbot.agent.core.AgentEvent;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveConfig;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveRule;
import hcmute.edu.vn.doinbot.agent.proactive.Suggestion;
import hcmute.edu.vn.doinbot.model.Task;

public class CommuteMicroTaskRule implements ProactiveRule {

    private static final long TWO_DAYS_MILLIS = 2L * 24L * 60L * 60L * 1000L;

    @Override
    public String getRuleId() {
        return "COMMUTE_MICRO_TASK";
    }

    @Override
    public boolean supports(AgentEvent event) {
        if (event == null) {
            return false;
        }

        String type = event.getType();
        return AgentEvent.TYPE_CONTEXT_REFRESHED.equals(type)
                || AgentEvent.TYPE_PROACTIVE_TICK.equals(type);
    }

    @Override
    public Suggestion evaluate(AgentEvent event, RuleContext context) {
        ContextSnapshot snapshot = context.getSnapshot();
        if (snapshot == null) {
            return null;
        }

        if (!"CELLULAR".equalsIgnoreCase(snapshot.getConnectivity())) {
            return null;
        }

        long now = context.getNowMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        boolean morningCommute = ProactiveConfig.isHourInRangeInclusive(
                hour,
                ProactiveConfig.COMMUTE_MORNING_START_HOUR,
                ProactiveConfig.COMMUTE_MORNING_END_HOUR
        );
        boolean eveningCommute = ProactiveConfig.isHourInRangeInclusive(
                hour,
                ProactiveConfig.COMMUTE_EVENING_START_HOUR,
                ProactiveConfig.COMMUTE_EVENING_END_HOUR
        );
        if (!morningCommute && !eveningCommute) {
            return null;
        }

        List<Task> allTasks = context.getDatabase().taskDao().getAllTasksSync();
        int quickCandidateCount = countQuickCandidates(allTasks, now);
        if (quickCandidateCount <= 0) {
            return null;
        }

        float priority = Math.min(1.0f, 0.62f + (quickCandidateCount * 0.025f));

        return new Suggestion(
                null,
                getRuleId(),
                "Khung di chuyen phu hop cho micro-task",
                "Dang o ket noi cellular va co " + quickCandidateCount
                        + " task gan han co the xu ly nhanh.",
                0.74f,
                priority,
                now,
                now + ProactiveConfig.COMMUTE_MICRO_TASK_TTL_MILLIS,
                false,
                Suggestion.STATUS_NEW
        );
    }

    private int countQuickCandidates(List<Task> tasks, long now) {
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }

        long horizon = now + TWO_DAYS_MILLIS;
        int count = 0;
        for (Task task : tasks) {
            if (task == null || task.isCompleted()) {
                continue;
            }

            Long dueDate = task.getDueDate();
            if (dueDate == null) {
                continue;
            }

            // Keep candidate selection simple: low/medium priority tasks due in next 2 days.
            if (dueDate >= now && dueDate <= horizon && task.getPriority() <= 2) {
                count++;
            }
        }
        return count;
    }
}
