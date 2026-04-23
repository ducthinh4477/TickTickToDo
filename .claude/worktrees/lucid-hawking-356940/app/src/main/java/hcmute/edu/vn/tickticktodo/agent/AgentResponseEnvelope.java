package hcmute.edu.vn.doinbot.agent;

import org.json.JSONObject;

public class AgentResponseEnvelope {

    private final String action;
    private final JSONObject payload;
    private final String reply;
    private final String rawText;
    private final boolean structured;

    public AgentResponseEnvelope(String action,
                                 JSONObject payload,
                                 String reply,
                                 String rawText,
                                 boolean structured) {
        this.action = action;
        this.payload = payload;
        this.reply = reply;
        this.rawText = rawText;
        this.structured = structured;
    }

    public static AgentResponseEnvelope plainText(String text) {
        return new AgentResponseEnvelope(AgentAction.CHAT, null, "", text == null ? "" : text, false);
    }

    public String getAction() {
        return action;
    }

    public JSONObject getPayload() {
        return payload;
    }

    public String getReply() {
        return reply;
    }

    public String getRawText() {
        return rawText;
    }

    public boolean isStructured() {
        return structured;
    }
}