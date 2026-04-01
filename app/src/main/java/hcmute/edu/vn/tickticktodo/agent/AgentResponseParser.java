package hcmute.edu.vn.tickticktodo.agent;

import org.json.JSONException;
import org.json.JSONObject;

public class AgentResponseParser {

    public AgentResponseEnvelope parse(String rawResponseText) {
        if (rawResponseText == null) {
            return AgentResponseEnvelope.plainText("");
        }

        String cleanText = stripMarkdownFence(rawResponseText.trim());
        if (cleanText.isEmpty()) {
            return AgentResponseEnvelope.plainText("");
        }

        try {
            JSONObject root = new JSONObject(cleanText);

            if (!root.has("action") && root.has("title")) {
                return new AgentResponseEnvelope(
                        AgentAction.CREATE_TASK,
                        root,
                        root.optString("reply", ""),
                        cleanText,
                        true
                );
            }

            String action = AgentAction.normalize(root.optString("action", AgentAction.CHAT));
            JSONObject payload = root.optJSONObject("payload");
            String reply = root.optString("reply", "");

            return new AgentResponseEnvelope(action, payload, reply, cleanText, true);
        } catch (JSONException ignored) {
            return AgentResponseEnvelope.plainText(cleanText);
        }
    }

    private String stripMarkdownFence(String rawText) {
        if (rawText.startsWith("```") && rawText.endsWith("```")) {
            int firstNewLine = rawText.indexOf('\n');
            int lastBackticks = rawText.lastIndexOf("```");
            if (firstNewLine != -1 && lastBackticks > firstNewLine) {
                return rawText.substring(firstNewLine + 1, lastBackticks).trim();
            }
        }
        return rawText;
    }
}