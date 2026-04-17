package hcmute.edu.vn.tickticktodo.agent.orchestrator;

import android.app.Application;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentTool;
import hcmute.edu.vn.tickticktodo.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.agent.AgentToolRegistry;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;

public class ToolExecutionBridge {

    private final Application application;
    private final AgentToolRegistry toolRegistry;

    public ToolExecutionBridge(Application application, AgentToolRegistry toolRegistry) {
        this.application = application;
        this.toolRegistry = toolRegistry;
    }

    public JSONArray getToolSchemas() {
        return toolRegistry.getToolSchemas();
    }

    public ToolResult dispatchToolCall(ToolCall call) {
        AgentTool tool = toolRegistry.get(call.getToolName());
        if (tool == null) {
            return ToolResult.failure(
                    call.getCallId(),
                    call.getToolName(),
                    "TOOL_NOT_FOUND",
                    "Khong tim thay cong cu: " + call.getToolName()
            );
        }

        if (tool.requiresConfirmation(call)) {
            return ToolResult.failure(
                    call.getCallId(),
                    call.getToolName(),
                    "CONFIRMATION_REQUIRED",
                    "Cong cu yeu cau xac nhan nguoi dung truoc khi chay."
            );
        }

        try {
            return tool.execute(call, AgentExecutionContext.create(application));
        } catch (Exception e) {
            return ToolResult.failure(
                    call.getCallId(),
                    call.getToolName(),
                    "TOOL_EXECUTION_FAILED",
                    e.getMessage() == null ? "Tool execution failed" : e.getMessage()
            );
        }
    }

    public String renderToolResultSummary(ToolResult result) {
        if (result == null) {
            return "Minh chua co ket qua tu cong cu.";
        }

        if (!result.isSuccess()) {
            return "Cong cu " + result.getToolName() + " loi: " + result.getErrorMessage();
        }

        JSONObject data = result.getData();
        String toolName = result.getToolName();

        if (AgentToolNames.CREATE_TASK_WITH_SUBTASKS.equals(toolName)) {
            JSONObject parentTask = data == null ? null : data.optJSONObject("parentTask");
            String title = parentTask == null ? "" : parentTask.optString("title", "").trim();
            int subtaskCount = data == null ? 0 : data.optInt("createdSubtasksCount", 0);
            if (!TextUtils.isEmpty(title)) {
                if (subtaskCount > 0) {
                    return "Da tao task \"" + title + "\" cung " + subtaskCount + " subtask.";
                }
                return "Da tao task \"" + title + "\" thanh cong.";
            }
            return "Da tao task moi thanh cong.";
        }

        if (AgentToolNames.GET_TODAY_TASKS.equals(toolName)
                || AgentToolNames.GET_OVERDUE_TASKS.equals(toolName)
                || AgentToolNames.FIND_TASKS.equals(toolName)) {
            int count = data == null ? 0 : data.optInt("count", 0);
            JSONArray tasks = data == null ? null : data.optJSONArray("tasks");
            if (tasks != null && tasks.length() > 0) {
                JSONObject first = tasks.optJSONObject(0);
                String firstTitle = first == null ? "" : first.optString("title", "").trim();
                if (!TextUtils.isEmpty(firstTitle)) {
                    return "Minh tim thay " + count + " task. Goi y dau tien: \"" + firstTitle + "\".";
                }
            }
            return "Minh tim thay " + count + " task phu hop.";
        }

        if (AgentToolNames.RESCHEDULE_BULK_TASKS.equals(toolName)) {
            int count = data == null ? 0 : data.optInt("rescheduledCount", 0);
            return "Da doi lich " + count + " task thanh cong.";
        }

        return "Da chay cong cu " + toolName + " thanh cong.";
    }
}
