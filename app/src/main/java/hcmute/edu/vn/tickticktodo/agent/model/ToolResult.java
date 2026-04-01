package hcmute.edu.vn.tickticktodo.agent.model;

import org.json.JSONObject;
import org.json.JSONException;

public class ToolResult {

    private final String callId;
    private final String toolName;
    private final boolean success;
    private final JSONObject data;
    private final String errorCode;
    private final String errorMessage;

    public ToolResult(String callId,
                      String toolName,
                      boolean success,
                      JSONObject data,
                      String errorCode,
                      String errorMessage) {
        this.callId = callId == null ? "" : callId;
        this.toolName = toolName == null ? "" : toolName;
        this.success = success;
        this.data = data == null ? new JSONObject() : data;
        this.errorCode = errorCode == null ? "" : errorCode;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static ToolResult success(String callId, String toolName, JSONObject data) {
        return new ToolResult(callId, toolName, true, data, "", "");
    }

    public static ToolResult failure(String callId, String toolName, String errorCode, String errorMessage) {
        return new ToolResult(callId, toolName, false, new JSONObject(), errorCode, errorMessage);
    }

    public String getCallId() {
        return callId;
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isSuccess() {
        return success;
    }

    public JSONObject getData() {
        return data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        safePut(json, "callId", callId);
        safePut(json, "toolName", toolName);
        safePut(json, "success", success);
        safePut(json, "data", data);
        safePut(json, "errorCode", errorCode);
        safePut(json, "errorMessage", errorMessage);
        return json;
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
