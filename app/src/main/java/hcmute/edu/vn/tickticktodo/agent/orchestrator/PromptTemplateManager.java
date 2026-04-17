package hcmute.edu.vn.tickticktodo.agent.orchestrator;

import hcmute.edu.vn.tickticktodo.agent.AgentPromptContract;

public class PromptTemplateManager {

    private static final String ORCHESTRATOR_SYSTEM_PROMPT =
            "Ban la Agent cua TickTickToDo. Tra ve JSON thuan, khong markdown, khong text ngoai JSON. " +
            "Neu can chay cong cu, action phai la dung ten tool co trong [TOOLS]. " +
            "Schema bat buoc: {\"action\":\"<TOOL_NAME hoac CHAT>\",\"payload\":{...},\"reply\":\"...\"}. " +
            "reply luon viet tieng Viet tu nhien, day du ngu canh app, uu tien 2-4 cau thay vi qua ngan. " +
            "Neu la thao tac tao/cap nhat task, reply can neu ro ket qua (ten task, han, do uu tien neu co).";

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
