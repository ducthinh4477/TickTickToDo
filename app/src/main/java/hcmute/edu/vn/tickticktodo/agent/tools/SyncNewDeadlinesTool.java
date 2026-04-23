package hcmute.edu.vn.tickticktodo.agent.tools;

import org.json.JSONException;
import org.json.JSONObject;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.ai.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.agent.integration.IntegrationFacade;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;

public class SyncNewDeadlinesTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.SYNC_NEW_DEADLINES_TOOL;
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Trigger deadline sync from external integrations (currently Moodle iCal)."
        );

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");
        safePut(parameters, "properties", new JSONObject());
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        IntegrationFacade facade = IntegrationFacade.getInstance(context.getApplication());
        boolean moodleEnabled = facade.isMoodleIntegrationEnabled();
        String workId = moodleEnabled ? facade.triggerDeadlineSync() : "";

        JSONObject data = new JSONObject();
        safePut(data, "renderType", "INTEGRATION_SYNC");
        safePut(data, "status", moodleEnabled ? "ENQUEUED" : "DISABLED");
        safePut(data, "workId", workId);
        safePut(data, "source", "MOODLE_ICAL");
        safePut(data, "message", moodleEnabled
                ? "Đã kích hoạt đồng bộ deadline từ Moodle ở nền."
                : "Nguồn Moodle đang tắt, chưa thể chạy đồng bộ deadline.");
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