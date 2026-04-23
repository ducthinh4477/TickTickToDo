package hcmute.edu.vn.doinbot.agent;

import org.json.JSONObject;

import hcmute.edu.vn.doinbot.core.ai.model.ToolCall;
import hcmute.edu.vn.doinbot.core.ai.model.ToolResult;

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
