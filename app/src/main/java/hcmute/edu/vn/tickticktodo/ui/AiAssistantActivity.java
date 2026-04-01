package hcmute.edu.vn.tickticktodo.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;
import hcmute.edu.vn.tickticktodo.service.FloatingAssistantService;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.adapter.ChatAdapter;
import hcmute.edu.vn.tickticktodo.agent.AgentAction;
import hcmute.edu.vn.tickticktodo.agent.AgentPromptContract;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseParser;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.databinding.ActivityAiAssistantBinding;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.model.ChatMessage;
import hcmute.edu.vn.tickticktodo.model.Task;

public class AiAssistantActivity extends BaseActivity {

    private static final String PREFS_NAME = "TickTickPrefs";
    private static final String KEY_FLOATING_ASSISTANT_ENABLED = "floating_assistant_enabled";
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiAssistantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Register overlay permission launcher
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleOverlayPermissionResult());

        dbExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        setupRecyclerView();
        setupGemini();
        ensureFloatingServiceState();

        if (binding.btnBack != null) {
            binding.btnBack.setOnClickListener(v -> finish());
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
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.chatRecyclerView.setLayoutManager(layoutManager);
        binding.chatRecyclerView.setAdapter(chatAdapter);
    }

    private void setupGemini() {
        geminiManager = GeminiManager.getInstance();

        if (!geminiManager.hasConfiguredApiKey()) {
            showAssistantMessage("Vui lòng kiểm tra cấu hình API Key trong local.properties");
            return;
        }

        showAssistantMessage("Xin chào! Tôi là trợ lý TickTickToDo. Bạn có thể nhắn: tạo task, hoàn thành task, hoặc xem danh sách hôm nay.");
    }

    private void showSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(hcmute.edu.vn.tickticktodo.R.layout.layout_ai_settings_dialog, null);
        dialog.setContentView(view);

        SwitchMaterial switchFloating = view.findViewById(hcmute.edu.vn.tickticktodo.R.id.switchFloatingAi);
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
        if (geminiManager == null) {
            showAssistantMessage("Vui lòng kiểm tra cấu hình API Key trong local.properties");
            return;
        }

        binding.messageInput.setText("");
        showUserMessage(userText);
        binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

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
        if (text == null || text.trim().isEmpty()) return;

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
        chatAdapter.addMessage(new ChatMessage(text, true));
        rememberTurn("user", text);
    }

    private void showAssistantMessage(String text) {
        mainHandler.post(() -> {
            chatAdapter.addMessage(new ChatMessage(text, false));
            rememberTurn("assistant", text);
            binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        });
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
                // Fallback cho thiết bị/ROM không cho foreground service ngay thời điểm hiện tại.
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

    private void showTextResponse(String text) {
        showAssistantMessage(text);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }
}