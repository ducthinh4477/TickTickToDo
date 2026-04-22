package hcmute.edu.vn.tickticktodo.ui.chat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.ai.agent.AgentAction;
import hcmute.edu.vn.tickticktodo.agent.AgentExecutionContext;
import hcmute.edu.vn.tickticktodo.agent.AgentPromptContract;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseParser;
import hcmute.edu.vn.tickticktodo.ai.agent.AgentToolNames;
import hcmute.edu.vn.tickticktodo.agent.integration.IntegrationFacade;
import hcmute.edu.vn.tickticktodo.agent.orchestrator.AgentOrchestrator;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveEngine;
import hcmute.edu.vn.tickticktodo.agent.tools.ApplyPlanOptionTool;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolCall;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;
import hcmute.edu.vn.tickticktodo.data.dao.ChatHistoryDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.data.model.SuggestionEntity;
import hcmute.edu.vn.tickticktodo.databinding.ActivityAiAssistantBinding;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.helper.SecurePreferencesHelper;
import hcmute.edu.vn.tickticktodo.model.ChatHistoryMessage;
import hcmute.edu.vn.tickticktodo.model.ChatMessage;
import hcmute.edu.vn.tickticktodo.model.ChatSession;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.core.background.FloatingAssistantService;
import hcmute.edu.vn.tickticktodo.ui.debug.ProactiveDebugActivity;

public class AiAssistantActivity extends BaseActivity {

    public static final String EXTRA_SESSION_ID = "extra_chat_session_id";
    public static final String EXTRA_CREATE_NEW_SESSION = "extra_chat_create_new_session";

    private static final String PREFS_NAME = "TickTickPrefs";
    private static final String KEY_FLOATING_ASSISTANT_ENABLED = "floating_assistant_enabled";
    private static final String CHAT_SOURCE_MAIN = "ai_assistant";

    /**
     * True once a session has been created in the current process lifetime.
     * Reset to false when the process is killed, so the next cold launch always
     * starts with a brand-new conversation.
     */
    private static volatile boolean sProcessSessionCreated = false;
    private static final String ACTION_APPLY_PLAN_OPTION = "APPLY_PLAN_OPTION";
    private static final String ACTION_DATA_SEPARATOR = "::";
    private static final int MAX_MEMORY_LINES = 10;
        private static final String HEALTH_PERMISSION_READ_STEPS = "android.permission.health.READ_STEPS";
        private static final String HEALTH_PERMISSION_READ_SLEEP = "android.permission.health.READ_SLEEP";
        private static final String HEALTH_PERMISSION_READ_ACTIVE_CALORIES =
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED";
    // Accepts Gemini (AIza...), OpenAI (sk-...), Anthropic (sk-ant-...), and other providers
    private static final Pattern API_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-\\.]{10,}$");
    private static final Pattern GEMINI_MODEL_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._/-]{3,128}$");
    private static final Object DB_EXECUTOR_LOCK = new Object();
    private static volatile ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private ActivityAiAssistantBinding binding;
    private ChatAdapter chatAdapter;
    private GeminiManager geminiManager;
    private SecurePreferencesHelper securePreferencesHelper;
    private AgentOrchestrator agentOrchestrator;
    private Handler mainHandler;
    private final ArrayDeque<String> conversationMemory = new ArrayDeque<>();
    private final AgentResponseParser responseParser = new AgentResponseParser();
    private final PlanPreviewMessageFormatter planPreviewMessageFormatter = new PlanPreviewMessageFormatter();
    private final SuggestionCardMessageFormatter suggestionCardMessageFormatter =
            new SuggestionCardMessageFormatter();
        private final SuggestionFeedbackCommandParser suggestionFeedbackCommandParser =
            new SuggestionFeedbackCommandParser();
    private final HashSet<String> surfacedSuggestionIds = new HashSet<>();
    private volatile String lastSurfacedSuggestionId;

    private SharedPreferences sharedPrefs;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private ActivityResultLauncher<String[]> integrationPermissionsLauncher;
    private ActivityResultLauncher<Intent> historyLauncher;
    private LiveData<List<SuggestionEntity>> suggestionLiveData;
    private TextView integrationPermissionStatusView;
    private Button integrationPermissionActionButton;

    private volatile long currentSessionId = -1L;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiAssistantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        securePreferencesHelper = SecurePreferencesHelper.getInstance(getApplicationContext());

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleOverlayPermissionResult());

        integrationPermissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> handleIntegrationPermissionsResult());

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

        mainHandler = new Handler(Looper.getMainLooper());
        destroyed = false;

        setupRecyclerView();
        setupQuickPrompts();
        setupGemini();
        updateModelNameIndicator();
        observeProactiveSuggestions();
        ensureFloatingServiceState();

        if (binding.btnNavigateBack != null) {
            binding.btnNavigateBack.setOnClickListener(v -> finish());
        }

        if (binding.btnHistory != null) {
            binding.btnHistory.setOnClickListener(v -> openChatHistory());
        }

        if (binding.btnSettings != null) {
            binding.btnSettings.setOnClickListener(v -> showSettingsDialog());
            binding.btnSettings.setOnLongClickListener(v -> {
                openProactiveDebugScreen();
                return true;
            });
        }

        binding.btnSend.setOnClickListener(v -> {
            String message = binding.messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
            }
        });

        if (binding.btnAttachTask != null) {
            binding.btnAttachTask.setOnClickListener(v -> showAttachTaskPicker());
        }

        binding.messageInput.setOnFocusChangeListener((v, hasFocus) ->
                binding.inputLayout.animate()
                        .translationZ(hasFocus ? 8f : 4f)
                        .setDuration(200)
                        .start());

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
        chatAdapter.setActionClickListener(this::handleChatMessageAction);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.chatRecyclerView.setLayoutManager(layoutManager);
        binding.chatRecyclerView.setAdapter(chatAdapter);
    }

    private void setupQuickPrompts() {
        if (binding.chipQuickTodayPlan != null) {
            binding.chipQuickTodayPlan.setOnClickListener(v -> sendMessage("Lên kế hoạch công việc hôm nay theo mức ưu tiên giúp tôi."));
        }
        if (binding.chipQuickPrioritize != null) {
            binding.chipQuickPrioritize.setOnClickListener(v -> sendMessage("Hãy giúp tôi phân loại việc cần làm theo mức độ khẩn cấp và quan trọng."));
        }
        if (binding.chipQuickHabit != null) {
            binding.chipQuickHabit.setOnClickListener(v -> sendMessage("Gợi ý cho tôi 3 thói quen nhỏ để duy trì năng suất trong ngày."));
        }
        if (binding.chipQuickReview != null) {
            binding.chipQuickReview.setOnClickListener(v -> sendMessage("Tổng kết ngày hôm nay và đề xuất việc cần chuẩn bị cho ngày mai."));
        }
    }

    private void setupGemini() {
        geminiManager = GeminiManager.getInstance();
        geminiManager.reloadConfiguration();
        agentOrchestrator = new AgentOrchestrator(getApplication());

        if (!geminiManager.hasConfiguredApiKey()) {
            showAssistantMessage(getString(R.string.assistant_api_key_missing_message));
        }
    }

    private void observeProactiveSuggestions() {
        ProactiveEngine proactiveEngine = ProactiveEngine.getInstance(getApplicationContext());
        suggestionLiveData = proactiveEngine.observePendingSuggestions();
        if (suggestionLiveData == null) {
            return;
        }

        suggestionLiveData.observe(this, suggestions -> {
            if (suggestions == null || suggestions.isEmpty()) {
                return;
            }

            for (SuggestionEntity suggestion : suggestions) {
                if (suggestion == null || TextUtils.isEmpty(suggestion.id)) {
                    continue;
                }
                if (surfacedSuggestionIds.contains(suggestion.id)) {
                    continue;
                }

                surfacedSuggestionIds.add(suggestion.id);
                renderSuggestionCard(suggestion);
                proactiveEngine.markSuggestionShown(suggestion.id);
            }
        });
    }

    private void renderSuggestionCard(SuggestionEntity suggestion) {
        if (suggestion == null) {
            return;
        }

        lastSurfacedSuggestionId = suggestion.id;

        String card = suggestionCardMessageFormatter.buildSuggestionCard(suggestion);
        if (!TextUtils.isEmpty(card)) {
            showAssistantMessage(card);
        }
    }

    private void openProactiveDebugScreen() {
        try {
            startActivity(new Intent(this, ProactiveDebugActivity.class));
        } catch (Exception ignored) {
        }
    }

    private void initializeSessionFromIntent(Intent intent) {
        final boolean shouldCreateNew = intent != null && intent.getBooleanExtra(EXTRA_CREATE_NEW_SESSION, false);
        final long requestedSessionId = intent != null ? intent.getLongExtra(EXTRA_SESSION_ID, -1L) : -1L;

        // Detect a cold process start: the static flag is false only when the OS has
        // killed and restarted the process (i.e. the user "reset" / force-quit the app).
        final boolean isColdStart = !sProcessSessionCreated;
        if (isColdStart) sProcessSessionCreated = true;

        if (intent != null) {
            intent.removeExtra(EXTRA_CREATE_NEW_SESSION);
            intent.removeExtra(EXTRA_SESSION_ID);
        }

        runDbTask(() -> {
            ChatHistoryDao dao = TaskDatabase.getInstance(this).chatHistoryDao();
            long resolvedSessionId;

            if (shouldCreateNew || isColdStart) {
                // Explicit "new session" request OR first launch after app restart →
                // always begin with a fresh conversation.
                resolvedSessionId = createNewSessionSync(dao, CHAT_SOURCE_MAIN, "");
            } else if (requestedSessionId > 0L && dao.getSessionByIdSync(requestedSessionId) != null) {
                resolvedSessionId = requestedSessionId;
            } else {
                // Within the same process (user navigated away and came back):
                // resume the most recent ai_assistant session.
                ChatSession latest = dao.getLatestSessionBySourceSync(CHAT_SOURCE_MAIN);
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
        runDbTask(() -> {
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
        runDbTask(() -> {
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
        if (binding.quickPromptContainer == null) return;
        if (chatAdapter.getItemCount() == 0) {
            binding.quickPromptContainer.setVisibility(View.VISIBLE);
        } else if (binding.quickPromptContainer.getVisibility() == View.VISIBLE) {
            binding.quickPromptContainer.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> binding.quickPromptContainer.setVisibility(View.GONE))
                    .start();
        }
    }

    private void openChatHistory() {
        Intent intent = new Intent(this, ChatHistoryActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, currentSessionId);
        historyLauncher.launch(intent);
    }

    private void showAttachTaskPicker() {
        runDbTask(() -> {
            List<Task> tasks = TaskDatabase.getInstance(this).taskDao().getAllTasksSync();
            if (tasks == null || tasks.isEmpty()) {
                mainHandler.post(() -> Toast.makeText(this,
                        getString(R.string.assistant_attach_no_tasks), Toast.LENGTH_SHORT).show());
                return;
            }
            String[] titles = new String[tasks.size()];
            for (int i = 0; i < tasks.size(); i++) {
                titles[i] = tasks.get(i).getTitle();
            }
            mainHandler.post(() -> new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.assistant_attach_task_desc))
                    .setItems(titles, (dialog, which) -> {
                        String taskTitle = tasks.get(which).getTitle();
                        String current = binding.messageInput.getText().toString();
                        String inserted = current.isEmpty() ? "[Task: " + taskTitle + "] "
                                : current + " [Task: " + taskTitle + "] ";
                        binding.messageInput.setText(inserted);
                        binding.messageInput.setSelection(inserted.length());
                    })
                    .show());
        });
    }

    private void showSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_ai_settings_dialog, null);
        dialog.setContentView(view);

        SwitchMaterial switchFloating = view.findViewById(R.id.switchFloatingAi);
        SwitchMaterial switchIntegrationCalendar = view.findViewById(R.id.switchIntegrationCalendar);
        SwitchMaterial switchIntegrationMoodle = view.findViewById(R.id.switchIntegrationMoodle);
        SwitchMaterial switchIntegrationHealth = view.findViewById(R.id.switchIntegrationHealth);
        TextView tvIntegrationPermissionsStatus = view.findViewById(R.id.tvIntegrationPermissionsStatus);
        Button btnGrantIntegrationPermissions = view.findViewById(R.id.btnGrantIntegrationPermissions);
        Button btnResetIntegrationSources = view.findViewById(R.id.btnResetIntegrationSources);
        TextView tvCurrentAiModelValue = view.findViewById(R.id.tvCurrentAiModelValue);
        Button btnModelDetails = view.findViewById(R.id.btnModelDetails);
        Button btnAddAiModel = view.findViewById(R.id.btnAddAiModel);
        if (switchFloating == null
            || switchIntegrationCalendar == null
            || switchIntegrationMoodle == null
            || switchIntegrationHealth == null
                || tvIntegrationPermissionsStatus == null
                || btnGrantIntegrationPermissions == null
                || btnResetIntegrationSources == null
                || btnModelDetails == null
                || btnAddAiModel == null
                || tvCurrentAiModelValue == null) {
            dialog.dismiss();
            Toast.makeText(this, "Không thể mở cài đặt Trợ lý nổi.", Toast.LENGTH_SHORT).show();
            return;
        }

        tvCurrentAiModelValue.setText(getCurrentModelDisplayName());

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

        IntegrationFacade integrationFacade = IntegrationFacade.getInstance(getApplication());
        final boolean[] suppressIntegrationToggleSideEffects = {true};
        switchIntegrationCalendar.setChecked(integrationFacade.isCalendarIntegrationEnabled());
        switchIntegrationMoodle.setChecked(integrationFacade.isMoodleIntegrationEnabled());
        switchIntegrationHealth.setChecked(integrationFacade.isHealthIntegrationEnabled());
        suppressIntegrationToggleSideEffects[0] = false;

        switchIntegrationCalendar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressIntegrationToggleSideEffects[0]) {
                return;
            }
            integrationFacade.setCalendarIntegrationEnabled(isChecked);
            if (isChecked && !hasCalendarReadPermission()) {
                requestMissingIntegrationPermissions();
            }
            refreshIntegrationPermissionUi();
        });

        switchIntegrationMoodle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressIntegrationToggleSideEffects[0]) {
                return;
            }
            integrationFacade.setMoodleIntegrationEnabled(isChecked);
        });

        switchIntegrationHealth.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressIntegrationToggleSideEffects[0]) {
                return;
            }
            integrationFacade.setHealthIntegrationEnabled(isChecked);
        });

        btnResetIntegrationSources.setOnClickListener(v -> {
            integrationFacade.resetSourceSettingsToDefault();

            suppressIntegrationToggleSideEffects[0] = true;
            switchIntegrationCalendar.setChecked(integrationFacade.isCalendarIntegrationEnabled());
            switchIntegrationMoodle.setChecked(integrationFacade.isMoodleIntegrationEnabled());
            switchIntegrationHealth.setChecked(integrationFacade.isHealthIntegrationEnabled());
            suppressIntegrationToggleSideEffects[0] = false;

            refreshIntegrationPermissionUi();
            Toast.makeText(this, R.string.assistant_integration_sources_reset_done, Toast.LENGTH_SHORT).show();
        });

        integrationPermissionStatusView = tvIntegrationPermissionsStatus;
        integrationPermissionActionButton = btnGrantIntegrationPermissions;
        refreshIntegrationPermissionUi();

        btnGrantIntegrationPermissions.setOnClickListener(v -> requestMissingIntegrationPermissions());

        btnModelDetails.setOnClickListener(v -> {
            showModelDetailDialog(tvCurrentAiModelValue);
        });

        btnAddAiModel.setOnClickListener(v -> {
            showAddModelDialog(tvCurrentAiModelValue);
        });

        dialog.setOnDismissListener(listener -> {
            if (integrationPermissionStatusView == tvIntegrationPermissionsStatus) {
                integrationPermissionStatusView = null;
            }
            if (integrationPermissionActionButton == btnGrantIntegrationPermissions) {
                integrationPermissionActionButton = null;
            }
        });

        dialog.show();
    }

    private void requestMissingIntegrationPermissions() {
        IntegrationFacade integrationFacade = IntegrationFacade.getInstance(getApplication());
        List<String> missingPermissions = new ArrayList<>();
        if (!hasCalendarReadPermission()) {
            missingPermissions.add(Manifest.permission.READ_CALENDAR);
        }
        if (isActivityRecognitionRequired() && !hasActivityRecognitionPermission()) {
            missingPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (integrationFacade.isHealthIntegrationEnabled() && isHealthConnectPermissionRequired()) {
            if (!hasHealthPermission(HEALTH_PERMISSION_READ_STEPS)) {
                missingPermissions.add(HEALTH_PERMISSION_READ_STEPS);
            }
            if (!hasHealthPermission(HEALTH_PERMISSION_READ_SLEEP)) {
                missingPermissions.add(HEALTH_PERMISSION_READ_SLEEP);
            }
            if (!hasHealthPermission(HEALTH_PERMISSION_READ_ACTIVE_CALORIES)) {
                missingPermissions.add(HEALTH_PERMISSION_READ_ACTIVE_CALORIES);
            }
        }

        if (missingPermissions.isEmpty()) {
            Toast.makeText(this, R.string.assistant_integration_permissions_already_granted, Toast.LENGTH_SHORT).show();
            refreshIntegrationPermissionUi();
            return;
        }

        integrationPermissionsLauncher.launch(missingPermissions.toArray(new String[0]));
    }

    private void handleIntegrationPermissionsResult() {
        refreshIntegrationPermissionUi();

        if (hasAllIntegrationPermissions()) {
            Toast.makeText(this, R.string.assistant_integration_permissions_granted_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean permanentlyDenied = isPermissionPermanentlyDenied(Manifest.permission.READ_CALENDAR)
                || (isActivityRecognitionRequired()
                && isPermissionPermanentlyDenied(Manifest.permission.ACTIVITY_RECOGNITION))
                || (isHealthConnectPermissionRequired()
                && (isPermissionPermanentlyDenied(HEALTH_PERMISSION_READ_STEPS)
                || isPermissionPermanentlyDenied(HEALTH_PERMISSION_READ_SLEEP)
                || isPermissionPermanentlyDenied(HEALTH_PERMISSION_READ_ACTIVE_CALORIES)));

        Toast.makeText(this, R.string.assistant_integration_permissions_partial_toast, Toast.LENGTH_SHORT).show();
        if (permanentlyDenied) {
            showOpenIntegrationSettingsDialog();
        }
    }

    private void refreshIntegrationPermissionUi() {
        if (integrationPermissionStatusView == null || integrationPermissionActionButton == null) {
            return;
        }

        integrationPermissionStatusView.setText(buildIntegrationPermissionStatusMessage());
        integrationPermissionActionButton.setText(hasAllIntegrationPermissions()
                ? R.string.assistant_integration_permissions_button_done
                : R.string.assistant_integration_permissions_button_grant);
    }

    private String buildIntegrationPermissionStatusMessage() {
        IntegrationFacade integrationFacade = IntegrationFacade.getInstance(getApplication());

        String calendarStatus = hasCalendarReadPermission()
                ? getString(R.string.assistant_integration_permission_granted)
                : getString(R.string.assistant_integration_permission_missing);

        String activityStatus;
        if (!isActivityRecognitionRequired()) {
            activityStatus = getString(R.string.assistant_integration_permission_not_required);
        } else if (hasActivityRecognitionPermission()) {
            activityStatus = getString(R.string.assistant_integration_permission_granted);
        } else {
            activityStatus = getString(R.string.assistant_integration_permission_missing);
        }

        String healthStatus;
        if (!integrationFacade.isHealthIntegrationEnabled()) {
            healthStatus = getString(R.string.assistant_integration_permission_disabled_by_toggle);
        } else if (!isHealthConnectPermissionRequired()) {
            healthStatus = getString(R.string.assistant_integration_permission_not_required);
        } else if (hasHealthConnectReadPermissions()) {
            healthStatus = getString(R.string.assistant_integration_permission_granted);
        } else {
            healthStatus = getString(R.string.assistant_integration_permission_missing);
        }

        return getString(
                R.string.assistant_integration_permission_status_template,
                getString(R.string.assistant_integration_permission_calendar_label),
                calendarStatus,
                getString(R.string.assistant_integration_permission_activity_label),
                activityStatus,
                getString(R.string.assistant_integration_permission_health_label),
                healthStatus
        );
    }

    private boolean hasAllIntegrationPermissions() {
        IntegrationFacade integrationFacade = IntegrationFacade.getInstance(getApplication());
        boolean healthPermissionOk = true;
        if (integrationFacade.isHealthIntegrationEnabled() && isHealthConnectPermissionRequired()) {
            healthPermissionOk = hasHealthConnectReadPermissions();
        }
        return hasCalendarReadPermission() && hasActivityRecognitionPermission() && healthPermissionOk;
    }

    private boolean hasCalendarReadPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasActivityRecognitionPermission() {
        if (!isActivityRecognitionRequired()) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isActivityRecognitionRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    private boolean isHealthConnectPermissionRequired() {
        IntegrationFacade integrationFacade = IntegrationFacade.getInstance(getApplication());
        return integrationFacade.isHealthConnectSupported() && integrationFacade.isHealthConnectAvailable();
    }

    private boolean hasHealthConnectReadPermissions() {
        return hasHealthPermission(HEALTH_PERMISSION_READ_STEPS)
                && hasHealthPermission(HEALTH_PERMISSION_READ_SLEEP)
                && hasHealthPermission(HEALTH_PERMISSION_READ_ACTIVE_CALORIES);
    }

    private boolean hasHealthPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isPermissionPermanentlyDenied(String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        return !shouldShowRequestPermissionRationale(permission);
    }

    private void showOpenIntegrationSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.assistant_integration_permissions_settings_title)
                .setMessage(R.string.assistant_integration_permissions_settings_message)
                .setPositiveButton(R.string.assistant_open_app_settings,
                        (dialog, which) -> openAppDetailSettings())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAppDetailSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void showAddModelDialog(TextView tvCurrentAiModelValue) {
        showModelEditorDialog(tvCurrentAiModelValue, false);
    }

    private void showModelDetailDialog(TextView tvCurrentAiModelValue) {
        showModelEditorDialog(tvCurrentAiModelValue, true);
    }

    private void showModelEditorDialog(TextView tvCurrentAiModelValue, boolean detailMode) {
        BottomSheetDialog popupDialog = new BottomSheetDialog(this);
        View popupView = getLayoutInflater().inflate(R.layout.layout_add_ai_model_dialog, null);
        popupDialog.setContentView(popupView);

        TextView tvModelDialogTitle = popupView.findViewById(R.id.tvModelDialogTitle);
        EditText editPopupModelName = popupView.findViewById(R.id.editPopupModelName);
        EditText editPopupApiKey = popupView.findViewById(R.id.editPopupApiKey);
        Button btnSaveAiModelPopup = popupView.findViewById(R.id.btnSaveAiModelPopup);

        if (tvModelDialogTitle == null
                || editPopupModelName == null
                || editPopupApiKey == null
                || btnSaveAiModelPopup == null) {
            popupDialog.dismiss();
            Toast.makeText(this, R.string.assistant_settings_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        if (detailMode) {
            tvModelDialogTitle.setText(R.string.assistant_model_detail_popup_title);
            btnSaveAiModelPopup.setText(R.string.assistant_update_model_button);

            String currentModelName = securePreferencesHelper.getAiModel();
            String currentApiKey = securePreferencesHelper.getApiKey();
            if (!TextUtils.isEmpty(currentModelName)) {
                editPopupModelName.setText(currentModelName);
                editPopupModelName.setSelection(currentModelName.length());
            }
            if (!TextUtils.isEmpty(currentApiKey)) {
                editPopupApiKey.setText(currentApiKey);
                editPopupApiKey.setSelection(currentApiKey.length());
            }
        } else {
            tvModelDialogTitle.setText(R.string.assistant_add_model_popup_title);
            btnSaveAiModelPopup.setText(R.string.assistant_save_model_button);
        }

        btnSaveAiModelPopup.setOnClickListener(v -> {
            String modelName = editPopupModelName.getText() == null
                    ? ""
                    : editPopupModelName.getText().toString().trim();
            String apiKey = editPopupApiKey.getText() == null
                    ? ""
                    : editPopupApiKey.getText().toString().trim();

            if (TextUtils.isEmpty(modelName)) {
                Toast.makeText(this, R.string.assistant_model_name_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isModelNameValid(modelName)) {
                Toast.makeText(this, R.string.assistant_invalid_model_name, Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(apiKey)) {
                Toast.makeText(this, R.string.assistant_api_key_required, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isApiKeyFormatValid(apiKey)) {
                Toast.makeText(this, R.string.assistant_invalid_api_key, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                securePreferencesHelper.addAiModel(modelName);
                securePreferencesHelper.saveAiModel(modelName);
                securePreferencesHelper.saveApiKey(apiKey);

                GeminiManager manager = GeminiManager.getInstance();
                manager.reloadConfiguration();
                geminiManager = manager;

                tvCurrentAiModelValue.setText(getCurrentModelDisplayName());
                updateModelNameIndicator();
                Toast.makeText(this, R.string.assistant_settings_saved, Toast.LENGTH_SHORT).show();
                popupDialog.dismiss();
            } catch (Exception exception) {
                Toast.makeText(this, R.string.assistant_settings_save_failed, Toast.LENGTH_SHORT).show();
            }
        });

        popupDialog.show();
    }

    private void sendMessage(String userText) {
        binding.messageInput.setText("");

        if (tryHandleSuggestionFeedbackCommand(userText)) {
            return;
        }

        if (geminiManager == null || !geminiManager.hasConfiguredApiKey()) {
            showAssistantMessage(getString(R.string.assistant_api_key_missing_message));
            return;
        }

        showUserMessage(userText);
        showTypingIndicator();
        binding.chatRecyclerView.smoothScrollToPosition(Math.max(0, chatAdapter.getItemCount() - 1));

        if (tryHandleQuickCreateIntent(userText)) {
            return;
        }

        if (geminiManager != null && geminiManager.isQuotaCooldownActive()) {
            String cooldownMessage = geminiManager.getQuotaCooldownMessage();
            if (TextUtils.isEmpty(cooldownMessage)) {
                cooldownMessage = "Trợ lý AI đang tạm quá tải. Bạn thử lại sau ít giây nữa nhé.";
            }
            showAssistantMessage(cooldownMessage);
            return;
        }

        if (agentOrchestrator == null) {
            runLegacyAgentFlow(userText);
            return;
        }

        final boolean[] fallbackTriggered = {false};
        agentOrchestrator.handleUserMessage(userText, new AgentOrchestrator.Callback() {
            @Override
            public void onAssistantReply(String replyText) {
                if (shouldIgnoreAsyncResult() || fallbackTriggered[0]) {
                    return;
                }
                if (!TextUtils.isEmpty(replyText)) {
                    showAssistantMessage(replyText);
                }
            }

            @Override
            public void onToolResult(ToolResult toolResult) {
                if (shouldIgnoreAsyncResult() || fallbackTriggered[0] || toolResult == null) {
                    return;
                }
                if (handleSchedulerToolResult(toolResult)) {
                    return;
                }
                if (!toolResult.isSuccess() && "TOOL_NOT_FOUND".equals(toolResult.getErrorCode())) {
                    fallbackTriggered[0] = true;
                    runLegacyAgentFlow(userText);
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (shouldIgnoreAsyncResult() || fallbackTriggered[0]) {
                    return;
                }

                 if ((geminiManager != null && geminiManager.isQuotaCooldownActive())
                        || shouldSkipLegacyFallback(errorMessage)) {
                    fallbackTriggered[0] = true;
                    showAssistantMessage(TextUtils.isEmpty(errorMessage)
                            ? "Trợ lý AI đang bận hoặc chưa sẵn sàng. Bạn thử lại sau ít phút nhé."
                            : errorMessage);
                    return;
                }

                fallbackTriggered[0] = true;
                runLegacyAgentFlow(userText);
            }
        });
    }

    private boolean shouldSkipLegacyFallback(String errorMessage) {
        if (TextUtils.isEmpty(errorMessage)) {
            return false;
        }

        String lowered = errorMessage.toLowerCase(Locale.ROOT);
        return lowered.contains("429")
                || lowered.contains("rpm")
                || lowered.contains("rate limit")
                || lowered.contains("resource_exhausted")
                || lowered.contains("quota")
                || lowered.contains("api key")
                || lowered.contains("mô hình")
                || lowered.contains("model")
                || lowered.contains("không thể kết nối mạng")
                || lowered.contains("failed to connect")
                || lowered.contains("timeout");
    }

    private boolean tryHandleSuggestionFeedbackCommand(String userText) {
        SuggestionFeedbackCommandParser.ParsedSuggestionFeedbackCommand parsedCommand =
                suggestionFeedbackCommandParser.parse(userText);
        if (parsedCommand == null) {
            return false;
        }

        String feedbackType = parsedCommand.getFeedbackType();
        String target = parsedCommand.getTargetToken();

        String suggestionId;
        if ("last".equalsIgnoreCase(target)) {
            suggestionId = lastSurfacedSuggestionId;
        } else {
            suggestionId = target;
        }

        showUserMessage(userText);

        if (TextUtils.isEmpty(suggestionId)) {
            showAssistantMessage("Chua co goi y gan nhat de xu ly.");
            return true;
        }

        ProactiveEngine.getInstance(getApplicationContext())
                .recordSuggestionFeedback(suggestionId, feedbackType, "chat");
        showAssistantMessage("Da ghi nhan feedback " + feedbackType + " cho suggestion " + suggestionId + ".");
        return true;
    }

    private boolean handleSchedulerToolResult(ToolResult toolResult) {
        if (toolResult == null) {
            return false;
        }

        if (!toolResult.isSuccess()) {
            if (AgentToolNames.APPLY_PLAN_OPTION_TOOL.equals(toolResult.getToolName())) {
                String error = toolResult.getErrorMessage();
                if (TextUtils.isEmpty(error)) {
                    error = "Không thể áp dụng kế hoạch lúc này.";
                }
                showAssistantMessage(error);
                return true;
            }
            return false;
        }

        JSONObject data = toolResult.getData();
        String renderType = data == null ? "" : data.optString("renderType", "");

        if ("PLAN_PROPOSAL".equalsIgnoreCase(renderType)) {
            renderPlanProposalMessages(data);
            return true;
        }

        if ("PLAN_APPLY_RESULT".equalsIgnoreCase(renderType)
                || AgentToolNames.APPLY_PLAN_OPTION_TOOL.equals(toolResult.getToolName())) {
            String message = data == null ? "" : data.optString("message", "");
            if (TextUtils.isEmpty(message)) {
                String optionId = data == null ? "" : data.optString("optionId", "");
                int appliedTaskCount = data == null ? 0 : data.optInt("appliedTaskCount", 0);
                message = "Đã áp dụng phương án " + optionId + " cho " + appliedTaskCount + " task.";
            }
            showAssistantMessage(message);
            return true;
        }

        return false;
    }

    private void renderPlanProposalMessages(JSONObject data) {
        if (data == null) {
            return;
        }

        String proposalId = data.optString("proposalId", "");
        String proposalType = data.optString("proposalType", "DAILY");
        String anchorDate = data.optString("anchorDate", "");
        JSONArray options = data.optJSONArray("options");

        if (options == null || options.length() == 0) {
            showAssistantMessage(planPreviewMessageFormatter.buildEmptyStateMessage());
            return;
        }

        showAssistantMessage(planPreviewMessageFormatter.buildProposalSummary(
            proposalType,
            anchorDate,
            options.length()
        ));

        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option == null) {
                continue;
            }

            String optionId = option.optString("optionId", "");
            String label = option.optString("label", "Option");
            String description = option.optString("description", "");
            int scheduled = option.optInt("scheduledMinutes", 0);
            int unscheduled = option.optInt("unscheduledMinutes", 0);

            String card = planPreviewMessageFormatter.buildOptionCard(
                    label,
                    optionId,
                    description,
                    scheduled,
                    unscheduled
            );

            if (!TextUtils.isEmpty(proposalId) && !TextUtils.isEmpty(optionId)) {
                showAssistantActionMessage(
                        card,
                        ACTION_APPLY_PLAN_OPTION,
                        "Áp dụng kế hoạch này",
                        encodeActionData(proposalId, optionId)
                );
            } else {
                showAssistantMessage(card);
            }
        }
    }

    private void handleChatMessageAction(ChatMessage message) {
        if (message == null) {
            return;
        }

        if (!ACTION_APPLY_PLAN_OPTION.equals(message.getActionType())) {
            return;
        }

        String[] parts = decodeActionData(message.getActionData());
        if (parts == null || parts.length < 2) {
            showAssistantMessage("Không đọc được option kế hoạch cần áp dụng.");
            return;
        }

        String proposalId = parts[0];
        String optionId = parts[1];
        if (TextUtils.isEmpty(proposalId) || TextUtils.isEmpty(optionId)) {
            showAssistantMessage("Thiếu proposalId/optionId để áp dụng kế hoạch.");
            return;
        }

        confirmAndApplyPlanOption(proposalId, optionId);
    }

    private void confirmAndApplyPlanOption(String proposalId, String optionId) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận áp dụng kế hoạch")
                .setMessage("Bạn có chắc muốn áp dụng option " + optionId + " cho lịch task hiện tại?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Áp dụng", (dialog, which) -> {
                    showUserMessage("Áp dụng option " + optionId);
                    executeApplyPlanOptionTool(proposalId, optionId);
                })
                .show();
    }

    private void executeApplyPlanOptionTool(String proposalId, String optionId) {
        runDbTask(() -> {
            try {
                JSONObject args = new JSONObject();
                args.put("proposalId", proposalId);
                args.put("optionId", optionId);
                args.put("confirmed", true);

                ToolCall call = new ToolCall(
                        UUID.randomUUID().toString(),
                        AgentToolNames.APPLY_PLAN_OPTION_TOOL,
                        args,
                        ""
                );

                ApplyPlanOptionTool tool = new ApplyPlanOptionTool();
                ToolResult result = tool.execute(call, AgentExecutionContext.create(getApplication()));
                mainHandler.post(() -> handleSchedulerToolResult(result));
            } catch (Exception e) {
                showAssistantMessage("Không thể áp dụng kế hoạch lúc này.");
            }
        });
    }

    private void showAssistantActionMessage(String text,
                                            String actionType,
                                            String actionLabel,
                                            String actionData) {
        mainHandler.post(() -> {
            if (TextUtils.isEmpty(text)) {
                return;
            }
            chatAdapter.addMessage(new ChatMessage(text, false, actionType, actionLabel, actionData));
            rememberTurn("assistant", text);
            persistMessageAsync("assistant", text, CHAT_SOURCE_MAIN);
            binding.chatRecyclerView.smoothScrollToPosition(Math.max(0, chatAdapter.getItemCount() - 1));
            updateQuickPromptVisibility();
        });
    }

    private String encodeActionData(String proposalId, String optionId) {
        return (proposalId == null ? "" : proposalId)
                + ACTION_DATA_SEPARATOR
                + (optionId == null ? "" : optionId);
    }

    private String[] decodeActionData(String actionData) {
        if (TextUtils.isEmpty(actionData)) {
            return null;
        }
        String[] parts = actionData.split(Pattern.quote(ACTION_DATA_SEPARATOR), 2);
        if (parts.length < 2) {
            return null;
        }
        return parts;
    }

    private void runLegacyAgentFlow(String userText) {
        runDbTask(() -> {
            String promptWithContext = buildAgentPrompt(userText);
            geminiManager.generateResponse(promptWithContext, new GeminiManager.ResponseCallback() {
                @Override
                public void onSuccess(String responseText) {
                    if (shouldIgnoreAsyncResult()) {
                        return;
                    }
                    handleAiResponse(responseText);
                }

                @Override
                public void onError(String errorMessage) {
                    if (shouldIgnoreAsyncResult()) {
                        return;
                    }
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

        runDbTask(() -> {
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

        runDbTask(() -> {
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
        runDbTask(() -> {
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

    private void showTypingIndicator() {
        mainHandler.post(() -> {
            if (binding.tvTypingIndicator == null) return;
            binding.tvTypingIndicator.setVisibility(View.VISIBLE);
            binding.tvTypingIndicator.animate().alpha(1f).setDuration(200).start();
        });
    }

    private void hideTypingIndicator() {
        if (binding.tvTypingIndicator == null) return;
        binding.tvTypingIndicator.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> binding.tvTypingIndicator.setVisibility(View.GONE))
                .start();
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
            hideTypingIndicator();
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

        runDbTask(() -> {
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

        // Only look for an existing session from the same source (avoids picking up
        // floating or voice sessions when the main assistant needs a session).
        ChatSession latest = dao.getLatestSessionBySourceSync(source);
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

    private void updateModelNameIndicator() {
        if (binding != null && binding.tvModelName != null) {
            binding.tvModelName.setText(getCurrentModelDisplayName());
        }
    }

    private String getCurrentModelDisplayName() {
        String currentModel = securePreferencesHelper.getAiModel();
        if (TextUtils.isEmpty(currentModel)) {
            return getString(R.string.assistant_current_model_empty);
        }
        return currentModel;
    }

    private boolean isModelNameValid(String modelName) {
        return !TextUtils.isEmpty(modelName)
                && GEMINI_MODEL_NAME_PATTERN.matcher(modelName.trim()).matches();
    }

    private boolean isApiKeyFormatValid(String apiKey) {
        return API_KEY_PATTERN.matcher(apiKey).matches();
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

    private static ExecutorService ensureDbExecutor() {
        synchronized (DB_EXECUTOR_LOCK) {
            if (dbExecutor == null || dbExecutor.isShutdown() || dbExecutor.isTerminated()) {
                dbExecutor = Executors.newSingleThreadExecutor();
            }
            return dbExecutor;
        }
    }

    private void runDbTask(Runnable task) {
        if (task == null) {
            return;
        }
        try {
            ensureDbExecutor().execute(task);
        } catch (RejectedExecutionException rejectedExecutionException) {
            // If an external shutdown slipped through, recreate and retry once.
            try {
                ensureDbExecutor().execute(task);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    private boolean shouldIgnoreAsyncResult() {
        if (destroyed || isFinishing()) {
            return true;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureFloatingServiceState();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (suggestionLiveData != null) {
            suggestionLiveData.removeObservers(this);
        }
        super.onDestroy();
    }
}
