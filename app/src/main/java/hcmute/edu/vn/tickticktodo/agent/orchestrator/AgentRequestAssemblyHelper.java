package hcmute.edu.vn.tickticktodo.agent.orchestrator;

import hcmute.edu.vn.tickticktodo.agent.AgentContextAssembler;

class AgentRequestAssemblyHelper {

    private final AgentContextAssembler contextAssembler;
    private final ToolExecutionBridge toolExecutionBridge;
    private final PromptTemplateManager promptTemplateManager;

    AgentRequestAssemblyHelper(AgentContextAssembler contextAssembler,
                               ToolExecutionBridge toolExecutionBridge,
                               PromptTemplateManager promptTemplateManager) {
        this.contextAssembler = contextAssembler;
        this.toolExecutionBridge = toolExecutionBridge;
        this.promptTemplateManager = promptTemplateManager;
    }

    PreparedRequest assemble(String userMessage) {
        String contextBlock = contextAssembler.buildTieredContextBlock(userMessage);
        String toolSchemas = toolExecutionBridge.getToolSchemas().toString();
        String prompt = promptTemplateManager.buildPrompt(userMessage, contextBlock, toolSchemas);
        return new PreparedRequest(contextBlock, toolSchemas, prompt);
    }

    static class PreparedRequest {
        private final String contextBlock;
        private final String toolSchemas;
        private final String prompt;

        PreparedRequest(String contextBlock, String toolSchemas, String prompt) {
            this.contextBlock = contextBlock;
            this.toolSchemas = toolSchemas;
            this.prompt = prompt;
        }

        String getContextBlock() {
            return contextBlock;
        }

        String getToolSchemas() {
            return toolSchemas;
        }

        String getPrompt() {
            return prompt;
        }
    }
}