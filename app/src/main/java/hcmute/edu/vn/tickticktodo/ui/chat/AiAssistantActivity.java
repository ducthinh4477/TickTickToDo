package hcmute.edu.vn.tickticktodo.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.agent.AgentAction;
import hcmute.edu.vn.tickticktodo.agent.AgentPromptContract;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseParser;
import hcmute.edu.vn.tickticktodo.data.dao.ChatHistoryDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.databinding.ActivityAiAssistantBinding;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.model.ChatHistoryMessage;
import hcmute.edu.vn.tickticktodo.model.ChatMessage;
import hcmute.edu.vn.tickticktodo.model.ChatSession;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.core.background.FloatingAssistantService;

public class AiAssistantActivity extends BaseActivity {

    public static final String EXTRA_SESSION_ID = "extra_chat_session_id";
    public static final String EXTRA_CREATE_NEW_SESSION = "extra_chat_create_new_session";

    private static final String PREFS_NAME = "TickTickPrefs";
    private static final String KEY_FLOATING_ASSISTANT_ENABLED = "floating_assistant_enabled";
    private static final String CHAT_SOURCE_MAIN = "ai_assistant";
    private static final int MAX_MEMORY_LINES = 10;

    private ActivityAiAssistantBinding binding;
    private ChatAdapter chatAdapter;
    private GeminiManager geminiManager;
    private ExecutorService dbExecutor;
    private Handler mainHandler;
    private final ArrayDeque<String> conversationMemory = new ArrayDeque<>();
    private final AgentResponseParser responseParser = new AgentResponseParser();

    private SharedPreferences sharedPrefs;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private ActivityResultLauncher<Intent> historyLauncher;

    private volatile long currentSessionId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiAssistantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleOverlayPermissionResult());

        historyLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }

                    Intent data = result.getData();
                    if (data.getBooleanExtra(EXTRA_CREATE_NEW_SESSION, false)) {
                        createFreshSession();
                        return;
                    }

                    long selectedSessionId = data.getLongExtra(EXTRA_SESSION_ID, -1L);
                    if (selectedSessionId > 0L) {
                        switchToSession(selectedSessionId);
                    }
                });

        dbExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        setupRecyclerView();
        setupQuickPrompts();
        setupGemini();
        ensureFloatingServiceState();

        if (binding.btnNavigateBack != null) {
            binding.btnNavigateBack.setOnClickListener(v -> finish());
        }

        if (binding.btnHistory != null) {
            binding.btnHistory.setOnClickListener(v -> openChatHistory());
        }

        if (binding.btnSettings != null) {
            binding.btnSettings.setOnClickListener(v -> showSettingsDialog());
        }

        binding.btnSend.setOnClickListener(v -> {
            String message = binding.messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
            }
        });

        initializeSessionFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        initializeSessionFromIntent(intent);
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.chatRecyclerView.setLayoutManager(layoutManager);
        binding.chatRecyclerView.setAdapter(chatAdapter);
    }

    private void setupQuickPrompts() {
        if (binding.cardQuickTodayPlan != null) {
            binding.cardQuickTodayPlan.setOnClickListener(v -> sendMessage("Lên kế hoạch công việc hôm nay theo mức ưu tiên giúp tôi."));
        }
        if (binding.cardQuickPrioritize != null) {
            binding.cardQuickPrioritize.setOnClickListener(v -> sendMessage("Hãy giúp tôi phân loại việc cần làm theo mức độ khẩn cấp và quan trọng."));
        }
        if (binding.cardQuickHabit != null) {
            binding.cardQuickHabit.setOnClickListener(v -> sendMessage("Gợi ý cho tôi 3 thói quen nhỏ để duy trì năng suất trong ngày."));
        }
        if (binding.cardQuickReview != null) {
            binding.cardQuickReview.setOnClickListener(v -> sendMessage("Tổng kết ngày hôm nay và đề xuất việc cần chuẩn bị cho ngày mai."));
        }
    }

    private void setupGemini() {
        geminiManager = GeminiManager.getInstance();

        if (!geminiManager.hasConfiguredApiKey()) {
            showAssistantMessage("Vui lòng kiểm tra cấu hình API Key trong local.properties");
        }
    }

    private void initializeSessionFromIntent(Intent intent) {
        final boolean shouldCreateNew = intent != null && intent.getBooleanExtra(EXTRA_CREATE_NEW_SESSION, false);
        final long requestedSessionId = intent != null ? intent.getLongExtra(EXTRA_SESSION_ID, -1L) : -1L;

        if (intent != null) {
            intent.removeExtra(EXTRA_CREATE_NEW_SESSION);
            intent.removeExtra(EXTRA_SESSION_ID);
        }

        dbExecutor.execute(() -> {
            ChatHistoryDao dao = TaskDatabase.getInstance(this).chatHistoryDao();
            long resolvedSessionId;

            if (shouldCreateNew) {
                resolvedSessionId = createNewSessionSync(dao, CHAT_SOURCE_MAIN, "");
            } else if (requestedSessionId > 0L && dao.getSessionByIdSync(requestedSessionId) != null) {
                resolvedSessionId = requestedSessionId;
            } else {
                ChatSession latest = dao.getLatestSessionSync();
                resolvedSessionId = latest != null
                        ? latest.id
                        : createNewSessionSync(dao, CHAT_SOURCE_MAIN, "");
            }

            currentSessionId = resolvedSessionId;
            List<ChatHistoryMessage> historyRows = dao.getMessagesForSessionSync(resolvedSessionId);

            mainHandler.post(() -> {
                applyHistoryToUi(historyRows);
                if (shouldCreateNew) {
                    Toast.makeText(this, "Đã tạo phiên trò chuyện mới.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void switchToSession(long sessionId) {
        dbExecutor.execute(() -> {
            ChatHistoryDao dao = TaskDatabase.getInstance(this).chatHistoryDao();
            if (dao.getSessionByIdSync(sessionId) == null) {
                return;
            }

            currentSessionId = sessionId;
            List<ChatHistoryMessage> rows = dao.getMessagesForSessionSync(sessionId);
            mainHandler.post(() -> applyHistoryToUi(rows));
        });
    }

    private void createFreshSession() {
        dbExecutor.execute(() -> {
            ChatHistoryDao dao = TaskDatabase.getInstance(this).chatHistoryDao();
            long newSessionId = createNewSessionSync(dao, CHAT_SOURCE_MAIN, "");
            currentSessionId = newSessionId;

            mainHandler.post(() -> {
                conversationMemory.clear();
                chatAdapter.clearMessages();
                updateQuickPromptVisibility();
                Toast.makeText(this, "Đã tạo phiên trò chuyện mới.", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void applyHistoryToUi(List<ChatHistoryMessage> rows) {
        List<ChatMessage> uiMessages = new ArrayList<>();
        conversationMemory.clear();

        if (rows != null) {
            for (ChatHistoryMessage row : rows) {
                boolean isUser = "user".equalsIgnoreCase(row.role);
                String text = row.content == null ? "" : row.content;
                uiMessages.add(new ChatMessage(text, isUser));
                rememberTurn(isUser ? "user" : "assistant", text);
            }
        }

        chatAdapter.setMessages(uiMessages);
        if (!uiMessages.isEmpty()) {
            binding.chatRecyclerView.scrollToPosition(uiMessages.size() - 1);
        }
        updateQuickPromptVisibility();
    }

    private void updateQuickPromptVisibility() {
        if (binding.quickPromptContainer != null) {
            binding.quickPromptContainer.setVisibility(chatAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void openChatHistory() {
        Intent intent = new Intent(this, ChatHistoryActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, currentSessionId);
        historyLauncher.launch(intent);
    }

    private void showSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_ai_settings_dialog, null);
        dialog.setContentView(view);

        SwitchMaterial switchFloating = view.findViewById(R.id.switchFloatingAi);
        if (switchFloating == null) {
            dialog.dismiss();
            Toast.makeText(this, "Không thể mở cài đặt Trợ lý nổi.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isEnabled = sharedPrefs.getBoolean(KEY_FLOATING_ASSISTANT_ENABLED, false);
        if (isEnabled && !hasOverlayPermission()) {
            isEnabled = false;
            setFloatingPreference(false);
        }
        switchFloating.setChecked(isEnabled);

        switchFloating.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!hasOverlayPermission()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    overlayPermissionLauncher.launch(intent);
                } else {
                    setFloatingPreference(true);
                    startFloatingService(FloatingAssistantService.ACTION_HIDE_BUBBLE);
                }
            } else {
                setFloatingPreference(false);
                stopFloatingService();
            }
        });

        dialog.show();
    }

    private void sendMessage(String userText) {
        if (geminiManager == null || !geminiManager.hasConfiguredApiKey()) {
            showAssistantMessage("Vui lòng kiểm tra cấu hình API Key trong local.properties");
            return;
        }

        binding.messageInput.setText("");
        showUserMessage(userText);
        binding.chatRecyclerView.smoothScrollToPosition(Math.max(0, chatAdapter.getItemCount() - 1));

        if (tryHandleQuickCreateIntent(userText)) {
            return;
        }

        dbExecutor.execute(() -> {
            String promptWithContext = buildAgentPrompt(userText);
            geminiManager.generateResponse(promptWithContext, new GeminiManager.ResponseCallback() {
                @Override
                public void onSuccess(String responseText) {
                    handleAiResponse(responseText);
                }

                @Override
                public void onError(String errorMessage) {
                    showAssistantMessage(errorMessage);
                }
            });
        });
    }

    private void handleAiResponse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        AgentResponseEnvelope response = responseParser.parse(text);
        String action = response.getAction();
        JSONObject payload = response.getPayload();
        String reply = response.getReply();

        switch (action) {
            case AgentAction.CREATE_TASK:
                createTaskFromPayload(payload, reply);
                break;
            case AgentAction.COMPLETE_TASK:
                completeTaskFromPayload(payload, reply);
                break;
            case AgentAction.LIST_TODAY:
                listTodayTasks(reply);
                break;
            case AgentAction.CHAT:
            default:
                if (TextUtils.isEmpty(reply)) {
                    showAssistantMessage(response.getRawText());
                } else {
                    showAssistantMessage(reply);
                }
                break;
        }
    }

    private void createTaskFromPayload(JSONObject payload, String reply) {
        if (payload == null) {
            showAssistantMessage(TextUtils.isEmpty(reply) ? "Mình chưa đọc được dữ liệu task để tạo." : reply);
            return;
        }

        String title = payload.optString("title", "").trim();
        String description = payload.optString("description", "").trim();
        long dueDate = payload.optLong("dueDate", 0L);
        int priority = clampPriority(payload.optInt("priority", 1));

        if (title.isEmpty()) {
            showAssistantMessage(TextUtils.isEmpty(reply)
                    ? "Bạn vui lòng nói rõ tên công việc để mình tạo giúp nhé."
                    : reply);
            return;
        }

        Task targetTask = new Task();
        targetTask.setTitle(title);
        targetTask.setDescription(description);
        targetTask.setPriority(priority);
        if (dueDate > 0) {
            targetTask.setDueDate(dueDate);
        }

        dbExecutor.execute(() -> {
            try {
                TaskDatabase.getInstance(AiAssistantActivity.this).taskDao().insert(targetTask);
                String success = TextUtils.isEmpty(reply)
                        ? "Đã tạo công việc \"" + title + "\" thành công."
                        : reply;
                showAssistantMessage(success);
            } catch (Exception e) {
                showAssistantMessage("Đã có lỗi xảy ra khi lưu công việc vào database.");
            }
        });
    }

    private void completeTaskFromPayload(JSONObject payload, String reply) {
        if (payload == null) {
            showAssistantMessage(TextUtils.isEmpty(reply)
                    ? "Mình chưa nhận được thông tin task cần hoàn thành."
                    : reply);
            return;
        }

        final long taskId = payload.optLong("id", -1L);
        final String title = payload.optString("title", "").trim();

        dbExecutor.execute(() -> {
            try {
                List<Task> allTasks = TaskDatabase.getInstance(AiAssistantActivity.this).taskDao().getAllTasksSync();
                Task target = null;

                for (Task task : allTasks) {
                    if (taskId > 0 && task.getId() == taskId) {
                        target = task;
                        break;
                    }
                }

                if (target == null && !title.isEmpty()) {
                    String lowerTitle = title.toLowerCase(Locale.ROOT);
                    for (Task task : allTasks) {
                        String taskTitle = task.getTitle() == null ? "" : task.getTitle().toLowerCase(Locale.ROOT);
                        if (taskTitle.contains(lowerTitle) || lowerTitle.contains(taskTitle)) {
                            target = task;
                            break;
                        }
                    }
                }

                if (target == null) {
                    showAssistantMessage(TextUtils.isEmpty(reply)
                            ? "Mình chưa tìm thấy công việc để đánh dấu hoàn thành."
                            : reply);
                    return;
                }

                TaskDatabase.getInstance(AiAssistantActivity.this).taskDao()
                        .markTaskAsCompletedWithDate(target.getId(), true, System.currentTimeMillis());

                String doneMessage = TextUtils.isEmpty(reply)
                        ? "Đã đánh dấu hoàn thành: \"" + target.getTitle() + "\"."
                        : reply;
                showAssistantMessage(doneMessage);
            } catch (Exception e) {
                showAssistantMessage("Không thể cập nhật trạng thái công việc lúc này.");
            }
        });
    }

    private void listTodayTasks(String reply) {
        dbExecutor.execute(() -> {
            try {
                long start = startOfDayMillis();
                long end = endOfDayMillis();
                List<Task> allTasks = TaskDatabase.getInstance(AiAssistantActivity.this).taskDao().getAllTasksSync();
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

                if (!TextUtils.isEmpty(reply)) {
                    showAssistantMessage(reply);
                } else if (count == 0) {
                    showAssistantMessage("Hôm nay bạn chưa có công việc nào.");
                } else {
                    showAssistantMessage("Danh sách hôm nay:\n" + builder.toString().trim());
                }
            } catch (Exception e) {
                showAssistantMessage("Không thể lấy danh sách công việc hôm nay lúc này.");
            }
        });
    }

    private String buildAgentPrompt(String userText) {
        return AgentPromptContract.buildPrompt(
                AgentPromptContract.STANDARD_ASSISTANT_PROMPT,
                buildRuntimeContext(),
                buildConversationMemoryBlock(),
                userText
        );
    }

    private String buildRuntimeContext() {
        long now = System.currentTimeMillis();
        long start = startOfDayMillis();
        long end = endOfDayMillis();

        StringBuilder context = new StringBuilder();
        context.append("nowMillis=").append(now).append("\n");
        context.append("timezone=").append(TimeZone.getDefault().getID()).append("\n");

        try {
            List<Task> todayIncomplete = TaskDatabase.getInstance(this)
                    .taskDao()
                    .getIncompleteTasksForDaySync(start, end);

            context.append("todayIncompleteCount=").append(todayIncomplete.size()).append("\n");
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault());

            int limit = Math.min(5, todayIncomplete.size());
            for (int i = 0; i < limit; i++) {
                Task task = todayIncomplete.get(i);
                String dueText = task.getDueDate() == null
                        ? "không hạn"
                        : sdf.format(new Date(task.getDueDate()));
                context.append("task[")
                        .append(i + 1)
                        .append("]=")
                        .append(task.getTitle())
                        .append(" | priority=")
                        .append(task.getPriority())
                        .append(" | due=")
                        .append(dueText)
                        .append("\n");
            }
        } catch (Exception e) {
            context.append("todayIncompleteCount=unknown\n");
        }

        return context.toString().trim();
    }

    private String buildConversationMemoryBlock() {
        if (conversationMemory.isEmpty()) {
            return "(empty)";
        }
        StringBuilder memory = new StringBuilder();
        for (String line : conversationMemory) {
            memory.append(line).append("\n");
        }
        return memory.toString().trim();
    }

    private void rememberTurn(String role, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        String clean = text.replace("\n", " ").trim();
        if (clean.length() > 160) {
            clean = clean.substring(0, 160) + "...";
        }
        conversationMemory.addLast(role + ": " + clean);
        while (conversationMemory.size() > MAX_MEMORY_LINES) {
            conversationMemory.removeFirst();
        }
    }

    private void showUserMessage(String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        chatAdapter.addMessage(new ChatMessage(text, true));
        rememberTurn("user", text);
        persistMessageAsync("user", text, CHAT_SOURCE_MAIN);
        updateQuickPromptVisibility();
    }

    private void showAssistantMessage(String text) {
        mainHandler.post(() -> {
            if (TextUtils.isEmpty(text)) {
                return;
            }
            chatAdapter.addMessage(new ChatMessage(text, false));
            rememberTurn("assistant", text);
            persistMessageAsync("assistant", text, CHAT_SOURCE_MAIN);
            binding.chatRecyclerView.smoothScrollToPosition(Math.max(0, chatAdapter.getItemCount() - 1));
            updateQuickPromptVisibility();
        });
    }

    private void persistMessageAsync(String role, String text, String source) {
        if (TextUtils.isEmpty(text)) {
            return;
        }

        dbExecutor.execute(() -> {
            try {
                ChatHistoryDao dao = TaskDatabase.getInstance(this).chatHistoryDao();
                long sessionId = ensureSessionSync(dao, source);
                long now = System.currentTimeMillis();

                ChatHistoryMessage row = new ChatHistoryMessage();
                row.sessionId = sessionId;
                row.role = role;
                row.content = text;
                row.source = source;
                row.createdAt = now;
                dao.insertMessage(row);

                String preview = abbreviateForPreview(text);
                dao.updateSessionAfterMessage(sessionId, now, preview);
                if ("user".equals(role)) {
                    dao.updateSessionTitleIfEmpty(sessionId, preview);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private long ensureSessionSync(ChatHistoryDao dao, String source) {
        if (currentSessionId > 0L && dao.getSessionByIdSync(currentSessionId) != null) {
            return currentSessionId;
        }

        ChatSession latest = dao.getLatestSessionSync();
        if (latest != null) {
            currentSessionId = latest.id;
            return currentSessionId;
        }

        currentSessionId = createNewSessionSync(dao, source, "");
        return currentSessionId;
    }

    private long createNewSessionSync(ChatHistoryDao dao, String source, String title) {
        long now = System.currentTimeMillis();
        ChatSession session = new ChatSession();
        session.title = title;
        session.source = source;
        session.lastMessage = "";
        session.createdAt = now;
        session.updatedAt = now;
        return dao.insertSession(session);
    }

    private String abbreviateForPreview(String text) {
        String value = text == null ? "" : text.trim().replace('\n', ' ');
        if (value.length() <= 64) {
            return value;
        }
        return value.substring(0, 64) + "...";
    }

    private int clampPriority(int priority) {
        return Math.max(0, Math.min(priority, 3));
    }

    private long startOfDayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long endOfDayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private boolean tryHandleQuickCreateIntent(String userText) {
        String lower = userText.toLowerCase(Locale.ROOT).trim();
        boolean likelyCreate = lower.startsWith("nhắc tôi")
                || lower.startsWith("nhac toi")
                || lower.startsWith("tạo task")
                || lower.startsWith("tao task")
                || lower.startsWith("thêm task")
                || lower.startsWith("them task");

        if (!likelyCreate) {
            return false;
        }

        String title = userText
                .replaceFirst("(?i)^nhắc tôi", "")
                .replaceFirst("(?i)^nhac toi", "")
                .replaceFirst("(?i)^tạo task", "")
                .replaceFirst("(?i)^tao task", "")
                .replaceFirst("(?i)^thêm task", "")
                .replaceFirst("(?i)^them task", "")
                .trim();

        Long dueDate = parseNaturalDueDate(lower);
        title = title.replace("chiều nay", "")
                .replace("chieu nay", "")
                .replace("sáng mai", "")
                .replace("sang mai", "")
                .replace("chiều mai", "")
                .replace("chieu mai", "")
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

        createTaskFromPayload(payload, "Mình đã tạo nhanh công việc \"" + title + "\" cho bạn.");
        return true;
    }

    private Long parseNaturalDueDate(String lowerText) {
        Calendar c = Calendar.getInstance();

        if (lowerText.contains("chiều mai") || lowerText.contains("chieu mai")) {
            c.add(Calendar.DATE, 1);
            c.set(Calendar.HOUR_OF_DAY, 17);
        } else if (lowerText.contains("sáng mai") || lowerText.contains("sang mai")) {
            c.add(Calendar.DATE, 1);
            c.set(Calendar.HOUR_OF_DAY, 8);
        } else if (lowerText.contains("mai")) {
            c.add(Calendar.DATE, 1);
            c.set(Calendar.HOUR_OF_DAY, 9);
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

    private boolean hasOverlayPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void handleOverlayPermissionResult() {
        if (hasOverlayPermission()) {
            setFloatingPreference(true);
            startFloatingService(FloatingAssistantService.ACTION_HIDE_BUBBLE);
            Toast.makeText(this, "Đã bật Trợ lý nổi.", Toast.LENGTH_SHORT).show();
        } else {
            setFloatingPreference(false);
            Toast.makeText(this, "Bạn chưa cấp quyền hiển thị nổi.", Toast.LENGTH_SHORT).show();
        }
    }

    private void ensureFloatingServiceState() {
        boolean enabled = sharedPrefs.getBoolean(KEY_FLOATING_ASSISTANT_ENABLED, false);
        if (enabled && hasOverlayPermission()) {
            startFloatingService(FloatingAssistantService.ACTION_HIDE_BUBBLE);
        } else {
            if (enabled && !hasOverlayPermission()) {
                setFloatingPreference(false);
            }
            stopFloatingService();
        }
    }

    private void setFloatingPreference(boolean enabled) {
        sharedPrefs.edit().putBoolean(KEY_FLOATING_ASSISTANT_ENABLED, enabled).apply();
    }

    private void startFloatingService(String action) {
        Intent intent = new Intent(this, FloatingAssistantService.class);
        intent.setAction(action);
        try {
            ContextCompat.startForegroundService(this, intent);
        } catch (Exception firstException) {
            try {
                startService(intent);
            } catch (Exception secondException) {
                setFloatingPreference(false);
                Toast.makeText(this, "Không thể khởi động Trợ lý nổi trên thiết bị này.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopFloatingService() {
        stopService(new Intent(this, FloatingAssistantService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureFloatingServiceState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }
}
