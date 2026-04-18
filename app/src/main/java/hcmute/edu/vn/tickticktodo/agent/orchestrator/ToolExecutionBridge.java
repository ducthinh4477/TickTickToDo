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
                    "Không tìm thấy công cụ: " + call.getToolName()
            );
        }

        if (tool.requiresConfirmation(call)) {
            return ToolResult.failure(
                    call.getCallId(),
                    call.getToolName(),
                    "CONFIRMATION_REQUIRED",
                    "Công cụ yêu cầu xác nhận người dùng trước khi chạy."
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
            return "Mình chưa có kết quả từ công cụ.";
        }

        if (!result.isSuccess()) {
            return "Công cụ " + result.getToolName() + " lỗi: " + result.getErrorMessage();
        }

        JSONObject data = result.getData();
        String toolName = result.getToolName();

        if (AgentToolNames.CREATE_TASK_WITH_SUBTASKS.equals(toolName)) {
            JSONObject parentTask = data == null ? null : data.optJSONObject("parentTask");
            String title = parentTask == null ? "" : parentTask.optString("title", "").trim();
            int subtaskCount = data == null ? 0 : data.optInt("createdSubtasksCount", 0);
            if (!TextUtils.isEmpty(title)) {
                if (subtaskCount > 0) {
                    return "Đã tạo task \"" + title + "\" cùng " + subtaskCount + " subtask.";
                }
                return "Đã tạo task \"" + title + "\" thành công.";
            }
            return "Đã tạo task mới thành công.";
        }

        if (AgentToolNames.COMPLETE_TASK_TOOL.equals(toolName)) {
            JSONObject taskJson = data == null ? null : data.optJSONObject("task");
            String title = taskJson == null ? "" : taskJson.optString("title", "").trim();
            boolean alreadyCompleted = data != null && data.optBoolean("alreadyCompleted", false);
            if (TextUtils.isEmpty(title)) {
                return alreadyCompleted
                        ? "Task này đã ở trạng thái hoàn thành từ trước."
                        : "Đã đánh dấu task là hoàn thành.";
            }
            return alreadyCompleted
                    ? "Task \"" + title + "\" đã được hoàn thành từ trước."
                    : "Đã đánh dấu hoàn thành task \"" + title + "\".";
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
                    return "Mình tìm thấy " + count + " task. Gợi ý đầu tiên: \"" + firstTitle + "\".";
                }
            }
            return "Mình tìm thấy " + count + " task phù hợp.";
        }

        if (AgentToolNames.RESCHEDULE_BULK_TASKS.equals(toolName)) {
            int count = data == null ? 0 : data.optInt("rescheduledCount", 0);
            return "Đã dời lịch " + count + " task thành công.";
        }

        if (AgentToolNames.START_POMODORO_TOOL.equals(toolName)) {
            int minutes = data == null ? 25 : data.optInt("minutes", 25);
            boolean countdownCreated = data != null && data.optBoolean("countdownEventCreated", false);
            if (countdownCreated) {
                return "Đã bắt đầu Pomodoro " + minutes + " phút và lưu mốc đếm ngược kết thúc phiên.";
            }
            return "Đã bắt đầu Pomodoro " + minutes + " phút.";
        }

        if (AgentToolNames.EISENHOWER_SORT_TOOL.equals(toolName)) {
            int updatedCount = data == null ? 0 : data.optInt("updatedCount", 0);
            String quadrant = data == null ? "" : data.optString("quadrant", "");
            if (updatedCount <= 0) {
                return "Không có task nào được cập nhật theo ma trận Eisenhower.";
            }
            if (!TextUtils.isEmpty(quadrant)) {
                return "Đã sắp xếp " + updatedCount + " task vào nhóm " + renderQuadrantLabel(quadrant) + ".";
            }
            return "Đã cập nhật phân loại Eisenhower cho " + updatedCount + " task.";
        }

        if (AgentToolNames.BREAKDOWN_TASK_TOOL.equals(toolName)) {
            String taskTitle = data == null ? "" : data.optString("taskTitle", "").trim();
            int createdSubtasksCount = data == null ? 0 : data.optInt("createdSubtasksCount", 0);
            boolean applied = data != null && data.optBoolean("applied", false);
            JSONArray steps = data == null ? null : data.optJSONArray("steps");
            int suggestedSteps = steps == null ? 0 : steps.length();

            if (applied) {
                if (!TextUtils.isEmpty(taskTitle)) {
                    return "Đã tách task \"" + taskTitle + "\" thành " + createdSubtasksCount + " bước để bạn duyệt.";
                }
                return "Đã tạo " + createdSubtasksCount + " bước breakdown để bạn duyệt.";
            }

            return "Mình đã gợi ý " + suggestedSteps + " bước breakdown. Bạn có thể yêu cầu áp dụng ngay nếu đồng ý.";
        }

        return "Đã chạy công cụ " + toolName + " thành công.";
    }

    private String renderQuadrantLabel(String quadrant) {
        if (TextUtils.isEmpty(quadrant)) {
            return "không xác định";
        }

        switch (quadrant.trim().toUpperCase()) {
            case "URGENT_IMPORTANT":
                return "Q1 (Khẩn cấp và Quan trọng)";
            case "IMPORTANT_NOT_URGENT":
                return "Q2 (Quan trọng, không khẩn cấp)";
            case "URGENT_NOT_IMPORTANT":
                return "Q3 (Khẩn cấp, ít quan trọng)";
            case "NOT_URGENT_NOT_IMPORTANT":
                return "Q4 (Không khẩn cấp, không quan trọng)";
            default:
                return quadrant;
        }
    }
}
