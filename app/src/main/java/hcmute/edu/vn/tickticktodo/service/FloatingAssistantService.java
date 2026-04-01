package hcmute.edu.vn.tickticktodo.service;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.adapter.ChatAdapter;
import hcmute.edu.vn.tickticktodo.agent.AgentAction;
import hcmute.edu.vn.tickticktodo.agent.AgentOrchestrator;
import hcmute.edu.vn.tickticktodo.agent.AgentPromptContract;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseParser;
import hcmute.edu.vn.tickticktodo.agent.model.ToolResult;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.helper.ReminderScheduler;
import hcmute.edu.vn.tickticktodo.model.ChatMessage;
import hcmute.edu.vn.tickticktodo.model.Task;

public class FloatingAssistantService extends Service {

    public static final String ACTION_SHOW_BUBBLE = "hcmute.edu.vn.tickticktodo.action.SHOW_FLOATING_BUBBLE";
    public static final String ACTION_HIDE_BUBBLE = "hcmute.edu.vn.tickticktodo.action.HIDE_FLOATING_BUBBLE";
    public static final String ACTION_SHOW_DAILY_REVIEW = "hcmute.edu.vn.tickticktodo.action.SHOW_DAILY_REVIEW";
    public static final String ACTION_SHOW_HABIT_NUDGE = "hcmute.edu.vn.tickticktodo.action.SHOW_HABIT_NUDGE";

    public static final String EXTRA_DAILY_REVIEW_TEXT = "extra_daily_review_text";
    public static final String EXTRA_UNFINISHED_TASK_IDS = "extra_unfinished_task_ids";
    public static final String EXTRA_HABIT_NUDGE_TEXT = "extra_habit_nudge_text";

    private static final int NOTIFICATION_ID = 200;
    private static final String CHANNEL_ID = "floating_ai_channel";
    private static final String TAG = "FloatingAssistant";
    private static final int MAX_MEMORY_LINES = 8;
    private static final Object OVERLAY_LOCK = new Object();
    private static final String POSITION_PREFS = "floating_assistant_position";
    private static final String KEY_BUBBLE_X = "bubble_x";
    private static final String KEY_BUBBLE_Y = "bubble_y";
    private static final int DEFAULT_BUBBLE_X = 24;
    private static final int DEFAULT_BUBBLE_Y = 220;
    private static final int MAX_DEBUG_TRACE_LINES = 80;
    private static final int MAX_DEBUG_TRACE_CHARS = 2400;
    private static final int MAX_VOICE_AUTO_RETRY = 2;
    private static final long VOICE_RETRY_DELAY_MS = 500L;

    private static WindowManager sWindowManager;
    private static View sBubbleView;
    private static View sChatView;

    private WindowManager windowManager;
    private View floatingBubbleView;
    private View floatingChatView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams chatParams;
    private boolean chatCompatMode;

    private GeminiManager geminiManager;
    private ExecutorService workerExecutor;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isVoiceListening;
    private int voiceAutoRetryCount;
    private String latestPartialTranscript = "";
    private boolean voiceStopRequestedByUser;
    private Handler mainHandler;
    private RecyclerView floatingChatRecyclerView;
    private ChatAdapter floatingChatAdapter;
    private SharedPreferences positionPrefs;
    private View dailyReviewLayout;
    private TextView dailyReviewContent;
    private View moveUnfinishedButton;
    private View debugPanelLayout;
    private TextView debugPanelToggle;
    private TextView debugTraceContent;
    private ScrollView debugTraceScroll;
    private View debugTraceClear;
    private View voiceMicButton;
    private View voicePulseIndicator;
    private TextView voiceStatusText;
    private ObjectAnimator voicePulseAnimator;
    private boolean debugPanelVisible;
    private final ArrayList<Long> pendingCarryOverTaskIds = new ArrayList<>();
    private AgentOrchestrator agentOrchestrator;
    private final Runnable hideVoiceStatusRunnable = () -> {
        if (!isVoiceListening && voiceStatusText != null) {
            voiceStatusText.setVisibility(View.GONE);
        }
    };

    private final ArrayDeque<String> conversationMemory = new ArrayDeque<>();
    private final ArrayDeque<String> debugTraceMemory = new ArrayDeque<>();
    private final AgentResponseParser responseParser = new AgentResponseParser();

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            createNotificationChannel();
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Trợ lý AI TickTickToDo")
                    .setContentText("Trợ lý nổi đang hoạt động")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
            startForegroundSafely(notification);

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Missing overlay permission, stopping service");
                stopSelf();
                return;
            }

            cleanupStaleOverlayIfAny();

            mainHandler = new Handler(Looper.getMainLooper());
            workerExecutor = Executors.newSingleThreadExecutor();
            geminiManager = GeminiManager.getInstance();
            agentOrchestrator = new AgentOrchestrator(getApplication());
            positionPrefs = getSharedPreferences(POSITION_PREFS, MODE_PRIVATE);

            initSpeechRecognizer();
            initFloatingBubble();
            initFloatingChat();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize FloatingAssistantService", e);
            stopSelf();
        }
    }

    private void startForegroundSafely(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is not available");
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(android.os.Bundle params) {
                isVoiceListening = true;
                voiceStopRequestedByUser = false;
                updateVoiceUiState(true, "Đang lắng nghe...");
                appendDebugTrace("VOICE_READY", "SpeechRecognizer ready.");
            }

            @Override
            public void onBeginningOfSpeech() {
                isVoiceListening = true;
                voiceStopRequestedByUser = false;
                updateVoiceUiState(true, "Đang lắng nghe...");
                appendDebugTrace("VOICE_BEGIN", "Detected beginning of speech.");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                isVoiceListening = false;
                updateVoiceUiState(false, "Đã nghe xong, đang xử lý...");
                appendDebugTrace("VOICE_END", "End of speech captured.");
            }

            @Override
            public void onError(int error) {
                isVoiceListening = false;
                boolean userStopped = voiceStopRequestedByUser && error == SpeechRecognizer.ERROR_CLIENT;
                voiceStopRequestedByUser = false;

                if (userStopped) {
                    updateVoiceUiState(false, "Đã dừng nghe.");
                    appendDebugTrace("VOICE_STOPPED", "Stopped by user.");
                    return;
                }

                Log.e(TAG, "Speech error: " + error);
                appendDebugTrace("VOICE_ERROR", "code=" + error + ", message=" + mapSpeechErrorMessage(error));

                if (shouldUsePartialFallback(error)) {
                    if (applyRecognizedVoiceText(latestPartialTranscript, true)) {
                        return;
                    }
                }

                if (shouldRetryVoice(error)) {
                    scheduleVoiceRetry(error);
                    return;
                }

                updateVoiceUiState(false, mapSpeechErrorMessage(error));
                mainHandler.post(() ->
                        Toast.makeText(FloatingAssistantService.this, "Lỗi thu âm: " + error, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResults(android.os.Bundle results) {
                isVoiceListening = false;
                voiceStopRequestedByUser = false;
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (data == null || data.isEmpty()) {
                    if (applyRecognizedVoiceText(latestPartialTranscript, true)) {
                        return;
                    }
                    scheduleVoiceRetry(SpeechRecognizer.ERROR_NO_MATCH);
                    return;
                }

                String text = data.get(0);
                applyRecognizedVoiceText(text, false);
            }

            @Override
            public void onPartialResults(android.os.Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial == null || partial.isEmpty()) {
                    return;
                }

                String text = partial.get(0) == null ? "" : partial.get(0).trim();
                if (text.isEmpty()) {
                    return;
                }

                latestPartialTranscript = text;
                updateVoicePreviewText(text);
                updateVoiceUiState(true, "Đang nghe: " + abbreviateForStatus(text));
            }

            @Override
            public void onEvent(int eventType, android.os.Bundle params) {
            }
        });
    }

    private void initFloatingBubble() {
        floatingBubbleView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = getSavedBubbleX();
        bubbleParams.y = getSavedBubbleY();

        try {
            windowManager.addView(floatingBubbleView, bubbleParams);
            synchronized (OVERLAY_LOCK) {
                sWindowManager = windowManager;
                sBubbleView = floatingBubbleView;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to add floating bubble", e);
            stopSelf();
            return;
        }

        ImageView ivBubble = floatingBubbleView.findViewById(R.id.iv_floating_bubble);
        if (ivBubble == null) {
            Log.e(TAG, "Floating bubble view is missing iv_floating_bubble");
            stopSelf();
            return;
        }
        ivBubble.setClickable(false);

        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        final float clickThreshold = Math.max(touchSlop * 4f, 48f);
        View.OnTouchListener bubbleTouchListener = new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        if (!isDragging && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            bubbleParams.x = initialX + deltaX;
                            bubbleParams.y = initialY + deltaY;
                            windowManager.updateViewLayout(floatingBubbleView, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        float upDeltaX = Math.abs(event.getRawX() - initialTouchX);
                        float upDeltaY = Math.abs(event.getRawY() - initialTouchY);
                        if (!isDragging || (upDeltaX < clickThreshold && upDeltaY < clickThreshold)) {
                            openChatView();
                        } else {
                            saveBubblePosition(bubbleParams.x, bubbleParams.y);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };

        floatingBubbleView.setClickable(true);
        floatingBubbleView.setOnTouchListener(bubbleTouchListener);
        ivBubble.setOnTouchListener(null);
    }

    private void initFloatingChat() {
        floatingChatView = LayoutInflater.from(this).inflate(R.layout.layout_floating_chat, null);

        floatingChatRecyclerView = floatingChatView.findViewById(R.id.rv_floating_chat_messages);
        if (floatingChatRecyclerView != null) {
            floatingChatAdapter = new ChatAdapter();
            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            layoutManager.setStackFromEnd(true);
            floatingChatRecyclerView.setLayoutManager(layoutManager);
            floatingChatRecyclerView.setAdapter(floatingChatAdapter);
        }

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        chatParams = createChatLayoutParams(false);
        chatCompatMode = false;

        View btnClose = floatingChatView.findViewById(R.id.btn_close_chat);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> closeChatView());
        }

        EditText etInput = floatingChatView.findViewById(R.id.et_message_floating);
        View btnSend = floatingChatView.findViewById(R.id.btn_send_floating);
        View btnMic = floatingChatView.findViewById(R.id.btn_mic_floating);
        dailyReviewLayout = floatingChatView.findViewById(R.id.layout_daily_review_action);
        dailyReviewContent = floatingChatView.findViewById(R.id.tv_daily_review_content);
        moveUnfinishedButton = floatingChatView.findViewById(R.id.btn_move_unfinished_tasks);
        debugPanelLayout = floatingChatView.findViewById(R.id.layout_agent_debug_panel);
        debugPanelToggle = floatingChatView.findViewById(R.id.btn_toggle_debug_panel);
        debugTraceContent = floatingChatView.findViewById(R.id.tv_agent_debug_trace);
        debugTraceScroll = floatingChatView.findViewById(R.id.sv_agent_debug_trace);
        debugTraceClear = floatingChatView.findViewById(R.id.btn_clear_debug_trace);
        voiceMicButton = btnMic;
        voicePulseIndicator = floatingChatView.findViewById(R.id.voice_pulse_indicator);
        voiceStatusText = floatingChatView.findViewById(R.id.tv_voice_status_floating);
        debugPanelVisible = false;

        if (debugPanelToggle != null) {
            debugPanelToggle.setOnClickListener(v -> {
                debugPanelVisible = !debugPanelVisible;
                updateDebugPanelState();
            });
        }

        if (debugTraceClear != null) {
            debugTraceClear.setOnClickListener(v -> clearDebugTrace());
        }

        updateDebugPanelState();
        renderDebugTrace();

        if (moveUnfinishedButton != null) {
            moveUnfinishedButton.setOnClickListener(v -> carryOverUnfinishedTasks());
        }

        if (btnSend != null && etInput != null) {
            btnSend.setOnClickListener(v -> {
                String msg = etInput.getText().toString().trim();
                if (!msg.isEmpty()) {
                    etInput.setText("");
                    sendToGemini(msg);
                }
            });
        }

        if (btnMic != null) {
            btnMic.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                }
                return false;
            });
            btnMic.setOnClickListener(v -> toggleVoiceListening());
        }

        updateVoiceUiState(false, "Chạm mic để bắt đầu ghi âm");

        showAssistantMessage("Xin chào, mình là trợ lý nổi. Bạn có thể nói hoặc nhập lệnh tạo/hoàn thành task.");
        appendDebugTrace("DEBUG_PANEL", "Agent trace panel is ready. Toggle 'Hiện Trace' to watch tool-call flow.");
    }

    private void openChatView() {
        if (windowManager == null || floatingBubbleView == null || floatingChatView == null) {
            return;
        }

        if (floatingChatView.getParent() != null) {
            return;
        }

        saveBubblePosition(bubbleParams != null ? bubbleParams.x : DEFAULT_BUBBLE_X,
                bubbleParams != null ? bubbleParams.y : DEFAULT_BUBBLE_Y);

        try {
            if (floatingBubbleView.getParent() != null) {
                windowManager.removeView(floatingBubbleView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to remove bubble before opening chat", e);
        }

        if (chatParams == null) {
            chatParams = createChatLayoutParams(false);
            chatCompatMode = false;
        }

        try {
            windowManager.addView(floatingChatView, chatParams);
            synchronized (OVERLAY_LOCK) {
                sWindowManager = windowManager;
                sBubbleView = floatingBubbleView;
                sChatView = floatingChatView;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to add floating chat view", e);
            if (!chatCompatMode) {
                try {
                    chatParams = createChatLayoutParams(true);
                    chatCompatMode = true;
                    windowManager.addView(floatingChatView, chatParams);
                    synchronized (OVERLAY_LOCK) {
                        sWindowManager = windowManager;
                        sBubbleView = floatingBubbleView;
                        sChatView = floatingChatView;
                    }
                    Toast.makeText(this,
                            "Đã mở ở chế độ tương thích overlay.",
                            Toast.LENGTH_SHORT).show();
                    return;
                } catch (Exception compatException) {
                    Log.e(TAG, "Unable to add chat view in compatibility mode", compatException);
                }
            }

            restoreBubbleAfterOpenFailure();
        }
    }

    private WindowManager.LayoutParams createChatLayoutParams(boolean compatMode) {
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (compatMode) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        int maxWidth = getResources().getDisplayMetrics().widthPixels - dpToPx(24);
        int desiredWidth = Math.min(dpToPx(380), maxWidth);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Math.max(desiredWidth, dpToPx(280)),
            WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dpToPx(20);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
        return params;
    }

    private void toggleVoiceListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            updateVoiceUiState(false, "Thiếu quyền Microphone. Chuyển sang màn hình cấp quyền...");
            Toast.makeText(this,
                    "Chưa có quyền Microphone. Mình sẽ mở phần cài đặt app để bạn cấp quyền.",
                    Toast.LENGTH_LONG).show();
            openAppSettings();
            return;
        }

        if (speechRecognizer == null || speechRecognizerIntent == null) {
            updateVoiceUiState(false, "Speech recognizer chưa sẵn sàng.");
            Toast.makeText(this,
                    "Speech recognizer chưa sẵn sàng. Vui lòng thử lại.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (isVoiceListening) {
                voiceStopRequestedByUser = true;
                speechRecognizer.stopListening();
                isVoiceListening = false;
                updateVoiceUiState(false, "Đang dừng nghe...");
                Toast.makeText(this, "Đang dừng nghe...", Toast.LENGTH_SHORT).show();
            } else {
                startVoiceListening(false);
            }
        } catch (Exception e) {
            isVoiceListening = false;
            updateVoiceUiState(false, "Không thể bật voice lúc này.");
            Log.e(TAG, "Unable to toggle voice listening", e);
            Toast.makeText(this, "Không thể bật voice lúc này.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoiceListening(boolean fromRetry) {
        if (speechRecognizer == null || speechRecognizerIntent == null) {
            updateVoiceUiState(false, "Speech recognizer chưa sẵn sàng.");
            return;
        }

        if (!fromRetry) {
            voiceAutoRetryCount = 0;
            latestPartialTranscript = "";
        }

        voiceStopRequestedByUser = false;

        try {
            speechRecognizer.startListening(speechRecognizerIntent);
            isVoiceListening = true;
            updateVoiceUiState(true,
                    fromRetry
                            ? "Đang thử nghe lại..."
                            : "Đang lắng nghe... chạm mic lần nữa để dừng");
            appendDebugTrace("VOICE_START", fromRetry ? "Retry start listening" : "Start listening");
        } catch (Exception e) {
            isVoiceListening = false;
            Log.e(TAG, "Unable to start listening", e);
            appendDebugTrace("VOICE_START_ERROR", e.getMessage() == null ? "unknown" : e.getMessage());

            if (!fromRetry && voiceAutoRetryCount < MAX_VOICE_AUTO_RETRY) {
                voiceAutoRetryCount++;
                mainHandler.postDelayed(() -> startVoiceListening(true), VOICE_RETRY_DELAY_MS);
                return;
            }

            updateVoiceUiState(false, "Không thể khởi động nhận diện giọng nói.");
        }
    }

    private void scheduleVoiceRetry(int errorCode) {
        if (voiceAutoRetryCount >= MAX_VOICE_AUTO_RETRY) {
            updateVoiceUiState(false, mapSpeechErrorMessage(errorCode));
            return;
        }

        voiceAutoRetryCount++;
        updateVoiceUiState(false,
                "Không nghe rõ, đang thử lại ("
                        + voiceAutoRetryCount
                        + "/"
                        + MAX_VOICE_AUTO_RETRY
                        + ")...");
        appendDebugTrace("VOICE_RETRY", "error=" + errorCode + ", attempt=" + voiceAutoRetryCount);
        mainHandler.postDelayed(() -> startVoiceListening(true), VOICE_RETRY_DELAY_MS);
    }

    private boolean shouldRetryVoice(int error) {
        return error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER;
    }

    private boolean shouldUsePartialFallback(int error) {
        if (TextUtils.isEmpty(latestPartialTranscript)) {
            return false;
        }

        return error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER;
    }

    private boolean applyRecognizedVoiceText(String rawText, boolean fromPartial) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            return false;
        }

        voiceAutoRetryCount = 0;
        latestPartialTranscript = "";

        updateVoiceUiState(false,
                fromPartial
                        ? "Đã dùng kết quả nghe tạm thời."
                        : "Đã nhận giọng nói.");
        appendDebugTrace("VOICE_TEXT", text);

        updateVoicePreviewText(text);
        sendToGemini(text);
        return true;
    }

    private void updateVoicePreviewText(String text) {
        if (floatingChatView == null) {
            return;
        }

        EditText etInput = floatingChatView.findViewById(R.id.et_message_floating);
        if (etInput != null) {
            etInput.setText(text);
            etInput.setSelection(etInput.getText().length());
        }
    }

    private String abbreviateForStatus(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\n', ' ').trim();
        if (clean.length() <= 40) {
            return clean;
        }
        return clean.substring(0, 40) + "...";
    }

    private void updateVoiceUiState(boolean listening, String status) {
        if (voiceMicButton != null) {
            voiceMicButton.setBackgroundResource(
                    listening
                            ? R.drawable.bg_chat_mic_button_listening
                            : R.drawable.bg_chat_mic_button_idle
            );
            if (voiceMicButton instanceof ImageView) {
                ((ImageView) voiceMicButton).setColorFilter(
                        listening ? Color.WHITE : Color.parseColor("#3767D7")
                );
            }
        }

        if (voiceStatusText != null) {
            mainHandler.removeCallbacks(hideVoiceStatusRunnable);
            if (TextUtils.isEmpty(status)) {
                voiceStatusText.setVisibility(View.GONE);
            } else {
                voiceStatusText.setText(status);
                voiceStatusText.setVisibility(View.VISIBLE);
                if (!listening) {
                    mainHandler.postDelayed(hideVoiceStatusRunnable, 1800);
                }
            }
        }

        if (voicePulseIndicator != null) {
            if (listening) {
                voicePulseIndicator.setVisibility(View.VISIBLE);
                startVoicePulseAnimation();
            } else {
                stopVoicePulseAnimation();
                voicePulseIndicator.setVisibility(View.GONE);
            }
        }
    }

    private void startVoicePulseAnimation() {
        if (voicePulseIndicator == null) {
            return;
        }

        if (voicePulseAnimator == null) {
            voicePulseAnimator = ObjectAnimator.ofFloat(voicePulseIndicator, "alpha", 0.2f, 1f);
            voicePulseAnimator.setDuration(700L);
            voicePulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            voicePulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            voicePulseAnimator.setInterpolator(new LinearInterpolator());
        }

        if (!voicePulseAnimator.isStarted()) {
            voicePulseAnimator.start();
        }
    }

    private void stopVoicePulseAnimation() {
        if (voicePulseAnimator != null) {
            voicePulseAnimator.cancel();
        }
        if (voicePulseIndicator != null) {
            voicePulseIndicator.setAlpha(1f);
        }
    }

    private String mapSpeechErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Lỗi audio từ microphone.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Voice đã dừng.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Thiếu quyền Microphone.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Lỗi mạng khi nhận diện giọng nói.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "Không nhận diện được nội dung. Hãy thử nói rõ hơn.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognizer đang bận. Thử lại sau vài giây.";
            case SpeechRecognizer.ERROR_SERVER:
                return "Lỗi máy chủ nhận diện giọng nói.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Không phát hiện giọng nói.";
            default:
                return "Lỗi thu âm: " + error;
        }
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open app settings", e);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void restoreBubbleAfterOpenFailure() {
        try {
            if (floatingBubbleView.getParent() == null && bubbleParams != null) {
                bubbleParams.x = getSavedBubbleX();
                bubbleParams.y = getSavedBubbleY();
                windowManager.addView(floatingBubbleView, bubbleParams);
            }
            floatingBubbleView.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {
        }
    }

    private void closeChatView() {
        if (windowManager == null || floatingBubbleView == null || floatingChatView == null) {
            return;
        }

        try {
            if (floatingChatView.getParent() != null) {
                windowManager.removeView(floatingChatView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to close floating chat view", e);
        }

        synchronized (OVERLAY_LOCK) {
            sChatView = null;
        }

        try {
            if (floatingBubbleView.getParent() == null && bubbleParams != null) {
                bubbleParams.x = getSavedBubbleX();
                bubbleParams.y = getSavedBubbleY();
                windowManager.addView(floatingBubbleView, bubbleParams);
            }
            floatingBubbleView.setVisibility(View.VISIBLE);
            synchronized (OVERLAY_LOCK) {
                sWindowManager = windowManager;
                sBubbleView = floatingBubbleView;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to restore bubble after closing chat", e);
        }
    }

    private void sendToGemini(String message) {
        if (geminiManager == null || agentOrchestrator == null) {
            appendDebugTrace("GUARD", "Gemini or orchestrator is null.");
            showAssistantMessage("Vui lòng kiểm tra cấu hình API Key trong local.properties");
            return;
        }

        String userMessage = message == null ? "" : message.trim();
        if (userMessage.isEmpty()) {
            return;
        }

        appendDebugTrace("USER_INPUT", userMessage);
        showUserMessage("Bạn: " + userMessage);

        if (tryHandleQuickCreateIntent(userMessage)) {
            appendDebugTrace("SHORTCUT", "Handled by quick-create intent parser (no tool call).");
            return;
        }

        final boolean[] suppressNextAssistantReply = {false};
        agentOrchestrator.handleUserMessage(userMessage, new AgentOrchestrator.Callback() {
            @Override
            public void onAssistantReply(String replyText) {
                if (suppressNextAssistantReply[0]) {
                    suppressNextAssistantReply[0] = false;
                    return;
                }
                if (!TextUtils.isEmpty(replyText)) {
                    appendDebugTrace("ASSISTANT_REPLY", replyText);
                    showAssistantMessage(replyText);
                }
            }

            @Override
            public void onToolResult(ToolResult toolResult) {
                if (toolResult == null) {
                    return;
                }

                appendDebugTrace("TOOL_RESULT_CALLBACK", toolResult.toJson().toString());

                if (!toolResult.isSuccess() && "TOOL_NOT_FOUND".equals(toolResult.getErrorCode())) {
                    suppressNextAssistantReply[0] = true;
                    appendDebugTrace("FALLBACK", "TOOL_NOT_FOUND => switching to legacy parser flow.");
                    fallbackToLegacyAgentFlow(userMessage);
                }
            }

            @Override
            public void onDebugTrace(String stage, String payload) {
                appendDebugTrace(stage, payload);
            }

            @Override
            public void onError(String errorMessage) {
                // Keep the app usable even if orchestrator flow fails.
                appendDebugTrace("ORCHESTRATOR_ERROR", errorMessage);
                fallbackToLegacyAgentFlow(userMessage);
            }
        });
    }

    private void fallbackToLegacyAgentFlow(String userMessage) {
        appendDebugTrace("FALLBACK_PATH", "Running legacy prompt + response parser.");
        String prompt = buildAgentPrompt(userMessage);
        appendDebugTrace("LEGACY_PROMPT", prompt);
        geminiManager.generateResponse(prompt, new GeminiManager.ResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                appendDebugTrace("LEGACY_MODEL_RAW", responseText);
                workerExecutor.execute(() -> handleAIResponse(responseText));
            }

            @Override
            public void onError(String errorMessage) {
                appendDebugTrace("LEGACY_MODEL_ERROR", errorMessage);
                showAssistantMessage(errorMessage);
            }
        });
    }

    private void handleAIResponse(String responseText) {
        if (responseText == null || responseText.trim().isEmpty()) {
            showAssistantMessage("AI chưa trả về nội dung. Bạn thử lại nhé.");
            return;
        }

        AgentResponseEnvelope response = responseParser.parse(responseText);
        String action = response.getAction();
        JSONObject payload = response.getPayload();
        String reply = response.getReply();
        appendDebugTrace("LEGACY_PARSED_ENVELOPE", envelopeTraceJson(response).toString());

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
            case AgentAction.WIFI_ON:
                executeWifiCommand(true);
                if (!TextUtils.isEmpty(reply)) {
                    showAssistantMessage(reply);
                }
                break;
            case AgentAction.WIFI_OFF:
                executeWifiCommand(false);
                if (!TextUtils.isEmpty(reply)) {
                    showAssistantMessage(reply);
                }
                break;
            case AgentAction.CHAT:
            default:
                showAssistantMessage(TextUtils.isEmpty(reply) ? response.getRawText() : reply);
                break;
        }
    }

    private void createTaskFromPayload(JSONObject payload, String reply) {
        if (payload == null) {
            showAssistantMessage(TextUtils.isEmpty(reply)
                    ? "Mình chưa đọc được dữ liệu task để tạo."
                    : reply);
            return;
        }

        String title = payload.optString("title", "").trim();
        String description = payload.optString("description", "").trim();
        long dueDate = payload.optLong("dueDate", 0L);
        int priority = clampPriority(payload.optInt("priority", 1));

        if (title.isEmpty()) {
            showAssistantMessage(TextUtils.isEmpty(reply)
                    ? "Bạn nói rõ tên công việc để mình tạo giúp nhé."
                    : reply);
            return;
        }

        try {
            Task task = new Task();
            task.setTitle(title);
            task.setDescription(description);
            task.setPriority(priority);
            if (dueDate > 0L) {
                task.setDueDate(dueDate);
            }
            TaskDatabase.getInstance(this).taskDao().insert(task);

            showAssistantMessage(TextUtils.isEmpty(reply)
                    ? "Đã tạo công việc \"" + title + "\" thành công."
                    : reply);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create task", e);
            showAssistantMessage("Không thể tạo công việc lúc này. Bạn thử lại nhé.");
        }
    }

    private void completeTaskFromPayload(JSONObject payload, String reply) {
        if (payload == null) {
            showAssistantMessage(TextUtils.isEmpty(reply)
                    ? "Mình chưa nhận được thông tin task cần hoàn thành."
                    : reply);
            return;
        }

        long taskId = payload.optLong("id", -1L);
        String title = payload.optString("title", "").trim();

        try {
            List<Task> allTasks = TaskDatabase.getInstance(this).taskDao().getAllTasksSync();
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
                showAssistantMessage(TextUtils.isEmpty(reply)
                        ? "Mình chưa tìm thấy task phù hợp để hoàn thành."
                        : reply);
                return;
            }

            TaskDatabase.getInstance(this).taskDao()
                    .markTaskAsCompletedWithDate(target.getId(), true, System.currentTimeMillis());

            showAssistantMessage(TextUtils.isEmpty(reply)
                    ? "Đã hoàn thành task: \"" + target.getTitle() + "\"."
                    : reply);
        } catch (Exception e) {
            Log.e(TAG, "Failed to complete task", e);
            showAssistantMessage("Không thể cập nhật task lúc này. Bạn thử lại nhé.");
        }
    }

    private void listTodayTasks(String reply) {
        try {
            long start = startOfDayMillis();
            long end = endOfDayMillis();
            List<Task> allTasks = TaskDatabase.getInstance(this).taskDao().getAllTasksSync();

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
                showAssistantMessage("Hôm nay bạn chưa có task nào.");
            } else {
                showAssistantMessage("Danh sách hôm nay:\n" + builder.toString().trim());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to list today tasks", e);
            showAssistantMessage("Mình chưa lấy được danh sách task hôm nay. Bạn thử lại nhé.");
        }
    }

    private void executeWifiCommand(boolean enable) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return;
        }

        try {
            boolean result = wifiManager.setWifiEnabled(enable);
            String state = enable ? "Bật" : "Tắt";
            showAssistantMessage("Đã " + state + " Wifi: " + result);
        } catch (Exception e) {
            Log.e(TAG, "Wifi toggle failed", e);
            showAssistantMessage("Từ Android 10+, không thể bật/tắt Wifi trực tiếp từ app thường.");
        }
    }

    private String buildAgentPrompt(String userMessage) {
        return AgentPromptContract.buildPrompt(
            AgentPromptContract.FLOATING_ASSISTANT_PROMPT,
            buildRuntimeContext(),
            buildConversationMemoryBlock(),
            userMessage
        );
    }

    private String buildRuntimeContext() {
        StringBuilder context = new StringBuilder();
        long now = System.currentTimeMillis();
        context.append("nowMillis=").append(now).append("\n");
        context.append("timezone=").append(TimeZone.getDefault().getID()).append("\n");

        try {
            long start = startOfDayMillis();
            long end = endOfDayMillis();
            List<Task> todayIncomplete = TaskDatabase.getInstance(this)
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
        if (text == null) {
            return;
        }

        String clean = text.replace('\n', ' ').trim();
        if (clean.isEmpty()) {
            return;
        }

        if (clean.length() > 180) {
            clean = clean.substring(0, 180) + "...";
        }

        conversationMemory.addLast(role + ": " + clean);
        while (conversationMemory.size() > MAX_MEMORY_LINES) {
            conversationMemory.removeFirst();
        }
    }

    private void updateDebugPanelState() {
        if (debugPanelLayout != null) {
            debugPanelLayout.setVisibility(debugPanelVisible ? View.VISIBLE : View.GONE);
        }
        if (debugPanelToggle != null) {
            debugPanelToggle.setText(debugPanelVisible ? "Ẩn Trace" : "Hiện Trace");
        }
    }

    private void clearDebugTrace() {
        synchronized (debugTraceMemory) {
            debugTraceMemory.clear();
        }
        renderDebugTrace();
    }

    private void appendDebugTrace(String stage, String payload) {
        String safeStage = TextUtils.isEmpty(stage) ? "TRACE" : stage.trim();
        String safePayload = sanitizeTracePayload(payload);
        String entry = String.format(Locale.ROOT,
                "[%1$tH:%1$tM:%1$tS] %2$s\n%3$s",
                new Date(),
                safeStage,
                safePayload);

        synchronized (debugTraceMemory) {
            debugTraceMemory.addLast(entry);
            while (debugTraceMemory.size() > MAX_DEBUG_TRACE_LINES) {
                debugTraceMemory.removeFirst();
            }
        }

        if (mainHandler != null) {
            mainHandler.post(this::renderDebugTrace);
        }
    }

    private String sanitizeTracePayload(String payload) {
        if (payload == null) {
            return "(empty)";
        }

        String normalized = payload.replace("\r", "").trim();
        if (normalized.isEmpty()) {
            return "(empty)";
        }

        if (normalized.length() > MAX_DEBUG_TRACE_CHARS) {
            return normalized.substring(0, MAX_DEBUG_TRACE_CHARS) + "\n...(trace truncated)";
        }
        return normalized;
    }

    private void renderDebugTrace() {
        if (debugTraceContent == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        synchronized (debugTraceMemory) {
            if (debugTraceMemory.isEmpty()) {
                builder.append("Chưa có trace.");
            } else {
                for (String line : debugTraceMemory) {
                    builder.append(line).append("\n\n");
                }
            }
        }

        debugTraceContent.setText(builder.toString().trim());
        if (debugTraceScroll != null) {
            debugTraceScroll.post(() -> debugTraceScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private JSONObject envelopeTraceJson(AgentResponseEnvelope envelope) {
        JSONObject json = new JSONObject();
        safePut(json, "action", envelope == null ? "" : envelope.getAction());
        safePut(json, "payload", envelope == null ? new JSONObject() : envelope.getPayload());
        safePut(json, "reply", envelope == null ? "" : envelope.getReply());
        safePut(json, "structured", envelope != null && envelope.isStructured());
        safePut(json, "rawText", envelope == null ? "" : envelope.getRawText());
        return json;
    }

    private void showUserMessage(String message) {
        rememberTurn("user", message);
        mainHandler.post(() -> appendChatMessage(message, true));
    }

    private void showAssistantMessage(String message) {
        rememberTurn("assistant", message);
        mainHandler.post(() -> appendChatMessage(message, false));
    }

    private void appendChatMessage(String message, boolean isUser) {
        if (TextUtils.isEmpty(message)) {
            return;
        }

        if (floatingChatAdapter != null) {
            floatingChatAdapter.addMessage(new ChatMessage(message, isUser));
            if (floatingChatRecyclerView != null) {
                floatingChatRecyclerView.smoothScrollToPosition(
                        Math.max(0, floatingChatAdapter.getItemCount() - 1)
                );
            }
        }
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

    private int clampPriority(int priority) {
        return Math.max(0, Math.min(priority, 3));
    }

    private void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating AI Assistant Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void cleanupStaleOverlayIfAny() {
        synchronized (OVERLAY_LOCK) {
            if (sWindowManager != null) {
                try {
                    if (sChatView != null && sChatView.getParent() != null) {
                        sWindowManager.removeView(sChatView);
                    }
                } catch (Exception ignored) {
                }

                try {
                    if (sBubbleView != null && sBubbleView.getParent() != null) {
                        sWindowManager.removeView(sBubbleView);
                    }
                } catch (Exception ignored) {
                }
            }

            sWindowManager = null;
            sBubbleView = null;
            sChatView = null;
        }
    }

    private void hideAssistantOverlay() {
        if (windowManager == null) {
            return;
        }

        try {
            if (floatingChatView != null && floatingChatView.getParent() != null) {
                windowManager.removeView(floatingChatView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to hide chat overlay", e);
        }

        try {
            if (floatingBubbleView != null && floatingBubbleView.getParent() != null) {
                windowManager.removeView(floatingBubbleView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to hide bubble overlay", e);
        }

        synchronized (OVERLAY_LOCK) {
            sWindowManager = windowManager;
            sBubbleView = floatingBubbleView;
            sChatView = null;
        }
    }

    private void showDailyReview(String reviewText, long[] taskIds) {
        pendingCarryOverTaskIds.clear();
        if (taskIds != null) {
            for (long taskId : taskIds) {
                pendingCarryOverTaskIds.add(taskId);
            }
        }

        if (dailyReviewLayout != null && dailyReviewContent != null) {
            dailyReviewContent.setText(reviewText);
            dailyReviewLayout.setVisibility(View.VISIBLE);
        }
        if (moveUnfinishedButton != null) {
            moveUnfinishedButton.setEnabled(!pendingCarryOverTaskIds.isEmpty());
        }
    }

    private void carryOverUnfinishedTasks() {
        if (pendingCarryOverTaskIds.isEmpty()) {
            Toast.makeText(this, "Không có công việc dang dở để chuyển.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<Long> taskIds = new ArrayList<>(pendingCarryOverTaskIds);
        workerExecutor.execute(() -> {
            Calendar nextDay = Calendar.getInstance();
            nextDay.add(Calendar.DAY_OF_MONTH, 1);
            nextDay.set(Calendar.HOUR_OF_DAY, 9);
            nextDay.set(Calendar.MINUTE, 0);
            nextDay.set(Calendar.SECOND, 0);
            nextDay.set(Calendar.MILLISECOND, 0);
            long newDueDate = nextDay.getTimeInMillis();

            TaskDatabase.getInstance(this).taskDao().updateDueDateForTaskIds(taskIds, newDueDate);
            for (Long taskId : taskIds) {
                Task task = TaskDatabase.getInstance(this).taskDao().getTaskByIdSync(taskId);
                if (task != null && !task.isCompleted()) {
                    ReminderScheduler.scheduleReminder(this, task);
                }
            }

            mainHandler.post(() -> {
                Toast.makeText(this, "Đã chuyển việc dang dở sang ngày mai.", Toast.LENGTH_SHORT).show();
                showAssistantMessage("Mình đã chuyển các việc dang dở sang ngày mai cho bạn.");
                pendingCarryOverTaskIds.clear();
                if (dailyReviewLayout != null) {
                    dailyReviewLayout.setVisibility(View.GONE);
                }
            });
        });
    }

    private void playTingSound() {
        try {
            ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 220);
            mainHandler.postDelayed(toneGenerator::release, 300);
        } catch (Exception e) {
            Log.w(TAG, "Unable to play ting sound", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mainHandler != null) {
            String action = intent != null ? intent.getAction() : null;
            if (ACTION_HIDE_BUBBLE.equals(action)) {
                mainHandler.post(this::hideAssistantOverlay);
            } else if (ACTION_SHOW_BUBBLE.equals(action)) {
                mainHandler.post(this::ensureBubbleVisible);
            } else if (ACTION_SHOW_DAILY_REVIEW.equals(action)) {
                String reviewText = intent != null
                        ? intent.getStringExtra(EXTRA_DAILY_REVIEW_TEXT)
                        : null;
                long[] unfinishedIds = intent != null
                        ? intent.getLongArrayExtra(EXTRA_UNFINISHED_TASK_IDS)
                        : null;

                String safeReview = TextUtils.isEmpty(reviewText)
                        ? "Bạn đã hoàn thành nhiều nỗ lực hôm nay. Hãy nghỉ ngơi và chuẩn bị cho ngày mai nhé."
                        : reviewText;

                mainHandler.post(() -> {
                    playTingSound();
                    ensureBubbleVisible();
                    showDailyReview(safeReview, unfinishedIds);
                    showAssistantMessage(safeReview);
                });
            } else if (ACTION_SHOW_HABIT_NUDGE.equals(action)) {
                String nudgeText = intent != null ? intent.getStringExtra(EXTRA_HABIT_NUDGE_TEXT) : null;
                String safeNudge = TextUtils.isEmpty(nudgeText)
                        ? "Bạn đã nghỉ thói quen vài ngày rồi. Thử quay lại với một bước nhỏ hôm nay nhé."
                        : nudgeText;
                mainHandler.post(() -> {
                    playTingSound();
                    ensureBubbleVisible();
                    showAssistantMessage(safeNudge);
                });
            }
        }
        return START_NOT_STICKY;
    }

    private void ensureBubbleVisible() {
        if (windowManager == null || floatingBubbleView == null) {
            return;
        }

        try {
            if (floatingChatView != null && floatingChatView.getParent() != null) {
                // If chat is already open, keep it open.
                // This avoids race conditions where a delayed SHOW action closes chat immediately.
                floatingBubbleView.setVisibility(View.GONE);
                return;
            }

            if (floatingBubbleView.getParent() == null && bubbleParams != null) {
                bubbleParams.x = getSavedBubbleX();
                bubbleParams.y = getSavedBubbleY();
                windowManager.addView(floatingBubbleView, bubbleParams);
                synchronized (OVERLAY_LOCK) {
                    sWindowManager = windowManager;
                    sBubbleView = floatingBubbleView;
                }
            }
            floatingBubbleView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "Unable to ensure bubble visibility", e);
        }
    }

    private int getSavedBubbleX() {
        return positionPrefs != null
                ? positionPrefs.getInt(KEY_BUBBLE_X, DEFAULT_BUBBLE_X)
                : DEFAULT_BUBBLE_X;
    }

    private int getSavedBubbleY() {
        return positionPrefs != null
                ? positionPrefs.getInt(KEY_BUBBLE_Y, DEFAULT_BUBBLE_Y)
                : DEFAULT_BUBBLE_Y;
    }

    private void saveBubblePosition(int x, int y) {
        if (positionPrefs == null) {
            return;
        }
        positionPrefs.edit()
                .putInt(KEY_BUBBLE_X, x)
                .putInt(KEY_BUBBLE_Y, y)
                .apply();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (bubbleParams != null) {
            saveBubblePosition(bubbleParams.x, bubbleParams.y);
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        isVoiceListening = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacks(hideVoiceStatusRunnable);
        }
        stopVoicePulseAnimation();

        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            workerExecutor.shutdown();
        }

        if (windowManager != null) {
            if (floatingBubbleView != null && floatingBubbleView.getParent() != null) {
                windowManager.removeView(floatingBubbleView);
            }
            if (floatingChatView != null && floatingChatView.getParent() != null) {
                windowManager.removeView(floatingChatView);
            }
        }

        synchronized (OVERLAY_LOCK) {
            sWindowManager = null;
            sBubbleView = null;
            sChatView = null;
        }
    }
}