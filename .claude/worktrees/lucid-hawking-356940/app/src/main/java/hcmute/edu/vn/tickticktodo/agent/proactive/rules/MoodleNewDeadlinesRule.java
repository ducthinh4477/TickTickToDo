package hcmute.edu.vn.doinbot.agent.proactive.rules;

import hcmute.edu.vn.doinbot.agent.core.AgentEvent;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveConfig;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveRule;
import hcmute.edu.vn.doinbot.agent.proactive.Suggestion;

public class MoodleNewDeadlinesRule implements ProactiveRule {

    @Override
    public String getRuleId() {
        return "MOODLE_NEW_DEADLINES";
    }

    @Override
    public boolean supports(AgentEvent event) {
        return event != null && AgentEvent.TYPE_EXTERNAL_DEADLINES_SYNCED.equals(event.getType());
    }

    @Override
    public Suggestion evaluate(AgentEvent event, RuleContext context) {
        int newTasksCount = extractNewDeadlinesCount(event);
        if (newTasksCount <= 0) {
            return null;
        }

        long now = context.getNowMillis();
        return buildSuggestion(newTasksCount, now);
    }

    int extractNewDeadlinesCount(AgentEvent event) {
        if (event == null || event.getPayload() == null) {
            return 0;
        }
        // Backward compatibility: support both newTasksCount and newDeadlineCount keys.
        int count = event.getPayload().optInt("newTasksCount", -1);
        if (count >= 0) {
            return count;
        }
        return event.getPayload().optInt("newDeadlineCount", 0);
    }

    Suggestion buildSuggestion(int newTasksCount, long now) {
        float priority = Math.min(1.0f, 0.72f + (newTasksCount * 0.06f));

        return new Suggestion(
                null,
                getRuleId(),
                "Co deadline Moodle moi can sap xep",
                "Vua dong bo " + newTasksCount + " deadline moi tu Moodle. Nen lap ke hoach som de tranh don viec.",
                0.86f,
                priority,
                now,
                now + ProactiveConfig.MOODLE_DEADLINE_TTL_MILLIS,
                false,
                Suggestion.STATUS_NEW
        );
    }
}
