package hcmute.edu.vn.tickticktodo.agent.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.agent.integration.IntegrationFacade;
import hcmute.edu.vn.tickticktodo.agent.integration.model.ExternalEvent;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;

public class GetAllEventsTool implements AgentTool {

    private static final long WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    @Override
    public String getName() {
        return AgentToolNames.GET_ALL_EVENTS_TOOL;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Get external events from integration sources in a time range.");

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

        JSONObject limitSchema = new JSONObject();
        safePut(limitSchema, "type", "integer");
        safePut(limitSchema, "default", 20);
        safePut(properties, "limit", limitSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();
        long now = context.getNowMillis();
        long fromMillis = args == null ? now : args.optLong("fromMillis", now);
        long toMillis = args == null ? now + WEEK_MILLIS : args.optLong("toMillis", now + WEEK_MILLIS);
        int limit = args == null ? 20 : Math.max(1, args.optInt("limit", 20));

        IntegrationFacade facade = IntegrationFacade.getInstance(context.getApplication());
        List<ExternalEvent> events = facade.getAllEvents(fromMillis, toMillis);

        JSONArray items = new JSONArray();
        int max = Math.min(limit, events.size());
        for (int i = 0; i < max; i++) {
            ExternalEvent event = events.get(i);
            if (event != null) {
                items.put(event.toJson());
            }
        }

        JSONObject data = new JSONObject();
        safePut(data, "renderType", "INTEGRATION_EVENTS");
        safePut(data, "fromMillis", fromMillis);
        safePut(data, "toMillis", toMillis);
        safePut(data, "count", events.size());
        safePut(data, "events", items);
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
