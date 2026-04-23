package hcmute.edu.vn.tickticktodo.agent.tools;

import org.json.JSONException;
import org.json.JSONObject;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.ai.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.agent.integration.IntegrationFacade;
import hcmute.edu.vn.tickticktodo.agent.integration.model.HealthSummary;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;

public class GetHealthSummaryTool implements AgentTool {

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    @Override
    public String getName() {
        return AgentToolNames.GET_HEALTH_SUMMARY_TOOL;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Get health/activity summary used for planning and proactive decisions.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();
        JSONObject fromMillisSchema = new JSONObject();
        safePut(fromMillisSchema, "type", "number");
        safePut(fromMillisSchema, "description", "Optional start time in epoch millis.");
        safePut(properties, "fromMillis", fromMillisSchema);

        JSONObject toMillisSchema = new JSONObject();
        safePut(toMillisSchema, "type", "number");
        safePut(toMillisSchema, "description", "Optional end time in epoch millis.");
        safePut(properties, "toMillis", toMillisSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();
        long now = context.getNowMillis();
        long fromMillis = args == null ? now - DAY_MILLIS : args.optLong("fromMillis", now - DAY_MILLIS);
        long toMillis = args == null ? now : args.optLong("toMillis", now);

        IntegrationFacade facade = IntegrationFacade.getInstance(context.getApplication());
        HealthSummary summary = facade.getHealthSummary(fromMillis, toMillis);

        JSONObject data = new JSONObject();
        safePut(data, "renderType", "HEALTH_SUMMARY");
        safePut(data, "fromMillis", fromMillis);
        safePut(data, "toMillis", toMillis);
        safePut(data, "summary", summary.toJson());
        safePut(data, "integrationStatus", facade.buildIntegrationStatusJson());

        return ToolResult.success(call.getCallId(), getName(), data);
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}