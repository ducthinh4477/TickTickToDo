package hcmute.edu.vn.tickticktodo.core.background.assistant;

import android.app.Service;
import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.wifi.WifiManager;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hcmute.edu.vn.tickticktodo.agent.AgentAction;
import hcmute.edu.vn.tickticktodo.agent.AgentPromptContract;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseParser;
import hcmute.edu.vn.tickticktodo.agent.orchestrator.AgentOrchestrator;
import hcmute.edu.vn.tickticktodo.core.background.assistant.AssistantStateMonitor.AssistantState;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.helper.ReminderScheduler;
import hcmute.edu.vn.tickticktodo.model.Task;

public class AssistantAiController {

    public interface Host {
        boolean isServiceAlive();

        void runWorkerSafely(Runnable task);

        void appendDebugTrace(String stage, String payload);

        void updateState(AssistantState state);

        void showUserMessage(String message);

        void showAssistantMessage(String message);

        void showAssistantMessage(String message, boolean persist, boolean allowVoiceOutput);

        String buildConversationMemoryBlock();

        boolean isVoiceOnlyMode();

        void speakAssistantMessage(String message, int queueMode, boolean shouldAutoContinueListening);

        boolean isAssistantSpeaking();

        boolean isVoiceAutoTurnTakingEnabled();

        void setAutoListenAfterAssistantReply(boolean enabled);

        void scheduleAutoListenAfterSpeech();
    }

    private static final String TAG = "AssistantAiController";
    private static final String CHAT_SOURCE_FLOATING = "floating_assistant";
    private static final int MAX_DEBUG_TRACE_CHARS = 2400;

    private final Service service;
    private final GeminiManager geminiManager;
    private final AgentOrchestrator agentOrchestrator;
    private final AgentResponseParser responseParser;
    private final Host host;

    public AssistantAiController(Service service,
                                 GeminiManager geminiManager,
                                 AgentOrchestrator agentOrchestrator,
                                 AgentResponseParser responseParser,
                                 Host host) {
        this.service = service;
        this.geminiManager = geminiManager;
        this.agentOrchestrator = agentOrchestrator;
        this.responseParser = responseParser;
        this.host = host;
    }

    public void sendToGemini(String message) {
        if (geminiManager == null || agentOrchestrator == null) {
            host.appendDebugTrace("GUARD", "Gemini or orchestrator is null.");
            host.showAssistantMessage("Vui long kiem tra cau hinh API Key trong local.properties");
            return;
        }

        String userMessage = message == null ? "" : message.trim();
        if (userMessage.isEmpty()) {
            return;
        }

        host.appendDebugTrace("USER_INPUT", userMessage);
        host.showUserMessage(userMessage);

        if (tryHandleQuickCreateIntent(userMessage)) {
            host.appendDebugTrace("SHORTCUT", "Handled by quick-create intent parser (no tool call).");
            return;
        }

        final boolean[] suppressNextAssistantReply = {false};
        host.runWorkerSafely(() -> agentOrchestrator.handleUserMessage(userMessage, new AgentOrchestrator.Callback() {
            @Override
            public void onAssistantReply(String replyText) {
                if (!host.isServiceAlive()) {
                    return;
                }
                if (suppressNextAssistantReply[0]) {
                    suppressNextAssistantReply[0] = false;
                    return;
                }
                if (!TextUtils.isEmpty(replyText)) {
                    host.appendDebugTrace("ASSISTANT_REPLY", replyText);
                    host.showAssistantMessage(replyText);
                }
            }

            @Override
            public void onToolResult(hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult toolResult) {
                if (!host.isServiceAlive() || toolResult == null) {
                    return;
                }

                host.appendDebugTrace("TOOL_RESULT_CALLBACK", toolResult.toJson().toString());

                if (!toolResult.isSuccess() && "TOOL_NOT_FOUND".equals(toolResult.getErrorCode())) {
                    suppressNextAssistantReply[0] = true;
                    host.appendDebugTrace("FALLBACK", "TOOL_NOT_FOUND => switching to legacy parser flow.");
                    fallbackToLegacyAgentFlow(userMessage);
                }
            }

            @Override
            public void onDebugTrace(String stage, String payload) {
                if (!host.isServiceAlive()) {
                    return;
                }
                host.appendDebugTrace(stage, payload);
            }

            @Override
            public void onError(String errorMessage) {
                if (!host.isServiceAlive()) {
                    return;
                }
                host.appendDebugTrace("ORCHESTRATOR_ERROR", errorMessage);
                fallbackToLegacyAgentFlow(userMessage);
            }
        }));
    }

    public void fallbackToLegacyAgentFlow(String userMessage) {
        host.appendDebugTrace("FALLBACK_PATH", "Running legacy prompt + response parser.");
        host.runWorkerSafely(() -> {
            String prompt = buildAgentPrompt(userMessage);
            host.appendDebugTrace("LEGACY_PROMPT", prompt);
            host.updateState(AssistantState.THINKING);

            StringBuilder jsonAccumulator = new StringBuilder();
            StringBuilder speechAccumulator = new StringBuilder();
            final int[] lastSpokenIndex = {0};
            final boolean[] streamedAnySentence = {false};

            geminiManager.generateResponseStream(prompt, new GeminiManager.StreamResponseCallback() {
                @Override
                public void onNext(String chunk) {
                    if (!host.isServiceAlive()) {
                        return;
                    }
                    jsonAccumulator.append(chunk);

                    String currentReply = extractReplyFieldFromPartialJson(jsonAccumulator.toString());
                    if (TextUtils.isEmpty(currentReply)) {
                        return;
                    }

                    if (currentReply.length() > lastSpokenIndex[0]) {
                        String newText = currentReply.substring(lastSpokenIndex[0]);
                        speechAccumulator.append(newText);
                        lastSpokenIndex[0] = currentReply.length();
                        if (speakCompletedSentencesFromBuffer(speechAccumulator)) {
                            streamedAnySentence[0] = true;
                        }
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    if (!host.isServiceAlive()) {
                        return;
                    }
                    host.appendDebugTrace("LEGACY_MODEL_ERROR", errorMessage);
                    host.showAssistantMessage(errorMessage);
                    host.updateState(AssistantState.IDLE);
                }

                @Override
                public void onComplete() {
                    if (!host.isServiceAlive()) {
                        return;
                    }

                    if (flushRemainingSpeechBuffer(speechAccumulator)) {
                        streamedAnySentence[0] = true;
                    }

                    if (streamedAnySentence[0]) {
                        host.setAutoListenAfterAssistantReply(host.isVoiceAutoTurnTakingEnabled());
                        if (!host.isAssistantSpeaking() && host.isVoiceAutoTurnTakingEnabled()) {
                            host.scheduleAutoListenAfterSpeech();
                        }
                    }

                    boolean shouldSpeakParsedReply = !streamedAnySentence[0];
                    host.runWorkerSafely(() -> handleAIResponse(jsonAccumulator.toString(), shouldSpeakParsedReply));
                }
            });
        });
    }

    public String extractReplyFieldFromPartialJson(String jsonBuffer) {
        if (TextUtils.isEmpty(jsonBuffer)) {
            return "";
        }

        int keyIndex = jsonBuffer.indexOf("\"reply\"");
        if (keyIndex < 0) {
            return "";
        }

        int colonIndex = jsonBuffer.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            return "";
        }

        int firstQuote = jsonBuffer.indexOf('"', colonIndex + 1);
        if (firstQuote < 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = firstQuote + 1; i < jsonBuffer.length(); i++) {
            char ch = jsonBuffer.charAt(i);

            if (escaping) {
                switch (ch) {
                    case 'n':
                    case 'r':
                    case 't':
                        builder.append(' ');
                        break;
                    case '\\':
                    case '/':
                    case '"':
                        builder.append(ch);
                        break;
                    default:
                        builder.append(ch);
                        break;
                }
                escaping = false;
                continue;
            }

            if (ch == '\\') {
                escaping = true;
                continue;
            }

            if (ch == '"') {
                return builder.toString().trim();
            }

            builder.append(ch);
        }

        return builder.toString().trim();
    }

    public boolean speakCompletedSentencesFromBuffer(StringBuilder speechBuffer) {
        if (!host.isVoiceOnlyMode()) {
            return false;
        }

        if (speechBuffer == null || speechBuffer.length() == 0) {
            return false;
        }

        Matcher matcher = Pattern.compile(".*?[.?!,](?:\\s+|$)", Pattern.DOTALL)
                .matcher(speechBuffer.toString());
        int lastMatchEnd = 0;
        boolean spokeAny = false;

        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!TextUtils.isEmpty(sentence)) {
                host.speakAssistantMessage(sentence, TextToSpeech.QUEUE_ADD, false);
                spokeAny = true;
            }
            lastMatchEnd = matcher.end();
        }

        if (lastMatchEnd > 0) {
            speechBuffer.delete(0, lastMatchEnd);
        }
        return spokeAny;
    }

    public boolean flushRemainingSpeechBuffer(StringBuilder speechBuffer) {
        if (!host.isVoiceOnlyMode()) {
            if (speechBuffer != null) {
                speechBuffer.setLength(0);
            }
            return false;
        }

        if (speechBuffer == null) {
            return false;
        }
        String remaining = speechBuffer.toString().trim();
        speechBuffer.setLength(0);
        if (remaining.isEmpty()) {
            return false;
        }
        host.speakAssistantMessage(remaining, TextToSpeech.QUEUE_ADD, true);
        return true;
    }

    public void handleAIResponse(String responseText) {
        handleAIResponse(responseText, true);
    }

    public void handleAIResponse(String responseText, boolean allowAssistantReply) {
        if (responseText == null || responseText.trim().isEmpty()) {
            if (allowAssistantReply) {
                host.showAssistantMessage("AI chưa trả về nội dung. Bạn thử lại nhé.");
            }
            return;
        }

        AgentResponseEnvelope response = responseParser.parse(responseText);
        String action = response.getAction();
        JSONObject payload = response.getPayload();
        String reply = response.getReply();
        host.appendDebugTrace("LEGACY_PARSED_ENVELOPE", envelopeTraceJson(response).toString());

        switch (action) {
            case AgentAction.CREATE_TASK:
                createTaskFromPayload(payload, reply, allowAssistantReply);
                break;
            case AgentAction.COMPLETE_TASK:
                completeTaskFromPayload(payload, reply, allowAssistantReply);
                break;
            case AgentAction.LIST_TODAY:
                listTodayTasks(reply, allowAssistantReply);
                break;
            case AgentAction.WIFI_ON:
                executeWifiCommand(true, reply, allowAssistantReply);
                break;
            case AgentAction.WIFI_OFF:
                executeWifiCommand(false, reply, allowAssistantReply);
                break;
            case AgentAction.CHAT:
            default:
                String fallbackReply = TextUtils.isEmpty(reply) ? response.getRawText() : reply;
                if (allowAssistantReply) {
                    host.showAssistantMessage(fallbackReply);
                } else if (!TextUtils.isEmpty(fallbackReply)) {
                    host.showAssistantMessage(fallbackReply, true, false);
                }
                break;
        }
    }

    public void maybeShowAssistantReply(String reply, String fallbackMessage, boolean allowAssistantReply) {
        String finalMessage = TextUtils.isEmpty(reply) ? fallbackMessage : reply;
        if (TextUtils.isEmpty(finalMessage)) {
            return;
        }

        if (allowAssistantReply) {
            host.showAssistantMessage(finalMessage);
        } else {
            host.showAssistantMessage(finalMessage, true, false);
        }
    }

    public void createTaskFromPayload(JSONObject payload, String reply) {
        createTaskFromPayload(payload, reply, true);
    }

    public void createTaskFromPayload(JSONObject payload, String reply, boolean allowAssistantReply) {
        if (payload == null) {
            maybeShowAssistantReply(reply, "Mình chưa đọc được dữ liệu task để tạo.", allowAssistantReply);
            return;
        }

        String title = payload.optString("title", "").trim();
        String description = payload.optString("description", "").trim();
        long dueDate = payload.optLong("dueDate", 0L);
        int priority = clampPriority(payload.optInt("priority", 1));

        if (title.isEmpty()) {
            maybeShowAssistantReply(reply, "Bạn nói rõ tên công việc để mình tạo giúp nhé.", allowAssistantReply);
            return;
        }

        try {
            Task task = new Task();
            task.setTitle(title);
            task.setDescription(description);
            task.setPriority(priority);
            task.setSource("AI_AGENT");
            if (dueDate > 0L) {
                task.setDueDate(dueDate);
            }
            long newId = TaskDatabase.getInstance(service).taskDao().insert(task);
            task.setId(newId);
            ReminderScheduler.scheduleReminder(service, task);

            maybeShowAssistantReply(reply,
                    "Đã tạo công việc \"" + title + "\" thành công.",
                    allowAssistantReply);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create task", e);
            host.showAssistantMessage("Không thể tạo công việc lúc này. Bạn thử lại nhé.");
        }
    }

    public void completeTaskFromPayload(JSONObject payload, String reply) {
        completeTaskFromPayload(payload, reply, true);
    }

    public void completeTaskFromPayload(JSONObject payload, String reply, boolean allowAssistantReply) {
        if (payload == null) {
            maybeShowAssistantReply(reply,
                    "Mình chưa nhận được thông tin task cần hoàn thành.",
                    allowAssistantReply);
            return;
        }

        long taskId = payload.optLong("id", -1L);
        String title = payload.optString("title", "").trim();

        try {
            List<Task> allTasks = TaskDatabase.getInstance(service).taskDao().getAllTasksSync();
            Task target = null;

            if (taskId > 0L) {
                for (Task task : allTasks) {
                    if (task.getId() == taskId) {
                        target = task;
                        break;
                    }
                }
            }

            if (target == null && !title.isEmpty()) {
                String normalized = title.toLowerCase(Locale.ROOT);
                for (Task task : allTasks) {
                    String taskTitle = task.getTitle() == null
                            ? ""
                            : task.getTitle().toLowerCase(Locale.ROOT);
                    if (taskTitle.contains(normalized) || normalized.contains(taskTitle)) {
                        target = task;
                        break;
                    }
                }
            }

            if (target == null) {
                maybeShowAssistantReply(reply,
                        "Mình chưa tìm thấy task phù hợp để hoàn thành.",
                        allowAssistantReply);
                return;
            }

            TaskDatabase.getInstance(service).taskDao()
                    .markTaskAsCompletedWithDate(target.getId(), true, System.currentTimeMillis());

            maybeShowAssistantReply(reply,
                    "Đã hoàn thành task: \"" + target.getTitle() + "\".",
                    allowAssistantReply);
        } catch (Exception e) {
            Log.e(TAG, "Failed to complete task", e);
            host.showAssistantMessage("Không thể cập nhật task lúc này. Bạn thử lại nhé.");
        }
    }

    public void listTodayTasks(String reply) {
        listTodayTasks(reply, true);
    }

    public void listTodayTasks(String reply, boolean allowAssistantReply) {
        try {
            long start = startOfDayMillis();
            long end = endOfDayMillis();
            List<Task> allTasks = TaskDatabase.getInstance(service).taskDao().getAllTasksSync();

            StringBuilder builder = new StringBuilder();
            int count = 0;
            for (Task task : allTasks) {
                Long dueDate = task.getDueDate();
                if (dueDate == null || dueDate < start || dueDate >= end) {
                    continue;
                }
                count++;
                builder.append(count)
                        .append(". ")
                        .append(task.getTitle())
                        .append(task.isCompleted() ? " [Hoàn thành]" : "")
                        .append("\n");
                if (count >= 8) {
                    break;
                }
            }

            String fallback = count == 0
                    ? "Hôm nay bạn chưa có task nào."
                    : "Danh sách hôm nay:\n" + builder.toString().trim();
            maybeShowAssistantReply(reply, fallback, allowAssistantReply);
        } catch (Exception e) {
            Log.e(TAG, "Failed to list today tasks", e);
            host.showAssistantMessage("Mình chưa lấy được danh sách task hôm nay. Bạn thử lại nhé.");
        }
    }

    public void executeWifiCommand(boolean enable) {
        executeWifiCommand(enable, null, true);
    }

    public void executeWifiCommand(boolean enable, String reply, boolean allowAssistantReply) {
        WifiManager wifiManager = (WifiManager) service.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            maybeShowAssistantReply(reply,
                    "Mình chưa truy cập được Wifi Manager trên thiết bị này.",
                    allowAssistantReply);
            return;
        }

        try {
            boolean result = wifiManager.setWifiEnabled(enable);
            String state = enable ? "Bật" : "Tắt";
            maybeShowAssistantReply(reply, "Đã " + state + " Wifi: " + result, allowAssistantReply);
        } catch (Exception e) {
            Log.e(TAG, "Wifi toggle failed", e);
            host.showAssistantMessage("Từ Android 10+, không thể bật/tắt Wifi trực tiếp từ app thường.");
        }
    }

    public String buildAgentPrompt(String userMessage) {
        return AgentPromptContract.buildPrompt(
                AgentPromptContract.FLOATING_ASSISTANT_PROMPT,
                buildRuntimeContext(),
                host.buildConversationMemoryBlock(),
                userMessage
        );
    }

    public String buildRuntimeContext() {
        StringBuilder context = new StringBuilder();
        long now = System.currentTimeMillis();
        context.append("nowMillis=").append(now).append("\n");
        context.append("timezone=").append(TimeZone.getDefault().getID()).append("\n");

        try {
            long start = startOfDayMillis();
            long end = endOfDayMillis();
            List<Task> todayIncomplete = TaskDatabase.getInstance(service)
                    .taskDao()
                    .getIncompleteTasksForDaySync(start, end);

            context.append("todayIncompleteCount=").append(todayIncomplete.size()).append("\n");
            int limit = Math.min(4, todayIncomplete.size());
            for (int i = 0; i < limit; i++) {
                Task task = todayIncomplete.get(i);
                context.append("task[")
                        .append(i + 1)
                        .append("]=")
                        .append(task.getTitle())
                        .append(" | priority=")
                        .append(task.getPriority())
                        .append("\n");
            }
        } catch (Exception e) {
            context.append("todayIncompleteCount=unknown\n");
        }

        return context.toString().trim();
    }

    public boolean tryHandleQuickCreateIntent(String userText) {
        String lower = userText.toLowerCase(Locale.ROOT).trim();
        String normalized = lower.replaceAll("\\s+", " ");
        boolean hasCreateVerb = normalized.startsWith("nhac toi")
                || normalized.startsWith("tao task")
                || normalized.startsWith("them task")
                || normalized.startsWith("tao viec")
                || normalized.startsWith("them viec")
                || normalized.startsWith("tao cong viec")
                || normalized.startsWith("them cong viec")
                || normalized.startsWith("create task")
                || normalized.startsWith("add task");
        boolean hasTaskNoun = normalized.contains("task")
                || normalized.contains("viec")
                || normalized.contains("cong viec");
        boolean likelyCreate = hasCreateVerb
                || (hasTaskNoun
                && (normalized.contains("tao")
                || normalized.contains("them")
                || normalized.contains("nhac")));

        if (!likelyCreate) {
            return false;
        }

        String title = userText
                .replaceFirst("(?i)^(nhắc tôi|nhac toi|tạo task|tao task|thêm task|them task|tạo việc|tao viec|thêm việc|them viec|tạo công việc|tao cong viec|thêm công việc|them cong viec|create task|add task)\\s*", "")
                .replaceFirst("(?i)^(cho tôi|cho toi|giúp tôi|giup toi)\\s*", "")
                .trim();

        Long dueDate = parseNaturalDueDate(lower);
        title = title.replace("chiều nay", "")
                .replace("chieu nay", "")
                .replace("trưa nay", "")
                .replace("trua nay", "")
                .replace("sáng mai", "")
                .replace("sang mai", "")
                .replace("chiều mai", "")
                .replace("chieu mai", "")
                .replace("ngày mai", "")
                .replace("ngay mai", "")
                .replace("tối nay", "")
                .replace("toi nay", "")
                .replace("hôm nay", "")
                .replace("hom nay", "")
                .replace("mai", "")
                .trim();

        if (title.isEmpty()) {
            return false;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("title", title);
            payload.put("description", "");
            payload.put("priority", 1);
            if (dueDate != null) {
                payload.put("dueDate", dueDate);
            }
        } catch (JSONException ignored) {
        }

        final JSONObject quickPayload = payload;
        final String quickTitle = title;
        host.runWorkerSafely(() -> createTaskFromPayload(quickPayload, "Mình đã tạo nhanh công việc \"" + quickTitle + "\" cho bạn."));
        return true;
    }

    public Long parseNaturalDueDate(String lowerText) {
        Calendar c = Calendar.getInstance();

        if (lowerText.contains("chiều mai") || lowerText.contains("chieu mai")) {
            c.add(Calendar.DATE, 1);
            c.set(Calendar.HOUR_OF_DAY, 17);
        } else if (lowerText.contains("ngày mai") || lowerText.contains("ngay mai")) {
            c.add(Calendar.DATE, 1);
            c.set(Calendar.HOUR_OF_DAY, 9);
        } else if (lowerText.contains("sáng mai") || lowerText.contains("sang mai")) {
            c.add(Calendar.DATE, 1);
            c.set(Calendar.HOUR_OF_DAY, 8);
        } else if (lowerText.contains("mai")) {
            c.add(Calendar.DATE, 1);
            c.set(Calendar.HOUR_OF_DAY, 9);
        } else if (lowerText.contains("trưa nay") || lowerText.contains("trua nay")) {
            c.set(Calendar.HOUR_OF_DAY, 12);
        } else if (lowerText.contains("chiều nay") || lowerText.contains("chieu nay")) {
            c.set(Calendar.HOUR_OF_DAY, 17);
        } else if (lowerText.contains("tối nay") || lowerText.contains("toi nay")) {
            c.set(Calendar.HOUR_OF_DAY, 20);
        } else if (lowerText.contains("hôm nay") || lowerText.contains("hom nay")) {
            c.set(Calendar.HOUR_OF_DAY, Math.max(c.get(Calendar.HOUR_OF_DAY) + 1, 9));
        } else {
            return null;
        }

        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public int clampPriority(int priority) {
        return Math.max(0, Math.min(priority, 3));
    }

    public long startOfDayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public long endOfDayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    public JSONObject envelopeTraceJson(AgentResponseEnvelope envelope) {
        JSONObject json = new JSONObject();
        safePut(json, "action", envelope == null ? "" : envelope.getAction());
        safePut(json, "payload", envelope == null ? new JSONObject() : envelope.getPayload());
        safePut(json, "reply", envelope == null ? "" : envelope.getReply());
        safePut(json, "structured", envelope != null && envelope.isStructured());
        safePut(json, "rawText", envelope == null ? "" : envelope.getRawText());
        return json;
    }

    public void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
