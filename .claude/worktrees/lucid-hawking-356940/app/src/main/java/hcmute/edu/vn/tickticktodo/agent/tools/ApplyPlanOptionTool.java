package hcmute.edu.vn.doinbot.agent.tools;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import hcmute.edu.vn.doinbot.agent.AgentExecutionContext;
import hcmute.edu.vn.doinbot.agent.AgentTool;
import hcmute.edu.vn.doinbot.ai.agent.AgentToolNames;
import hcmute.edu.vn.doinbot.agent.scheduler.SchedulerAgent;
import hcmute.edu.vn.doinbot.core.ai.model.ToolCall;
import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;

public class ApplyPlanOptionTool implements AgentTool {

    @Override
    public String getName() {
        return AgentToolNames.APPLY_PLAN_OPTION_TOOL;
    }

    @Override
    public boolean isMutation() {
        return true;
    }

    @Override
    public boolean requiresConfirmation(ToolCall call) {
        if (call == null || call.getArguments() == null) {
            return true;
        }
        return !call.getArguments().optBoolean("confirmed", false);
    }

    @Override
    public JSONObject getSchema() {
        JSONObject schema = new JSONObject();
        safePut(schema, "name", getName());
        safePut(schema, "description", "Apply one plan option to task due dates after explicit confirmation.");

        JSONObject parameters = new JSONObject();
        safePut(parameters, "type", "object");

        JSONObject properties = new JSONObject();

        JSONObject proposalIdSchema = new JSONObject();
        safePut(proposalIdSchema, "type", "string");
        safePut(properties, "proposalId", proposalIdSchema);

        JSONObject optionIdSchema = new JSONObject();
        safePut(optionIdSchema, "type", "string");
        safePut(properties, "optionId", optionIdSchema);

        JSONObject confirmedSchema = new JSONObject();
        safePut(confirmedSchema, "type", "boolean");
        safePut(confirmedSchema, "default", false);
        safePut(properties, "confirmed", confirmedSchema);

        safePut(parameters, "properties", properties);
        safePut(schema, "parameters", parameters);
        return schema;
    }

    @Override
    public ToolResult execute(ToolCall call, AgentExecutionContext context) {
        JSONObject args = call.getArguments();
        String proposalId = args == null ? "" : args.optString("proposalId", "").trim();
        String optionId = args == null ? "" : args.optString("optionId", "").trim();
        boolean confirmed = args != null && args.optBoolean("confirmed", false);

        if (!confirmed) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "CONFIRMATION_REQUIRED",
                    "Bạn cần xác nhận trước khi áp dụng kế hoạch."
            );
        }

        if (TextUtils.isEmpty(proposalId) || TextUtils.isEmpty(optionId)) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "INVALID_ARGUMENT",
                    "proposalId và optionId là bắt buộc."
            );
        }

        SchedulerAgent schedulerAgent = SchedulerAgent.getInstance(context.getApplication());
        SchedulerAgent.ApplyPlanResult result = schedulerAgent.applyPlanOption(proposalId, optionId);
        if (result == null || !result.success) {
            return ToolResult.failure(
                    call.getCallId(),
                    getName(),
                    "PLAN_APPLY_FAILED",
                    result == null ? "Không thể áp dụng kế hoạch." : result.message
            );
        }

        JSONObject data = new JSONObject();
        safePut(data, "renderType", "PLAN_APPLY_RESULT");
        safePut(data, "proposalId", proposalId);
        safePut(data, "optionId", optionId);
        safePut(data, "appliedTaskCount", result.appliedTaskCount);
        safePut(data, "message", result.message);

        return ToolResult.success(call.getCallId(), getName(), data);
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
