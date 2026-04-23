package hcmute.edu.vn.tickticktodo.agent;

@Deprecated
public final class AgentAction {

    public static final String CREATE_TASK = hcmute.edu.vn.tickticktodo.ai.agent.AgentAction.CREATE_TASK;
    public static final String COMPLETE_TASK = hcmute.edu.vn.tickticktodo.ai.agent.AgentAction.COMPLETE_TASK;
    public static final String LIST_TODAY = hcmute.edu.vn.tickticktodo.ai.agent.AgentAction.LIST_TODAY;
    public static final String CHAT = hcmute.edu.vn.tickticktodo.ai.agent.AgentAction.CHAT;
    public static final String WIFI_ON = hcmute.edu.vn.tickticktodo.ai.agent.AgentAction.WIFI_ON;
    public static final String WIFI_OFF = hcmute.edu.vn.tickticktodo.ai.agent.AgentAction.WIFI_OFF;

    private AgentAction() {
    }

    public static String normalize(String rawAction) {
        return hcmute.edu.vn.tickticktodo.ai.agent.AgentAction.normalize(rawAction);
    }
}