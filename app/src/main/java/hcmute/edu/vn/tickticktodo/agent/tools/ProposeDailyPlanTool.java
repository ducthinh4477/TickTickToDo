package hcmute.edu.vn.tickticktodo.agent.tools;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.ai.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.agent.scheduler.SchedulerAgent;
import hcmute.edu.vn.tickticktodo.agent.scheduler.SchedulerJsonMapper;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ScheduleProposal;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;

public class ProposeDailyPlanTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.PROPOSE_DAILY_PLAN_TOOL;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Generate daily schedule proposal with 2-3 options (Aggressive, Balanced, Low-stress).");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        JSONObject dateIsoSchema = new JSONObject();
        safePut(dateIsoSchema, "type", "string");
        safePut(dateIsoSchema, "description", "Optional date in yyyy-MM-dd. Defaults to today.");
        safePut(properties, "dateIso", dateIsoSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();
        LocalDate targetDate = parseDate(args == null ? "" : args.optString("dateIso", ""));

        SchedulerAgent schedulerAgent = SchedulerAgent.getInstance(context.getApplication());
        ScheduleProposal proposal = schedulerAgent.proposeDailyPlan(targetDate);

        JSONObject data = SchedulerJsonMapper.toProposalJson(proposal, true);
        safePut(data, "renderType", "PLAN_PROPOSAL");

        return ToolResult.success(call.getCallId(), getName(), data);
    }

    private LocalDate parseDate(String dateIso) {
        if (dateIso == null || dateIso.trim().isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateIso.trim());
        } catch (DateTimeParseException ignored) {
            return LocalDate.now();
        }
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
