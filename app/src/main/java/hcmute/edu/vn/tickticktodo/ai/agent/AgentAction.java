package hcmute.edu.vn.tickticktodo.ai.agent;

import java.util.Locale;

public final class AgentAction {

    public static final String CREATE_TASK = "CREATE_TASK";
    public static final String COMPLETE_TASK = "COMPLETE_TASK";
    public static final String LIST_TODAY = "LIST_TODAY";
    public static final String CHAT = "CHAT";
    public static final String WIFI_ON = "WIFI_ON";
    public static final String WIFI_OFF = "WIFI_OFF";

    private AgentAction() {
    }

    public static String normalize(String rawAction) {
        if (rawAction == null || rawAction.trim().isEmpty()) {
            return CHAT;
        }
        return rawAction.trim().toUpperCase(Locale.ROOT);
    }
}
