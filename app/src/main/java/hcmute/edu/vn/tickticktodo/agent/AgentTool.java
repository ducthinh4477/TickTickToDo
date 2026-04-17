package hcmute.edu.vn.tickticktodo.agent;

import org.json.JSONObject;

import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;

public interface AgentTool {

    String getName();

    JSONObject getSchema();

    ToolResult execute(ToolCall call, AgentExecutionContext context) throws Exception;

    default boolean isMutation() {
        return false;
    }

    default boolean requiresConfirmation(ToolCall call) {
        return false;
    }
}
