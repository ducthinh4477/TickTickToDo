package hcmute.edu.vn.doinbot.core.ai.model;

import org.json.JSONObject;

@Deprecated
public class ToolResult extends hcmute.edu.vn.doinbot.ai.llm.model.ToolResult {

    public ToolResult(String callId,
                      String toolName,
                      boolean success,
                      JSONObject data,
                      String errorCode,
                      String errorMessage) {
        super(callId, toolName, success, data, errorCode, errorMessage);
    }

    public static ToolResult success(String callId, String toolName, JSONObject data) {
        hcmute.edu.vn.doinbot.ai.llm.model.ToolResult delegated =
                hcmute.edu.vn.doinbot.ai.llm.model.ToolResult.success(callId, toolName, data);
        return new ToolResult(
                delegated.getCallId(),
                delegated.getToolName(),
                delegated.isSuccess(),
                delegated.getData(),
                delegated.getErrorCode(),
                delegated.getErrorMessage()
        );
    }

    public static ToolResult failure(String callId, String toolName, String errorCode, String errorMessage) {
        hcmute.edu.vn.doinbot.ai.llm.model.ToolResult delegated =
                hcmute.edu.vn.doinbot.ai.llm.model.ToolResult.failure(
                        callId,
                        toolName,
                        errorCode,
                        errorMessage
                );
        return new ToolResult(
                delegated.getCallId(),
                delegated.getToolName(),
                delegated.isSuccess(),
                delegated.getData(),
                delegated.getErrorCode(),
                delegated.getErrorMessage()
        );
    }
}
