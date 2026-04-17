package hcmute.edu.vn.tickticktodo.agent.orchestrator;

import hcmute.edu.vn.tickticktodo.agent.AgentPromptContract;

public class PromptTemplateManager {

    private static final String ORCHESTRATOR_SYSTEM_PROMPT =
            "Bạn là Agent của TickTickToDo. Trả về JSON thuần, không markdown, không text ngoài JSON. " +
            "Nếu cần chạy công cụ, action phải là đúng tên tool có trong [TOOLS]. " +
            "Schema bắt buộc: {\"action\":\"<TOOL_NAME hoặc CHAT>\",\"payload\":{...},\"reply\":\"...\"}. " +
            "reply luôn viết tiếng Việt tự nhiên, đầy đủ ngữ cảnh app, ưu tiên 2-4 câu thay vì quá ngắn. " +
            "Nếu là thao tác tạo/cập nhật task, reply cần nêu rõ kết quả (tên task, hạn, độ ưu tiên nếu có).";

    public String buildPrompt(String userMessage, String contextBlock, String toolsBlock) {
        String systemPrompt = ORCHESTRATOR_SYSTEM_PROMPT + "\n[TOOLS]\n" + toolsBlock;
        return AgentPromptContract.buildPrompt(
                systemPrompt,
                contextBlock,
                "(managed-by-orchestrator)",
                userMessage
        );
    }
}
