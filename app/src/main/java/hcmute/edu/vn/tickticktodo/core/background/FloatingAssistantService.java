package hcmute.edu.vn.tickticktodo.core.background;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Log;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.MainActivity;
import hcmute.edu.vn.tickticktodo.agent.AgentAction;
import hcmute.edu.vn.tickticktodo.agent.AgentOrchestrator;
import hcmute.edu.vn.tickticktodo.agent.AgentPromptContract;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseParser;
import hcmute.edu.vn.tickticktodo.core.ai.model.ToolResult;
import hcmute.edu.vn.tickticktodo.core.background.assistant.BubbleWindowManager;
import hcmute.edu.vn.tickticktodo.core.background.assistant.VoiceInputHandler;
import hcmute.edu.vn.tickticktodo.data.dao.ChatHistoryDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.helper.ReminderScheduler;
import hcmute.edu.vn.tickticktodo.model.ChatHistoryMessage;
import hcmute.edu.vn.tickticktodo.model.ChatMessage;
import hcmute.edu.vn.tickticktodo.model.ChatSession;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.ui.chat.AiAssistantActivity;
import hcmute.edu.vn.tickticktodo.ui.chat.ChatAdapter;
import hcmute.edu.vn.tickticktodo.ui.FloatingQuickAddActivity;

public class FloatingAssistantService extends Service {

    public static final String ACTION_SHOW_BUBBLE = "hcmute.edu.vn.tickticktodo.action.SHOW_FLOATING_BUBBLE";
    public static final String ACTION_HIDE_BUBBLE = "hcmute.edu.vn.tickticktodo.action.HIDE_FLOATING_BUBBLE";
    public static final String ACTION_SHOW_DAILY_REVIEW = "hcmute.edu.vn.tickticktodo.action.SHOW_DAILY_REVIEW";
    public static final String ACTION_SHOW_HABIT_NUDGE = "hcmute.edu.vn.tickticktodo.action.SHOW_HABIT_NUDGE";
    public static final String ACTION_REFRESH_THEME = "hcmute.edu.vn.tickticktodo.action.REFRESH_THEME";
    public static final String ACTION_TOGGLE_BUBBLE = "hcmute.edu.vn.tickticktodo.action.TOGGLE_BUBBLE";

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
    private static final String KEY_CHAT_X = "chat_x";
    private static final String KEY_CHAT_Y = "chat_y";
    private static final String KEY_CHAT_ALPHA = "chat_alpha";
    private static final String KEY_VOICE_AUTO_TURN_TAKING = "voice_auto_turn_taking";
    private static final String KEY_VOICE_SPEAK_REPLIES = "voice_speak_replies";
    private static final String KEY_VOICE_BARGE_IN_THRESHOLD = "voice_barge_in_threshold";
    private static final int DEFAULT_BUBBLE_X = 24;
    private static final int DEFAULT_BUBBLE_Y = 220;
    private static final int MAX_DEBUG_TRACE_LINES = 80;
    private static final int MAX_DEBUG_TRACE_CHARS = 2400;
    private static final int MAX_VOICE_AUTO_RETRY = 3;
    private static final long[] VOICE_RETRY_DELAYS_MS = new long[]{700L, 1400L, 2200L};
    private static final long VOICE_START_MIN_INTERVAL_MS = 650L;
    private static final long VOICE_ERROR_DEDUPE_WINDOW_MS = 900L;
    private static final long VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS = 420L;
    private static final float VOICE_BARGE_IN_RMS_THRESHOLD = 7.0f;
    private static final float VOICE_BARGE_IN_RMS_MIN = 4.0f;
    private static final float VOICE_BARGE_IN_RMS_MAX = 15.0f;
    private static final long BUBBLE_LONG_PRESS_TRIGGER_MS = 1000L;
    private static final int BUBBLE_COLLAPSED_SIZE_DP = 90;
    private static final int BUBBLE_EXPANDED_SIZE_DP = 248;
    private static final int BUBBLE_RADIAL_RADIUS_DP = 96;
    private static final int BUBBLE_DRAG_START_THRESHOLD_DP = 6;
    private static final int BUBBLE_DISMISS_MIN_DRAG_DP = 72;
    private static final int BUBBLE_DISMISS_MIN_DOWNWARD_DP = 24;
    private static final float BUBBLE_DISMISS_BOTTOM_ZONE_RATIO = 0.30f;
    private static final float BUBBLE_DISMISS_CIRCLE_DIAMETER_RATIO = 0.30f;
    private static final int BUBBLE_DISMISS_MIN_RADIUS_DP = 24;
    private static final int BUBBLE_DISMISS_TARGET_SIZE_DP = 90;
    private static final int BUBBLE_DISMISS_TARGET_BOTTOM_MARGIN_DP = 24;
    private static final long BUBBLE_EDGE_SNAP_DURATION_MS = 180L;
    private static final long BUBBLE_EXPAND_DURATION_MS = 280L;
    private static final long BUBBLE_COLLAPSE_DURATION_MS = 220L;
    private static final long BUBBLE_ACTION_STAGGER_MS = 26L;
    private static final float MIN_CHAT_ALPHA = 0.35f;
    private static final float MAX_CHAT_ALPHA = 1.0f;
    private static final String CHAT_SOURCE_FLOATING = "floating_assistant";
    private static final String CHAT_SOURCE_FLOATING_TEMP_VOICE = "floating_assistant_temp_voice";
    private static final float[] BUBBLE_OPEN_LEFT_ANGLES = new float[]{120f, 150f, 180f, 210f, 240f};
    private static final float[] BUBBLE_OPEN_RIGHT_ANGLES = new float[]{60f, 30f, 0f, 330f, 300f};

    private enum AssistantState {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING
    }

    private enum OverlayInteractionMode {
        CHAT_ONLY,
        VOICE_ONLY
    }

    private static WindowManager sWindowManager;
    private static View sBubbleView;
    private static View sChatView;

    private WindowManager windowManager;
    private View floatingBubbleView;
    private View floatingChatView;
    private View chatSettingsOverlayView;
    private View bubbleVoiceGlow;
    private ImageView bubbleIcon;
    private ImageView actionChatView;
    private ImageView actionVoiceView;
    private ImageView actionCameraView;
    private ImageView actionTaskView;
    private ImageView actionHomeView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams chatParams;
    private WindowManager.LayoutParams chatSettingsOverlayParams;
    private boolean chatCompatMode;
    private boolean isServiceAlive;
    private boolean hasVoiceAudioFocus;
    private boolean bubbleLongPressTriggered;
    private boolean isRadialMenuExpanded;
    private boolean isRadialAnimating;
    private int bubbleCollapsedSizePx;
    private int bubbleExpandedSizePx;
    private int bubbleRadialRadiusPx;
    private float currentChatAlpha = MAX_CHAT_ALPHA;
    private AssistantState currentState = AssistantState.IDLE;

    private GeminiManager geminiManager;
    private ExecutorService workerExecutor;
    private VoiceInputHandler voiceInputHandler;
    private BubbleWindowManager bubbleWindowManager;
    private TextToSpeech assistantTts;
    private boolean isVoiceListening;
    private boolean isAssistantSpeaking;
    private boolean assistantTtsReady;
    private boolean voiceSessionRequested;
    private boolean autoListenAfterAssistantReply;
    private boolean voiceAutoTurnTakingEnabled = true;
    private boolean voiceSpeakRepliesEnabled = true;
    private float voiceBargeInThreshold = VOICE_BARGE_IN_RMS_THRESHOLD;
    private int voiceAutoRetryCount;
    private int lastSpeechErrorCode = Integer.MIN_VALUE;
    private long lastSpeechErrorAtMs;
    private long lastVoiceStartAtMs;
    private String latestPartialTranscript = "";
    private boolean voiceStopRequestedByUser;
    private Handler mainHandler;
    private AudioManager audioManager;
    private AudioFocusRequest voiceAudioFocusRequest;
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
    private OverlayInteractionMode overlayInteractionMode = OverlayInteractionMode.CHAT_ONLY;
    private EditText floatingInputField;
    private View floatingSendButton;
    private TextView floatingModeText;
    private final ArrayList<Long> pendingCarryOverTaskIds = new ArrayList<>();
    private AgentOrchestrator agentOrchestrator;
    private final Runnable voiceRetryRunnable = () -> startVoiceListening(true);
    private final Runnable autoListenAfterSpeechRunnable = () -> {
        if (!isServiceAlive || !voiceAutoTurnTakingEnabled || !autoListenAfterAssistantReply) {
            return;
        }
        autoListenAfterAssistantReply = false;
        if (!isVoiceListening) {
            startVoiceListening(false);
        }
    };
    private final Runnable bubbleLongPressRunnable = this::triggerBubbleLongPressVoice;
    private final Runnable hideVoiceStatusRunnable = () -> {
        if (!isVoiceListening && voiceStatusText != null) {
            voiceStatusText.setVisibility(View.GONE);
        }
    };

    private final ArrayList<View> radialActionViews = new ArrayList<>();
    private final ArrayDeque<String> conversationMemory = new ArrayDeque<>();
    private final ArrayDeque<String> debugTraceMemory = new ArrayDeque<>();
    private final AgentResponseParser responseParser = new AgentResponseParser();
    private volatile long currentSessionId = -1L;
    private boolean tempVoiceSessionActive;
    private volatile long tempVoiceSessionId = -1L;
    private volatile long previousPersistentSessionId = -1L;
    private int lastUiNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;

    @Override
    public void onCreate() {
        super.onCreate();
        isServiceAlive = true;

        try {
            createNotificationChannel();
            Intent toggleIntent = new Intent(this, FloatingAssistantService.class);
            toggleIntent.setAction(ACTION_TOGGLE_BUBBLE);
            android.app.PendingIntent togglePendingIntent = android.app.PendingIntent.getService(
                    this, 0, toggleIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Trợ lý AI TickTickToDo")
                    .setContentText("Trợ lý nổi đang hoạt động")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .addAction(android.R.drawable.ic_dialog_info, "Bật / Tắt trợ lý", togglePendingIntent)
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
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            workerExecutor = Executors.newSingleThreadExecutor();
            geminiManager = GeminiManager.getInstance();
            agentOrchestrator = new AgentOrchestrator(getApplication());
            positionPrefs = getSharedPreferences(POSITION_PREFS, MODE_PRIVATE);
                bubbleWindowManager = new BubbleWindowManager(
                    this,
                    windowManager,
                    positionPrefs,
                    KEY_BUBBLE_X,
                    KEY_BUBBLE_Y,
                    DEFAULT_BUBBLE_X,
                    DEFAULT_BUBBLE_Y,
                    BUBBLE_COLLAPSED_SIZE_DP,
                    BUBBLE_DISMISS_BOTTOM_ZONE_RATIO,
                    BUBBLE_DISMISS_CIRCLE_DIAMETER_RATIO,
                    BUBBLE_DISMISS_TARGET_SIZE_DP,
                    BUBBLE_DISMISS_TARGET_BOTTOM_MARGIN_DP,
                    BUBBLE_EDGE_SNAP_DURATION_MS
                );
            currentChatAlpha = getSavedChatAlpha();
            loadVoiceBehaviorSettings();
            lastUiNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;

            initAssistantTts();
            initSpeechRecognizer();
            initFloatingBubble();
            initFloatingChat();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize FloatingAssistantService", e);
            isServiceAlive = false;
            stopSelf();
        }
    }

    private void startForegroundSafely(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private boolean isChatAttached() {
        return floatingChatView != null && floatingChatView.getParent() != null;
    }

    private boolean isBubbleAttached() {
        return floatingBubbleView != null && floatingBubbleView.getParent() != null;
    }

    private void safePostMain(Runnable runnable) {
        if (!isServiceAlive || mainHandler == null || runnable == null) {
            return;
        }
        mainHandler.post(() -> {
            if (!isServiceAlive) {
                return;
            }
            try {
                runnable.run();
            } catch (Exception e) {
                Log.w(TAG, "Main-thread runnable failed", e);
            }
        });
    }

    private void safePostMainDelayed(Runnable runnable, long delayMillis) {
        if (!isServiceAlive || mainHandler == null || runnable == null) {
            return;
        }
        mainHandler.postDelayed(() -> {
            if (!isServiceAlive) {
                return;
            }
            try {
                runnable.run();
            } catch (Exception e) {
                Log.w(TAG, "Delayed runnable failed", e);
            }
        }, Math.max(0L, delayMillis));
    }

    private void runWorkerSafely(Runnable task) {
        if (!isServiceAlive || task == null || workerExecutor == null || workerExecutor.isShutdown()) {
            return;
        }
        try {
            workerExecutor.execute(task);
        } catch (Exception e) {
            Log.w(TAG, "Worker task rejected", e);
        }
    }

    private void updateState(AssistantState newState) {
        if (newState == null || currentState == newState) {
            return;
        }
        currentState = newState;
        appendDebugTrace("VOICE_STATE", newState.name());

        safePostMain(() -> {
            switch (newState) {
                case LISTENING:
                    updateVoiceUiState(true, "Đang lắng nghe...");
                    break;
                case THINKING:
                    updateVoiceUiState(false, "Đã nghe xong, đang xử lý...");
                    break;
                case SPEAKING:
                    updateVoiceUiState(false, "Trợ lý đang phản hồi...");
                    break;
                case IDLE:
                default:
                    if (!isVoiceListening && !isAssistantSpeaking) {
                        updateVoiceUiState(false, null);
                    }
                    break;
            }
        });
    }

    private void loadVoiceBehaviorSettings() {
        if (positionPrefs == null) {
            return;
        }
        voiceAutoTurnTakingEnabled = positionPrefs.getBoolean(KEY_VOICE_AUTO_TURN_TAKING, true);
        voiceSpeakRepliesEnabled = positionPrefs.getBoolean(KEY_VOICE_SPEAK_REPLIES, true);
        float storedThreshold = positionPrefs.getFloat(KEY_VOICE_BARGE_IN_THRESHOLD, VOICE_BARGE_IN_RMS_THRESHOLD);
        voiceBargeInThreshold = clampVoiceBargeInThreshold(storedThreshold);
    }

    private void setVoiceAutoTurnTakingEnabled(boolean enabled) {
        voiceAutoTurnTakingEnabled = enabled;
        if (!enabled) {
            autoListenAfterAssistantReply = false;
            if (mainHandler != null) {
                mainHandler.removeCallbacks(autoListenAfterSpeechRunnable);
            }
        }
        if (positionPrefs != null) {
            positionPrefs.edit().putBoolean(KEY_VOICE_AUTO_TURN_TAKING, enabled).apply();
        }
    }

    private void setVoiceSpeakRepliesEnabled(boolean enabled) {
        voiceSpeakRepliesEnabled = enabled;
        if (!enabled) {
            stopAssistantSpeech(false);
        }
        if (positionPrefs != null) {
            positionPrefs.edit().putBoolean(KEY_VOICE_SPEAK_REPLIES, enabled).apply();
        }
    }

    private float clampVoiceBargeInThreshold(float value) {
        return Math.max(VOICE_BARGE_IN_RMS_MIN, Math.min(VOICE_BARGE_IN_RMS_MAX, value));
    }

    private float progressToBargeInThreshold(int progress) {
        float safeProgress = Math.max(0, Math.min(100, progress));
        return VOICE_BARGE_IN_RMS_MIN
                + (safeProgress / 100f) * (VOICE_BARGE_IN_RMS_MAX - VOICE_BARGE_IN_RMS_MIN);
    }

    private int bargeInThresholdToProgress(float threshold) {
        float safeThreshold = clampVoiceBargeInThreshold(threshold);
        float normalized = (safeThreshold - VOICE_BARGE_IN_RMS_MIN)
                / (VOICE_BARGE_IN_RMS_MAX - VOICE_BARGE_IN_RMS_MIN);
        return Math.round(normalized * 100f);
    }

    private String formatBargeInThreshold(float threshold) {
        return String.format(Locale.ROOT, "%.1f dB", threshold);
    }

    private void setVoiceBargeInThreshold(float threshold) {
        voiceBargeInThreshold = clampVoiceBargeInThreshold(threshold);
        if (positionPrefs != null) {
            positionPrefs.edit().putFloat(KEY_VOICE_BARGE_IN_THRESHOLD, voiceBargeInThreshold).apply();
        }
        appendDebugTrace("VOICE_BARGE_THRESHOLD", formatBargeInThreshold(voiceBargeInThreshold));
    }

    private void initAssistantTts() {
        try {
            if (assistantTts != null) {
                assistantTts.stop();
                assistantTts.shutdown();
            }
        } catch (Exception ignored) {
        }

        assistantTtsReady = false;
        assistantTts = new TextToSpeech(getApplicationContext(), status -> {
            if (!isServiceAlive || assistantTts == null) {
                return;
            }
            if (status != TextToSpeech.SUCCESS) {
                appendDebugTrace("VOICE_TTS_INIT", "failed status=" + status);
                return;
            }

            int lang = assistantTts.setLanguage(new Locale("vi", "VN"));
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                appendDebugTrace("VOICE_TTS_LANG", "vi-VN unavailable, fallback default locale.");
                assistantTts.setLanguage(Locale.getDefault());
            }

            assistantTts.setSpeechRate(1.0f);
            assistantTts.setPitch(1.0f);
            assistantTtsReady = true;
            appendDebugTrace("VOICE_TTS_INIT", "ready");
        });

        if (assistantTts != null) {
            assistantTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    isAssistantSpeaking = true;
                    updateState(AssistantState.SPEAKING);
                }

                @Override
                public void onDone(String utteranceId) {
                    safePostMain(() -> {
                        isAssistantSpeaking = false;
                        updateState(AssistantState.IDLE);
                        if (voiceAutoTurnTakingEnabled && autoListenAfterAssistantReply) {
                            safePostMainDelayed(autoListenAfterSpeechRunnable, VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS);
                        }
                    });
                }

                @Override
                public void onError(String utteranceId) {
                    safePostMain(() -> {
                        isAssistantSpeaking = false;
                        updateState(AssistantState.IDLE);
                        appendDebugTrace("VOICE_TTS_ERROR", "utteranceId=" + utteranceId);
                        if (voiceAutoTurnTakingEnabled && autoListenAfterAssistantReply) {
                            safePostMainDelayed(autoListenAfterSpeechRunnable, VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS);
                        }
                    });
                }
            });
        }
    }

    private void shutdownAssistantTts() {
        try {
            if (assistantTts != null) {
                assistantTts.stop();
                assistantTts.shutdown();
            }
        } catch (Exception ignored) {
        }
        assistantTts = null;
        assistantTtsReady = false;
        isAssistantSpeaking = false;
    }

    private void stopAssistantSpeech(boolean keepAutoListenContinuation) {
        if (!keepAutoListenContinuation) {
            autoListenAfterAssistantReply = false;
            if (mainHandler != null) {
                mainHandler.removeCallbacks(autoListenAfterSpeechRunnable);
            }
        }
        isAssistantSpeaking = false;
        try {
            if (assistantTts != null) {
                assistantTts.stop();
            }
        } catch (Exception ignored) {
        }
    }

    private void speakAssistantMessage(String message, boolean shouldAutoContinueListening) {
        speakAssistantMessage(message, TextToSpeech.QUEUE_FLUSH, shouldAutoContinueListening);
    }
    
    public void speakAssistantMessage(String message, int queueMode, boolean shouldAutoContinueListening) {
        if (overlayInteractionMode != OverlayInteractionMode.VOICE_ONLY) {
            autoListenAfterAssistantReply = false;
            return;
        }

        if (TextUtils.isEmpty(message)) {
            if (shouldAutoContinueListening) {
                autoListenAfterAssistantReply = true;
                safePostMainDelayed(autoListenAfterSpeechRunnable, VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS);
            }
            return;
        }

        autoListenAfterAssistantReply = shouldAutoContinueListening;
        if (!voiceSpeakRepliesEnabled) {
            if (shouldAutoContinueListening) {
                safePostMainDelayed(autoListenAfterSpeechRunnable, VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS);
            }
            return;
        }

        if (assistantTts == null || !assistantTtsReady) {
            appendDebugTrace("VOICE_TTS_SKIP", "TTS not ready.");
            if (shouldAutoContinueListening) {
                safePostMainDelayed(autoListenAfterSpeechRunnable, VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS);
            }
            return;
        }

        updateState(AssistantState.SPEAKING);
        String utteranceId = "floating-tts-" + SystemClock.elapsedRealtime();
        appendDebugTrace("VOICE_TTS_SPEAK", abbreviateForStatus(message));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                assistantTts.speak(message, queueMode, null, utteranceId);
            } else {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                assistantTts.speak(message, queueMode, params);
            }
        } catch (Exception e) {
            isAssistantSpeaking = false;
            appendDebugTrace("VOICE_TTS_EXCEPTION", e.getMessage());
            if (shouldAutoContinueListening) {
                safePostMainDelayed(autoListenAfterSpeechRunnable, VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS);
            }
        }
    }

    private boolean requestVoiceAudioFocus() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) {
            hasVoiceAudioFocus = false;
            return false;
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (voiceAudioFocusRequest == null) {
                voiceAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(focusChange -> {
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                if (assistantTts != null) assistantTts.stop();
                                updateState(AssistantState.IDLE);
                            }
                        })
                        .build();
            }
            int result = audioManager.requestAudioFocus(voiceAudioFocusRequest);
            hasVoiceAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            appendDebugTrace("VOICE_AUDIO_FOCUS", hasVoiceAudioFocus ? "granted" : "denied");
            return hasVoiceAudioFocus;
        } else {
            int result = audioManager.requestAudioFocus(
                    focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            if (assistantTts != null) assistantTts.stop();
                            updateState(AssistantState.IDLE);
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            );
            hasVoiceAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            appendDebugTrace("VOICE_AUDIO_FOCUS", hasVoiceAudioFocus ? "granted" : "denied");
            return hasVoiceAudioFocus;
        }
    }

    private void releaseVoiceAudioFocus() {
        if (audioManager == null || !hasVoiceAudioFocus) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && voiceAudioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(voiceAudioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception ignored) {
        }
        hasVoiceAudioFocus = false;
    }

    private void initSpeechRecognizer() {
        if (voiceInputHandler == null) {
            voiceInputHandler = new VoiceInputHandler(this, TAG, new VoiceInputHandler.Callback() {
                @Override
                public void onReadyForSpeech(android.os.Bundle params) {
                    handleVoiceReadyForSpeech();
                }

                @Override
                public void onBeginningOfSpeech() {
                    handleVoiceBeginningOfSpeech();
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    handleVoiceRmsChanged(rmsdB);
                }

                @Override
                public void onEndOfSpeech() {
                    handleVoiceEndOfSpeech();
                }

                @Override
                public void onError(int error) {
                    handleVoiceRecognitionError(error);
                }

                @Override
                public void onResults(ArrayList<String> results) {
                    handleVoiceResults(results);
                }

                @Override
                public void onPartialResults(ArrayList<String> partialResults) {
                    handleVoicePartialResults(partialResults);
                }
            });
        }

        if (!voiceInputHandler.initialize()) {
            appendDebugTrace("VOICE_INIT", "Speech recognition is not available.");
        }
    }

    private void handleVoiceReadyForSpeech() {
        isVoiceListening = true;
        voiceStopRequestedByUser = false;
        updateState(AssistantState.LISTENING);
        updateVoiceUiState(true, "Đang lắng nghe...");
        appendDebugTrace("VOICE_READY", "SpeechRecognizer ready.");
    }

    private void handleVoiceBeginningOfSpeech() {
        isVoiceListening = true;
        voiceStopRequestedByUser = false;
        updateState(AssistantState.LISTENING);
        updateVoiceUiState(true, "Đang lắng nghe...");
        appendDebugTrace("VOICE_BEGIN", "Detected beginning of speech.");
    }

    private void handleVoiceRmsChanged(float rmsdB) {
        if (voicePulseIndicator != null && voicePulseIndicator.getVisibility() == View.VISIBLE) {
            float alpha = Math.max(0.2f, Math.min(1.0f, rmsdB / 10.0f));
            voicePulseIndicator.setAlpha(alpha);
        }

        if (currentState == AssistantState.SPEAKING
                && isAssistantSpeaking
                && rmsdB > voiceBargeInThreshold) {
            Log.d(TAG, "Voice interruption detected. Stop TTS and resume listening.");
            stopAssistantSpeech(true);
            updateState(AssistantState.LISTENING);
        }
    }

    private void handleVoiceEndOfSpeech() {
        isVoiceListening = false;
        releaseVoiceAudioFocus();
        updateState(AssistantState.THINKING);
        updateVoiceUiState(false, "Đã nghe xong, đang xử lý...");
        appendDebugTrace("VOICE_END", "End of speech captured.");
    }

    private void handleVoiceRecognitionError(int error) {
        isVoiceListening = false;
        releaseVoiceAudioFocus();
        boolean userStopped = voiceStopRequestedByUser && error == SpeechRecognizer.ERROR_CLIENT;
        voiceStopRequestedByUser = false;

        if (userStopped) {
            voiceSessionRequested = false;
            autoListenAfterAssistantReply = false;
            cancelVoiceRetry();
            updateState(AssistantState.IDLE);
            updateVoiceUiState(false, "Đã dừng nghe.");
            appendDebugTrace("VOICE_STOPPED", "Stopped by user.");
            return;
        }

        if (isDuplicateSpeechError(error)) {
            appendDebugTrace("VOICE_ERROR_DEDUP", "Skip duplicate speech error=" + error);
            return;
        }

        if (error == SpeechRecognizer.ERROR_CLIENT && !voiceSessionRequested) {
            appendDebugTrace("VOICE_ERROR_CLIENT_IGNORED", "No active voice session requested.");
            updateVoiceUiState(false, "Voice đã dừng.");
            return;
        }

        Log.e(TAG, "Speech error: " + error);
        appendDebugTrace("VOICE_ERROR", "code=" + error + ", message=" + mapSpeechErrorMessage(error));

        if (shouldUsePartialFallback(error) && applyRecognizedVoiceText(latestPartialTranscript, true)) {
            return;
        }

        if (shouldRetryVoice(error)) {
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    || error == SpeechRecognizer.ERROR_CLIENT) {
                resetSpeechRecognizer("onError=" + error);
            }
            scheduleVoiceRetry(error);
            return;
        }

        voiceSessionRequested = false;
        updateState(AssistantState.IDLE);
        updateVoiceUiState(false, mapSpeechErrorMessage(error));
        safePostMain(() ->
                Toast.makeText(FloatingAssistantService.this, "Loi thu am: " + error, Toast.LENGTH_SHORT).show());
    }

    private void handleVoiceResults(ArrayList<String> data) {
        isVoiceListening = false;
        releaseVoiceAudioFocus();
        voiceStopRequestedByUser = false;
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

    private void handleVoicePartialResults(ArrayList<String> partial) {
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

    private void initFloatingBubble() {
        floatingBubbleView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);
        bubbleVoiceGlow = floatingBubbleView.findViewById(R.id.voice_long_press_glow);
        bubbleIcon = floatingBubbleView.findViewById(R.id.iv_floating_bubble);
        actionChatView = floatingBubbleView.findViewById(R.id.iv_action_chat);
        actionVoiceView = floatingBubbleView.findViewById(R.id.iv_action_voice);
        actionCameraView = floatingBubbleView.findViewById(R.id.iv_action_camera);
        actionTaskView = floatingBubbleView.findViewById(R.id.iv_action_task);
        actionHomeView = floatingBubbleView.findViewById(R.id.iv_action_home);

        bubbleCollapsedSizePx = dpToPx(BUBBLE_COLLAPSED_SIZE_DP);
        bubbleExpandedSizePx = dpToPx(BUBBLE_EXPANDED_SIZE_DP);
        bubbleRadialRadiusPx = dpToPx(BUBBLE_RADIAL_RADIUS_DP);
        isRadialMenuExpanded = false;
        isRadialAnimating = false;

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        bubbleParams = new WindowManager.LayoutParams(
            bubbleCollapsedSizePx,
            bubbleCollapsedSizePx,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = clampBubbleX(getSavedBubbleX(), bubbleCollapsedSizePx);
        bubbleParams.y = clampBubbleY(getSavedBubbleY(), bubbleCollapsedSizePx);

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

        ImageView ivBubble = bubbleIcon;
        if (ivBubble == null) {
            Log.e(TAG, "Floating bubble view is missing iv_floating_bubble");
            stopSelf();
            return;
        }

        radialActionViews.clear();
        if (actionChatView != null) {
            radialActionViews.add(actionChatView);
            actionChatView.setOnClickListener(v -> handleChatAction());
        }
        if (actionVoiceView != null) {
            radialActionViews.add(actionVoiceView);
            actionVoiceView.setOnClickListener(v -> handleVoiceAction());
        }
        if (actionCameraView != null) {
            radialActionViews.add(actionCameraView);
            actionCameraView.setOnClickListener(v -> handleCameraAction());
        }
        if (actionTaskView != null) {
            radialActionViews.add(actionTaskView);
            actionTaskView.setOnClickListener(v -> handleTaskAction());
        }
        if (actionHomeView != null) {
            radialActionViews.add(actionHomeView);
            actionHomeView.setOnClickListener(v -> handleHomeAction());
        }
        resetRadialActionViews();

        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        final int dragStartThreshold = Math.max(touchSlop * 2, dpToPx(BUBBLE_DRAG_START_THRESHOLD_DP));
        final int dismissMinDragDistancePx = dpToPx(BUBBLE_DISMISS_MIN_DRAG_DP);
        final int dismissMinDownwardPx = dpToPx(BUBBLE_DISMISS_MIN_DOWNWARD_DP);
        final float clickThreshold = Math.max(touchSlop * 4f, 48f);
        View.OnTouchListener bubbleTouchListener = new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private float downTouchX;
            private float downTouchY;
            private boolean isDragging;
            private boolean dismissTargetHovered;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isRadialAnimating) {
                            return true;
                        }
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        downTouchX = initialTouchX;
                        downTouchY = initialTouchY;
                        bubbleLongPressTriggered = false;
                        isDragging = false;
                        dismissTargetHovered = false;
                        if (mainHandler != null) {
                            mainHandler.removeCallbacks(bubbleLongPressRunnable);
                            mainHandler.postDelayed(bubbleLongPressRunnable, BUBBLE_LONG_PRESS_TRIGGER_MS);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        if (!isDragging && Math.hypot(deltaX, deltaY) > dragStartThreshold) {
                            isDragging = true;
                            collapseRadialMenu(false);
                            if (mainHandler != null) {
                                mainHandler.removeCallbacks(bubbleLongPressRunnable);
                            }
                        }
                        if (isDragging) {
                            deltaX = (int) (event.getRawX() - initialTouchX);
                            deltaY = (int) (event.getRawY() - initialTouchY);
                            int screenWidth = getResources().getDisplayMetrics().widthPixels;
                            int screenHeight = getResources().getDisplayMetrics().heightPixels;
                            int bubbleWidth = Math.max(1, bubbleParams.width);
                            int bubbleHeight = Math.max(1, bubbleParams.height);

                            int minX = 0;
                            int maxX = Math.max(0, screenWidth - bubbleWidth);
                            int minY = 0;
                            int maxY = Math.max(0, screenHeight - bubbleHeight);

                            bubbleParams.x = Math.max(minX, Math.min(initialX + deltaX, maxX));
                            bubbleParams.y = Math.max(minY, Math.min(initialY + deltaY, maxY));
                            try {
                                windowManager.updateViewLayout(floatingBubbleView, bubbleParams);
                            } catch (Exception e) {
                                Log.w(TAG, "Unable to update bubble position", e);
                            }

                            float totalDragDistance = (float) Math.hypot(
                                    event.getRawX() - downTouchX,
                                    event.getRawY() - downTouchY
                            );
                            float downwardDelta = event.getRawY() - downTouchY;
                            if (isBubbleInDismissBottomBand()) {
                                showDismissTarget();
                                dismissTargetHovered = isBubbleOverDismissTarget();
                                updateDismissTargetHighlight(dismissTargetHovered);
                            } else {
                                dismissTargetHovered = false;
                                hideDismissTarget();
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (mainHandler != null) {
                            mainHandler.removeCallbacks(bubbleLongPressRunnable);
                        }
                        dismissTargetHovered = false;
                        hideDismissTarget();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (mainHandler != null) {
                            mainHandler.removeCallbacks(bubbleLongPressRunnable);
                        }
                        float upDeltaX = Math.abs(event.getRawX() - initialTouchX);
                        float upDeltaY = Math.abs(event.getRawY() - initialTouchY);
                        if (bubbleLongPressTriggered) {
                            bubbleLongPressTriggered = false;
                            hideDismissTarget();
                            return true;
                        }

                        if (isDragging) {
                            float downwardDelta = event.getRawY() - downTouchY;
                            boolean isFlingDown = downwardDelta > dpToPx(120) && (downwardDelta / Math.max(1, event.getEventTime() - event.getDownTime())) > 1.2f;
                            boolean shouldHide = dismissTargetHovered
                                    || (isBubbleInDismissBottomBand() && isBubbleOverDismissTarget())
                                    || isFlingDown;
                            dismissTargetHovered = false;
                            hideDismissTarget();
                            if (shouldHide) {
                                hideAssistantOverlay();
                                Toast.makeText(FloatingAssistantService.this,
                                        R.string.floating_chat_hidden_toast,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                snapBubbleToNearestHorizontalEdge(true);
                            }
                            return true;
                        }

                        if (upDeltaX < clickThreshold && upDeltaY < clickThreshold) {
                            toggleRadialMenu();
                        } else {
                            saveBubblePosition(bubbleParams.x, bubbleParams.y);
                        }
                        hideDismissTarget();
                        return true;
                    default:
                        return false;
                }
            }
        };

        ivBubble.setClickable(true);
        ivBubble.setOnTouchListener(bubbleTouchListener);
        floatingBubbleView.setClickable(false);
        floatingBubbleView.setOnTouchListener(null);
    }

    private void toggleRadialMenu() {
        if (isRadialAnimating || bubbleParams == null || floatingBubbleView == null || !isBubbleAttached()) {
            return;
        }
        if (isRadialMenuExpanded) {
            collapseRadialMenu(true);
        } else {
            expandRadialMenu();
        }
    }

    private void handleChatAction() {
        long delay = (isRadialMenuExpanded || isRadialAnimating) ? BUBBLE_COLLAPSE_DURATION_MS : 0L;
        collapseRadialMenu(true);
        safePostMainDelayed(() -> {
            openChatView();
            enterChatOnlyMode(true, true);
        }, delay);
    }

    private void handleVoiceAction() {
        long delay = (isRadialMenuExpanded || isRadialAnimating) ? BUBBLE_COLLAPSE_DURATION_MS : 0L;
        collapseRadialMenu(true);
        safePostMainDelayed(() -> {
            openChatView();
            enterVoiceOnlyMode();
            activateTemporaryVoiceSession();
        }, delay);
    }

    private void handleCameraAction() {
        long delay = (isRadialMenuExpanded || isRadialAnimating) ? BUBBLE_COLLAPSE_DURATION_MS : 0L;
        collapseRadialMenu(true);
        safePostMainDelayed(() -> Toast.makeText(
                this,
                R.string.floating_action_camera_todo,
                Toast.LENGTH_SHORT
        ).show(), delay);
    }

    private void handleTaskAction() {
        long delay = (isRadialMenuExpanded || isRadialAnimating) ? BUBBLE_COLLAPSE_DURATION_MS : 0L;
        collapseRadialMenu(true);
        safePostMainDelayed(this::launchFloatingQuickAddPopup, delay);
    }

    private void handleHomeAction() {
        long delay = (isRadialMenuExpanded || isRadialAnimating) ? BUBBLE_COLLAPSE_DURATION_MS : 0L;
        collapseRadialMenu(true);
        safePostMainDelayed(() -> launchMainActivity(false), delay);
    }

    private void launchMainActivity(boolean openAddTaskSheet) {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (openAddTaskSheet) {
                intent.putExtra(MainActivity.EXTRA_OPEN_ADD_TASK_SHEET, true);
            }
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Unable to launch MainActivity from radial action", e);
        }
    }

    private void launchFloatingQuickAddPopup() {
        try {
            Intent intent = new Intent(this, FloatingQuickAddActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Unable to launch floating quick add popup", e);
            launchMainActivity(true);
        }
    }

    private void expandRadialMenu() {
        if (isRadialMenuExpanded || isRadialAnimating || bubbleParams == null || floatingBubbleView == null || !isBubbleAttached()) {
            return;
        }

        isRadialAnimating = true;
        resizeBubbleWindowKeepingCenter(bubbleExpandedSizePx);

        if (bubbleIcon != null) {
            bubbleIcon.animate().rotationBy(360f).setDuration(BUBBLE_EXPAND_DURATION_MS).start();
        }

        if (radialActionViews.isEmpty()) {
            isRadialMenuExpanded = true;
            isRadialAnimating = false;
            return;
        }

        float[] targetAngles = shouldOpenRadialToLeft()
                ? BUBBLE_OPEN_LEFT_ANGLES
                : BUBBLE_OPEN_RIGHT_ANGLES;

        for (int i = 0; i < radialActionViews.size(); i++) {
            View actionView = radialActionViews.get(i);
            float angle = i < targetAngles.length ? targetAngles[i] : 180f;
            float radian = (float) Math.toRadians(angle);
            float targetX = (float) (Math.cos(radian) * bubbleRadialRadiusPx);
            float targetY = (float) (-Math.sin(radian) * bubbleRadialRadiusPx);

            actionView.animate().cancel();
            actionView.setVisibility(View.VISIBLE);
            actionView.setEnabled(true);
            actionView.setAlpha(0f);
            actionView.setScaleX(0.6f);
            actionView.setScaleY(0.6f);
            actionView.setTranslationX(0f);
            actionView.setTranslationY(0f);

            int index = i;
            actionView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationX(targetX)
                    .translationY(targetY)
                    .setStartDelay(BUBBLE_ACTION_STAGGER_MS * i)
                    .setDuration(BUBBLE_EXPAND_DURATION_MS)
                    .withEndAction(() -> {
                        if (index == radialActionViews.size() - 1) {
                            isRadialAnimating = false;
                            isRadialMenuExpanded = true;
                        }
                    })
                    .start();
        }
    }

    private boolean shouldOpenRadialToLeft() {
        if (bubbleParams == null) {
            return true;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int currentWidth = bubbleParams.width > 0 ? bubbleParams.width : bubbleCollapsedSizePx;
        int bubbleCenterX = bubbleParams.x + currentWidth / 2;
        return bubbleCenterX >= (screenWidth / 2);
    }

    private void collapseRadialMenu(boolean animated) {
        if (bubbleParams == null || floatingBubbleView == null) {
            return;
        }
        if (!isRadialMenuExpanded && !isRadialAnimating && bubbleParams.width == bubbleCollapsedSizePx) {
            return;
        }

        if (!animated || radialActionViews.isEmpty()) {
            finalizeRadialCollapse();
            return;
        }

        isRadialAnimating = true;
        for (int i = 0; i < radialActionViews.size(); i++) {
            View actionView = radialActionViews.get(i);
            int index = i;
            actionView.animate().cancel();
            actionView.animate()
                    .alpha(0f)
                    .scaleX(0.6f)
                    .scaleY(0.6f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(BUBBLE_COLLAPSE_DURATION_MS)
                    .withEndAction(() -> {
                        if (index == radialActionViews.size() - 1) {
                            finalizeRadialCollapse();
                        }
                    })
                    .start();
        }
    }

    private void finalizeRadialCollapse() {
        resetRadialActionViews();
        resizeBubbleWindowKeepingCenter(bubbleCollapsedSizePx);
        if (bubbleIcon != null) {
            bubbleIcon.animate().rotation(0f).setDuration(120L).start();
        }
        isRadialMenuExpanded = false;
        isRadialAnimating = false;
    }

    private void resetRadialActionViews() {
        for (View actionView : radialActionViews) {
            actionView.animate().cancel();
            actionView.setEnabled(false);
            actionView.setAlpha(0f);
            actionView.setScaleX(0.6f);
            actionView.setScaleY(0.6f);
            actionView.setTranslationX(0f);
            actionView.setTranslationY(0f);
            actionView.setVisibility(View.INVISIBLE);
        }
    }

    private void resizeBubbleWindowKeepingCenter(int newSizePx) {
        if (newSizePx <= 0 || bubbleParams == null || windowManager == null || floatingBubbleView == null) {
            return;
        }

        int currentWidth = bubbleParams.width > 0 ? bubbleParams.width : bubbleCollapsedSizePx;
        int currentHeight = bubbleParams.height > 0 ? bubbleParams.height : bubbleCollapsedSizePx;
        int centerX = bubbleParams.x + currentWidth / 2;
        int centerY = bubbleParams.y + currentHeight / 2;

        bubbleParams.width = newSizePx;
        bubbleParams.height = newSizePx;
        bubbleParams.x = centerX - newSizePx / 2;
        bubbleParams.y = centerY - newSizePx / 2;

        try {
            if (floatingBubbleView.getParent() != null) {
                windowManager.updateViewLayout(floatingBubbleView, bubbleParams);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to resize bubble window", e);
        }
    }

    private void activateTemporaryVoiceSession() {
        runWorkerSafely(() -> {
            try {
                ChatHistoryDao dao = TaskDatabase.getInstance(this).chatHistoryDao();
                long previousSessionId = currentSessionId;

                if (tempVoiceSessionActive && tempVoiceSessionId > 0L) {
                    dao.deleteSessionById(tempVoiceSessionId);
                }

                long newTempSessionId = createHistorySessionSync(dao, CHAT_SOURCE_FLOATING_TEMP_VOICE, "Voice tam thoi");
                tempVoiceSessionActive = true;
                tempVoiceSessionId = newTempSessionId;
                previousPersistentSessionId = (previousSessionId > 0L && previousSessionId != newTempSessionId)
                        ? previousSessionId
                        : -1L;
                currentSessionId = newTempSessionId;

                safePostMain(() -> {
                    conversationMemory.clear();
                    if (floatingChatAdapter != null) {
                        floatingChatAdapter.clearMessages();
                    }
                    showAssistantMessage("Da mo phien voice tam thoi. Dong popup de tu xoa phien nay.", false, false);
                    startVoiceListening(false);
                });
            } catch (Exception e) {
                Log.w(TAG, "Unable to activate temporary voice session", e);
                safePostMain(() -> Toast.makeText(
                        this,
                        "Khong the bat phien voice tam thoi luc nay.",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private void cleanupTemporaryVoiceSessionIfNeeded() {
        if (!tempVoiceSessionActive || tempVoiceSessionId <= 0L) {
            return;
        }

        long deleteSessionId = tempVoiceSessionId;
        long restoreSessionId = previousPersistentSessionId;
        tempVoiceSessionActive = false;
        tempVoiceSessionId = -1L;
        previousPersistentSessionId = -1L;
        currentSessionId = restoreSessionId > 0L ? restoreSessionId : -1L;
        conversationMemory.clear();

        runWorkerSafely(() -> {
            try {
                ChatHistoryDao dao = TaskDatabase.getInstance(this).chatHistoryDao();
                dao.deleteSessionById(deleteSessionId);

                if (restoreSessionId > 0L && dao.getSessionByIdSync(restoreSessionId) != null) {
                    currentSessionId = restoreSessionId;
                } else {
                    ChatSession latest = dao.getLatestSessionSync();
                    currentSessionId = latest != null ? latest.id : -1L;
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to cleanup temporary voice session", e);
            }
        });
    }

    private long createHistorySessionSync(ChatHistoryDao dao, String source, String title) {
        long now = System.currentTimeMillis();
        ChatSession session = new ChatSession();
        session.title = title == null ? "" : title;
        session.source = source;
        session.lastMessage = "";
        session.createdAt = now;
        session.updatedAt = now;
        return dao.insertSession(session);
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

        chatParams = createChatLayoutParams(false);
        chatCompatMode = false;

        View chatHeader = floatingChatView.findViewById(R.id.floating_chat_header);
        View btnClose = floatingChatView.findViewById(R.id.btn_close_chat);
        View btnSettings = floatingChatView.findViewById(R.id.btn_chat_settings);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> closeChatView());
        }
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showChatSettingsOverlay());
        }

        EditText etInput = floatingChatView.findViewById(R.id.et_message_floating);
        View btnSend = floatingChatView.findViewById(R.id.btn_send_floating);
        View btnMic = floatingChatView.findViewById(R.id.btn_mic_floating);
        floatingModeText = floatingChatView.findViewById(R.id.tv_floating_chat_mode);
        dailyReviewLayout = floatingChatView.findViewById(R.id.layout_daily_review_action);
        dailyReviewContent = floatingChatView.findViewById(R.id.tv_daily_review_content);
        moveUnfinishedButton = floatingChatView.findViewById(R.id.btn_move_unfinished_tasks);
        debugPanelLayout = floatingChatView.findViewById(R.id.layout_agent_debug_panel);
        debugPanelToggle = floatingChatView.findViewById(R.id.btn_toggle_debug_panel);
        debugTraceContent = floatingChatView.findViewById(R.id.tv_agent_debug_trace);
        debugTraceScroll = floatingChatView.findViewById(R.id.sv_agent_debug_trace);
        debugTraceClear = floatingChatView.findViewById(R.id.btn_clear_debug_trace);
        floatingInputField = etInput;
        floatingSendButton = btnSend;
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

        attachChatHeaderDrag(chatHeader, new View[]{btnClose, btnSettings, debugPanelToggle});

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
                if (overlayInteractionMode != OverlayInteractionMode.CHAT_ONLY) {
                    Toast.makeText(FloatingAssistantService.this,
                            R.string.floating_chat_voice_mode_no_typing,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    String msg = etInput.getText().toString().trim();
                    if (!msg.isEmpty()) {
                        etInput.setText("");
                        sendToGemini(msg);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Send button failed", e);
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
            btnMic.setOnClickListener(v -> {
                if (overlayInteractionMode != OverlayInteractionMode.VOICE_ONLY) {
                    Toast.makeText(FloatingAssistantService.this,
                            R.string.floating_chat_chat_mode_no_voice,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    toggleVoiceListening();
                } catch (Exception e) {
                    Log.w(TAG, "Mic button failed", e);
                }
            });
        }

        applyOverlayInteractionModeUi(false);
        applyChatAlpha(currentChatAlpha, false);
        updateVoiceUiState(false, null);
        runWorkerSafely(this::restoreFloatingHistory);
        appendDebugTrace("DEBUG_PANEL", "Agent trace panel is ready. Toggle 'Hiện Trace' to watch tool-call flow.");
    }

    private void openChatView() {
        if (windowManager == null || floatingBubbleView == null || floatingChatView == null) {
            return;
        }

        collapseRadialMenu(false);

        if (floatingChatView.getParent() != null) {
            return;
        }

        saveBubblePosition(bubbleParams != null ? bubbleParams.x : DEFAULT_BUBBLE_X,
                bubbleParams != null ? bubbleParams.y : DEFAULT_BUBBLE_Y);
        hideDismissTarget();

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
            applyChatAlpha(currentChatAlpha, false);
            applyOverlayInteractionModeUi(false);
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
                    applyChatAlpha(currentChatAlpha, false);
                    applyOverlayInteractionModeUi(false);
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

    private void enterChatOnlyMode(boolean cleanupTemporarySession, boolean restoreHistoryAfterSwitch) {
        overlayInteractionMode = OverlayInteractionMode.CHAT_ONLY;
        stopVoiceCaptureForOverlay();
        if (cleanupTemporarySession) {
            cleanupTemporaryVoiceSessionIfNeeded();
        }
        applyOverlayInteractionModeUi(true);

        if (restoreHistoryAfterSwitch) {
            runWorkerSafely(this::restoreFloatingHistory);
        }
    }

    private void enterVoiceOnlyMode() {
        overlayInteractionMode = OverlayInteractionMode.VOICE_ONLY;
        applyOverlayInteractionModeUi(true);
    }

    private void applyOverlayInteractionModeUi(boolean updateStatusChip) {
        if (floatingInputField != null) {
            if (overlayInteractionMode == OverlayInteractionMode.CHAT_ONLY) {
                floatingInputField.setEnabled(true);
                floatingInputField.setFocusable(true);
                floatingInputField.setFocusableInTouchMode(true);
                floatingInputField.setCursorVisible(true);
                floatingInputField.setHint(R.string.floating_chat_input_hint_chat);
                floatingInputField.setVisibility(View.VISIBLE);
            } else {
                floatingInputField.setText("");
                floatingInputField.setEnabled(false);
                floatingInputField.setFocusable(false);
                floatingInputField.setFocusableInTouchMode(false);
                floatingInputField.setCursorVisible(false);
                floatingInputField.setHint(R.string.floating_chat_input_hint_voice);
                floatingInputField.setVisibility(View.VISIBLE);
                hideOverlayKeyboard();
            }
        }

        if (floatingSendButton != null) {
            boolean chatOnly = overlayInteractionMode == OverlayInteractionMode.CHAT_ONLY;
            floatingSendButton.setEnabled(chatOnly);
            floatingSendButton.setVisibility(chatOnly ? View.VISIBLE : View.GONE);
        }

        if (voiceMicButton != null) {
            boolean voiceOnly = overlayInteractionMode == OverlayInteractionMode.VOICE_ONLY;
            voiceMicButton.setEnabled(voiceOnly);
            voiceMicButton.setVisibility(voiceOnly ? View.VISIBLE : View.GONE);
        }

        if (floatingModeText != null) {
            floatingModeText.setText(overlayInteractionMode == OverlayInteractionMode.CHAT_ONLY
                    ? R.string.floating_chat_mode_chat
                    : R.string.floating_chat_mode_voice);
        }

        if (overlayInteractionMode == OverlayInteractionMode.CHAT_ONLY) {
            updateVoiceUiState(false, null);
        } else if (updateStatusChip) {
            updateVoiceUiState(false, getString(R.string.floating_chat_voice_mode_ready));
        }
    }

    private void hideOverlayKeyboard() {
        if (floatingInputField == null) {
            return;
        }

        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(floatingInputField.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
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
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getSavedChatX();
        params.y = getSavedChatY();
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
        return params;
    }

    private void toggleVoiceListening() {
        if (overlayInteractionMode != OverlayInteractionMode.VOICE_ONLY) {
            Toast.makeText(this, R.string.floating_chat_chat_mode_no_voice, Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            updateVoiceUiState(false, "Thiếu quyền Microphone. Chuyển sang màn hình cấp quyền...");
            Toast.makeText(this,
                    "Chưa có quyền Microphone. Mình sẽ mở phần cài đặt app để bạn cấp quyền.",
                    Toast.LENGTH_LONG).show();
            openAppSettings();
            return;
        }

        if (!ensureSpeechRecognizerReady()) {
            updateVoiceUiState(false, "Speech recognizer chưa sẵn sàng.");
            Toast.makeText(this,
                    "Speech recognizer chưa sẵn sàng. Vui lòng thử lại.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (isAssistantSpeaking) {
                stopAssistantSpeech(false);
                startVoiceListening(false);
                return;
            }

            if (isVoiceListening) {
                voiceSessionRequested = false;
                voiceStopRequestedByUser = true;
                cancelVoiceRetry();
                releaseVoiceAudioFocus();
                if (voiceInputHandler != null) {
                    voiceInputHandler.stopListening();
                }
                isVoiceListening = false;
                updateState(AssistantState.IDLE);
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
        if (overlayInteractionMode != OverlayInteractionMode.VOICE_ONLY) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            updateVoiceUiState(false, "Thiếu quyền Microphone.");
            cancelVoiceRetry();
            return;
        }

        if (!ensureSpeechRecognizerReady()) {
            updateVoiceUiState(false, "Speech recognizer chưa sẵn sàng.");
            cancelVoiceRetry();
            return;
        }

        stopAssistantSpeech(true);
        releaseVoiceAudioFocus();

        long nowElapsed = SystemClock.elapsedRealtime();
        long elapsedSinceLastStart = nowElapsed - lastVoiceStartAtMs;
        if (elapsedSinceLastStart >= 0L && elapsedSinceLastStart < VOICE_START_MIN_INTERVAL_MS) {
            voiceSessionRequested = true;
            cancelVoiceRetry();
            safePostMainDelayed(voiceRetryRunnable, VOICE_START_MIN_INTERVAL_MS - elapsedSinceLastStart);
            return;
        }

        if (!fromRetry) {
            voiceAutoRetryCount = 0;
            latestPartialTranscript = "";
        }

        voiceSessionRequested = true;
        cancelVoiceRetry();
        voiceStopRequestedByUser = false;
        lastVoiceStartAtMs = nowElapsed;
        requestVoiceAudioFocus();

        try {
            boolean started = voiceInputHandler != null && voiceInputHandler.startListening();
            if (!started) {
                throw new IllegalStateException("Speech recognizer start failed");
            }
            isVoiceListening = true;
            updateState(AssistantState.LISTENING);
            updateVoiceUiState(true,
                    fromRetry
                            ? "Đang thử nghe lại..."
                            : "Đang lắng nghe... chạm mic lần nữa để dừng");
            appendDebugTrace("VOICE_START", fromRetry ? "Retry start listening" : "Start listening");
        } catch (Exception e) {
            isVoiceListening = false;
            Log.e(TAG, "Unable to start listening", e);
            appendDebugTrace("VOICE_START_ERROR", e.getMessage() == null ? "unknown" : e.getMessage());

            if (!fromRetry && voiceAutoRetryCount < MAX_VOICE_AUTO_RETRY && !voiceStopRequestedByUser) {
                resetSpeechRecognizer("startListening exception");
                scheduleVoiceRetry(SpeechRecognizer.ERROR_CLIENT);
                return;
            }

            updateVoiceUiState(false, "Không thể khởi động nhận diện giọng nói.");
        }
    }

    private void scheduleVoiceRetry(int errorCode) {
        if (voiceStopRequestedByUser || !voiceSessionRequested) {
            cancelVoiceRetry();
            updateVoiceUiState(false, "Đã dừng nghe.");
            return;
        }

        if (voiceAutoRetryCount >= MAX_VOICE_AUTO_RETRY) {
            voiceSessionRequested = false;
            String finalMessage = errorCode == SpeechRecognizer.ERROR_NO_MATCH
                    ? "Mình chưa nghe rõ. Bạn thử nói chậm và gần mic hơn nhé."
                    : mapSpeechErrorMessage(errorCode);
            updateVoiceUiState(false, finalMessage);
            appendDebugTrace("VOICE_RETRY_EXHAUSTED", "error=" + errorCode);
            return;
        }

        voiceAutoRetryCount++;
        long retryDelay = VOICE_RETRY_DELAYS_MS[Math.min(voiceAutoRetryCount - 1, VOICE_RETRY_DELAYS_MS.length - 1)];
        if (errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            retryDelay += 350L;
        }
        updateVoiceUiState(false,
                "Không nghe rõ, đang thử lại ("
                        + voiceAutoRetryCount
                        + "/"
                        + MAX_VOICE_AUTO_RETRY
                        + ") sau "
                        + String.format(Locale.getDefault(), "%.1fs", retryDelay / 1000f)
                        + "...");
        appendDebugTrace("VOICE_RETRY", "error=" + errorCode + ", attempt=" + voiceAutoRetryCount + ", delayMs=" + retryDelay);
        cancelVoiceRetry();
        safePostMainDelayed(voiceRetryRunnable, retryDelay);
    }

    private void cancelVoiceRetry() {
        if (mainHandler != null) {
            mainHandler.removeCallbacks(voiceRetryRunnable);
        }
    }

    private boolean ensureSpeechRecognizerReady() {
        if (voiceInputHandler == null) {
            try {
                initSpeechRecognizer();
            } catch (Exception e) {
                Log.w(TAG, "Unable to initialize speech recognizer", e);
            }
        }

        if (voiceInputHandler == null) {
            return false;
        }

        return voiceInputHandler.ensureReady();
    }

    private void resetSpeechRecognizer(String reason) {
        appendDebugTrace("VOICE_RESET", reason == null ? "unspecified" : reason);
        if (voiceInputHandler != null) {
            voiceInputHandler.reset();
        } else {
            initSpeechRecognizer();
        }
    }

    private boolean shouldRetryVoice(int error) {
        return error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER;
    }

    private boolean isDuplicateSpeechError(int error) {
        long nowElapsed = SystemClock.elapsedRealtime();
        boolean duplicate = error == lastSpeechErrorCode
                && (nowElapsed - lastSpeechErrorAtMs) >= 0L
                && (nowElapsed - lastSpeechErrorAtMs) < VOICE_ERROR_DEDUPE_WINDOW_MS;
        lastSpeechErrorCode = error;
        lastSpeechErrorAtMs = nowElapsed;
        return duplicate;
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

        voiceSessionRequested = false;
        voiceAutoRetryCount = 0;
        latestPartialTranscript = "";
        autoListenAfterAssistantReply = voiceAutoTurnTakingEnabled;
        if (mainHandler != null) {
            mainHandler.removeCallbacks(autoListenAfterSpeechRunnable);
        }

        updateVoiceUiState(false,
                fromPartial
                        ? "Đã dùng kết quả nghe tạm thời."
                        : "Đã nhận giọng nói.");
        updateState(AssistantState.THINKING);
        appendDebugTrace("VOICE_TEXT", text);

        updateVoicePreviewText(text);
        sendToGemini(text);
        return true;
    }

    private void updateVoicePreviewText(String text) {
        if (overlayInteractionMode != OverlayInteractionMode.VOICE_ONLY) {
            return;
        }

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
        if (!isServiceAlive) {
            return;
        }

        if (voiceMicButton != null) {
            try {
                voiceMicButton.setBackgroundResource(
                        listening
                                ? R.drawable.bg_chat_mic_button_listening
                                : R.drawable.bg_chat_mic_button_idle
                );
                if (voiceMicButton instanceof ImageView) {
                    ((ImageView) voiceMicButton).setColorFilter(
                            listening
                                    ? Color.WHITE
                                    : ContextCompat.getColor(this, R.color.floating_chat_action_tint)
                    );
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to update voice mic state", e);
            }
        }

        if (voiceStatusText != null) {
            if (mainHandler != null) {
                mainHandler.removeCallbacks(hideVoiceStatusRunnable);
            }
            if (TextUtils.isEmpty(status)) {
                voiceStatusText.setVisibility(View.GONE);
            } else {
                voiceStatusText.setText(status);
                voiceStatusText.setVisibility(View.VISIBLE);
                if (!listening && mainHandler != null) {
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
                return "Không nhận diện được nội dung. Hãy nói chậm, rõ và gần mic hơn.";
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

    private void stopVoiceCaptureForOverlay() {
        cancelVoiceRetry();
        voiceSessionRequested = false;
        autoListenAfterAssistantReply = false;
        voiceStopRequestedByUser = true;
        stopAssistantSpeech(false);
        releaseVoiceAudioFocus();
        if (mainHandler != null) {
            mainHandler.removeCallbacks(autoListenAfterSpeechRunnable);
        }
        if (voiceInputHandler != null) {
            voiceInputHandler.stopListening();
        }
        isVoiceListening = false;
        updateState(AssistantState.IDLE);
        updateVoiceUiState(false, null);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int getDefaultChatX() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int maxWidth = screenWidth - dpToPx(24);
        int desiredWidth = Math.min(dpToPx(380), maxWidth);
        int popupWidth = Math.max(desiredWidth, dpToPx(280));
        return Math.max(dpToPx(12), (screenWidth - popupWidth) / 2);
    }

    private int getSavedChatX() {
        return positionPrefs != null
                ? positionPrefs.getInt(KEY_CHAT_X, getDefaultChatX())
                : getDefaultChatX();
    }

    private int getSavedChatY() {
        return positionPrefs != null
                ? positionPrefs.getInt(KEY_CHAT_Y, dpToPx(20))
                : dpToPx(20);
    }

    private void saveChatPosition(int x, int y) {
        if (positionPrefs == null) {
            return;
        }
        positionPrefs.edit()
                .putInt(KEY_CHAT_X, x)
                .putInt(KEY_CHAT_Y, y)
                .apply();
    }

    private float getSavedChatAlpha() {
        if (positionPrefs == null) {
            return MAX_CHAT_ALPHA;
        }
        float stored = positionPrefs.getFloat(KEY_CHAT_ALPHA, MAX_CHAT_ALPHA);
        return Math.max(MIN_CHAT_ALPHA, Math.min(MAX_CHAT_ALPHA, stored));
    }

    private void saveChatAlpha(float alpha) {
        if (positionPrefs == null) {
            return;
        }
        positionPrefs.edit().putFloat(KEY_CHAT_ALPHA, alpha).apply();
    }

    private void applyChatAlpha(float alpha, boolean persist) {
        float safeAlpha = Math.max(MIN_CHAT_ALPHA, Math.min(MAX_CHAT_ALPHA, alpha));
        currentChatAlpha = safeAlpha;
        if (persist) {
            saveChatAlpha(safeAlpha);
        }

        if (floatingChatView == null) {
            return;
        }

        try {
            floatingChatView.setAlpha(safeAlpha);
            if (isChatAttached() && windowManager != null && chatParams != null) {
                windowManager.updateViewLayout(floatingChatView, chatParams);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to apply chat alpha", e);
        }
    }

    private void attachChatHeaderDrag(View dragHandle, View[] excludedViews) {
        if (dragHandle == null) {
            return;
        }

        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (chatParams == null) {
                    return false;
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isTouchInsideAnyExcluded(event, excludedViews)) {
                            return false;
                        }
                        initialX = chatParams.x;
                        initialY = chatParams.y;
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
                        if (!isDragging) {
                            return true;
                        }

                        int nextX = initialX + deltaX;
                        int nextY = initialY + deltaY;
                        if (floatingChatView != null) {
                            int screenWidth = getResources().getDisplayMetrics().widthPixels;
                            int screenHeight = getResources().getDisplayMetrics().heightPixels;
                            int maxX = Math.max(0, screenWidth - floatingChatView.getWidth());
                            int maxY = Math.max(dpToPx(4), screenHeight - floatingChatView.getHeight() - dpToPx(12));
                            nextX = Math.max(0, Math.min(nextX, maxX));
                            nextY = Math.max(dpToPx(4), Math.min(nextY, maxY));
                        }

                        chatParams.x = nextX;
                        chatParams.y = nextY;
                        try {
                            if (isChatAttached()) {
                                windowManager.updateViewLayout(floatingChatView, chatParams);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Unable to drag chat header", e);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDragging) {
                            saveChatPosition(chatParams.x, chatParams.y);
                        }
                        return isDragging;
                    default:
                        return false;
                }
            }
        });
    }

    private boolean isTouchInsideAnyExcluded(MotionEvent event, View... views) {
        if (event == null || views == null || views.length == 0) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        for (View view : views) {
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (x >= view.getLeft() && x <= view.getRight()
                    && y >= view.getTop() && y <= view.getBottom()) {
                return true;
            }
        }
        return false;
    }

    private void showDismissTarget() {
        if (bubbleWindowManager != null) {
            bubbleWindowManager.showDismissTarget();
        }
    }

    private void hideDismissTarget() {
        if (bubbleWindowManager != null) {
            bubbleWindowManager.hideDismissTarget();
        }
    }

    private void updateDismissTargetHighlight(boolean hovered) {
        if (bubbleWindowManager != null) {
            bubbleWindowManager.updateDismissTargetHighlight(hovered);
        }
    }

    private boolean isBubbleOverDismissTarget() {
        if (!isBubbleAttached() || bubbleParams == null || bubbleWindowManager == null) {
            return false;
        }
        return bubbleWindowManager.isBubbleOverDismissTarget(bubbleParams);
    }

    private boolean isBubbleInDismissBottomBand() {
        if (bubbleParams == null || bubbleWindowManager == null) {
            return false;
        }
        return bubbleWindowManager.isBubbleInDismissBottomBand(bubbleParams);
    }

    private void triggerBubbleLongPressVoice() {
        if (!isServiceAlive || floatingBubbleView == null) {
            return;
        }

        collapseRadialMenu(false);
        bubbleLongPressTriggered = true;

        if (bubbleVoiceGlow != null) {
            bubbleVoiceGlow.setAlpha(0f);
            bubbleVoiceGlow.setVisibility(View.VISIBLE);
            bubbleVoiceGlow.animate().alpha(1f).setDuration(180).withEndAction(() ->
                    bubbleVoiceGlow.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                        if (bubbleVoiceGlow != null) {
                            bubbleVoiceGlow.setVisibility(View.GONE);
                        }
                    }).start()
            ).start();
        }

        if (bubbleIcon != null) {
            bubbleIcon.setColorFilter(ContextCompat.getColor(this, R.color.floating_chat_action_tint));
            bubbleIcon.animate().scaleX(1.08f).scaleY(1.08f).setDuration(160).withEndAction(() -> {
                if (bubbleIcon != null) {
                    bubbleIcon.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    bubbleIcon.clearColorFilter();
                }
            }).start();
        }

        if (!isChatAttached()) {
            openChatView();
        }
        enterVoiceOnlyMode();
        safePostMainDelayed(this::activateTemporaryVoiceSession, 180L);
    }

    private WindowManager.LayoutParams createSettingsOverlayLayoutParams() {
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private void showChatSettingsOverlay() {
        if (windowManager == null || floatingChatView == null || !isChatAttached()) {
            return;
        }
        if (chatSettingsOverlayView != null && chatSettingsOverlayView.getParent() != null) {
            return;
        }

        try {
            chatSettingsOverlayView = LayoutInflater.from(this)
                    .inflate(R.layout.layout_floating_chat_settings_overlay, null);
        } catch (Exception e) {
            Log.e(TAG, "Unable to inflate chat settings overlay", e);
            appendDebugTrace("OVERLAY_SETTINGS_ERROR", e.getMessage());
            return;
        }
        View overlayRoot = chatSettingsOverlayView.findViewById(R.id.overlay_root);
        View overlayPanel = chatSettingsOverlayView.findViewById(R.id.overlay_panel);
        View btnClose = chatSettingsOverlayView.findViewById(R.id.btn_overlay_close);
        View btnFullscreen = chatSettingsOverlayView.findViewById(R.id.btn_overlay_fullscreen);
        SeekBar alphaSeekBar = chatSettingsOverlayView.findViewById(R.id.seek_overlay_alpha);
        TextView alphaValueText = chatSettingsOverlayView.findViewById(R.id.tv_overlay_alpha_value);
        SeekBar bargeInSeekBar = chatSettingsOverlayView.findViewById(R.id.seek_overlay_barge_in_threshold);
        TextView bargeInValueText = chatSettingsOverlayView.findViewById(R.id.tv_overlay_barge_in_value);
        Switch autoTurnTakingSwitch = chatSettingsOverlayView.findViewById(R.id.switch_overlay_auto_turn_taking);
        Switch speakRepliesSwitch = chatSettingsOverlayView.findViewById(R.id.switch_overlay_speak_replies);

        if (overlayRoot != null) {
            overlayRoot.setOnClickListener(v -> hideChatSettingsOverlay());
        }
        if (overlayPanel != null) {
            overlayPanel.setOnClickListener(v -> {
                // Eat clicks inside the panel.
            });
        }
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> hideChatSettingsOverlay());
        }
        if (btnFullscreen != null) {
            btnFullscreen.setOnClickListener(v -> openAssistantFullscreen());
        }
        if (alphaSeekBar != null) {
            int progress = Math.round(((currentChatAlpha - MIN_CHAT_ALPHA) / (MAX_CHAT_ALPHA - MIN_CHAT_ALPHA)) * 100f);
            alphaSeekBar.setProgress(Math.max(0, Math.min(100, progress)));
            if (alphaValueText != null) {
                alphaValueText.setText(Math.round(currentChatAlpha * 100f) + "%");
            }
            alphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                    float nextAlpha = MIN_CHAT_ALPHA
                            + (Math.max(0, Math.min(100, progressValue)) / 100f) * (MAX_CHAT_ALPHA - MIN_CHAT_ALPHA);
                    applyChatAlpha(nextAlpha, true);
                    if (alphaValueText != null) {
                        alphaValueText.setText(Math.round(nextAlpha * 100f) + "%");
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (autoTurnTakingSwitch != null) {
            autoTurnTakingSwitch.setChecked(voiceAutoTurnTakingEnabled);
            autoTurnTakingSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    setVoiceAutoTurnTakingEnabled(isChecked));
        }

        if (speakRepliesSwitch != null) {
            speakRepliesSwitch.setChecked(voiceSpeakRepliesEnabled);
            speakRepliesSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    setVoiceSpeakRepliesEnabled(isChecked));
        }

        if (bargeInSeekBar != null) {
            bargeInSeekBar.setProgress(bargeInThresholdToProgress(voiceBargeInThreshold));
            if (bargeInValueText != null) {
                bargeInValueText.setText(formatBargeInThreshold(voiceBargeInThreshold));
            }
            bargeInSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                    float nextThreshold = progressToBargeInThreshold(progressValue);
                    setVoiceBargeInThreshold(nextThreshold);
                    if (bargeInValueText != null) {
                        bargeInValueText.setText(formatBargeInThreshold(nextThreshold));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        if (chatSettingsOverlayParams == null) {
            chatSettingsOverlayParams = createSettingsOverlayLayoutParams();
        }

        try {
            windowManager.addView(chatSettingsOverlayView, chatSettingsOverlayParams);
        } catch (Exception e) {
            Log.w(TAG, "Unable to show chat settings overlay", e);
            chatSettingsOverlayView = null;
        }
    }

    private void hideChatSettingsOverlay() {
        if (windowManager == null || chatSettingsOverlayView == null) {
            return;
        }
        try {
            if (chatSettingsOverlayView.getParent() != null) {
                windowManager.removeView(chatSettingsOverlayView);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to hide chat settings overlay", e);
        }
        chatSettingsOverlayView = null;
    }

    private void openAssistantFullscreen() {
        try {
            cleanupTemporaryVoiceSessionIfNeeded();
            Intent intent = new Intent(this, AiAssistantActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (currentSessionId > 0L) {
                intent.putExtra(AiAssistantActivity.EXTRA_SESSION_ID, currentSessionId);
            }
            startActivity(intent);
            hideChatSettingsOverlay();
            closeChatView();
        } catch (Exception e) {
            Log.w(TAG, "Unable to open assistant fullscreen", e);
        }
    }

    private void restoreBubbleAfterOpenFailure() {
        try {
            if (floatingBubbleView.getParent() == null && bubbleParams != null) {
                bubbleParams.x = getSavedBubbleX();
                bubbleParams.y = getSavedBubbleY();
                windowManager.addView(floatingBubbleView, bubbleParams);
            }
            floatingBubbleView.setVisibility(View.VISIBLE);
            hideDismissTarget();
        } catch (Exception ignored) {
        }
    }

    private void closeChatView() {
        if (windowManager == null || floatingBubbleView == null || floatingChatView == null) {
            return;
        }

        overlayInteractionMode = OverlayInteractionMode.CHAT_ONLY;
        applyOverlayInteractionModeUi(false);
        collapseRadialMenu(false);
        stopVoiceCaptureForOverlay();
        cleanupTemporaryVoiceSessionIfNeeded();
        hideChatSettingsOverlay();
        if (chatParams != null) {
            saveChatPosition(chatParams.x, chatParams.y);
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
        showUserMessage(userMessage);

        if (tryHandleQuickCreateIntent(userMessage)) {
            appendDebugTrace("SHORTCUT", "Handled by quick-create intent parser (no tool call).");
            return;
        }

        final boolean[] suppressNextAssistantReply = {false};
        runWorkerSafely(() -> agentOrchestrator.handleUserMessage(userMessage, new AgentOrchestrator.Callback() {
            @Override
            public void onAssistantReply(String replyText) {
                if (!isServiceAlive) {
                    return;
                }
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
                if (!isServiceAlive) {
                    return;
                }
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
                if (!isServiceAlive) {
                    return;
                }
                appendDebugTrace(stage, payload);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isServiceAlive) {
                    return;
                }
                // Keep the app usable even if orchestrator flow fails.
                appendDebugTrace("ORCHESTRATOR_ERROR", errorMessage);
                fallbackToLegacyAgentFlow(userMessage);
            }
        }));
    }

    private void fallbackToLegacyAgentFlow(String userMessage) {
        appendDebugTrace("FALLBACK_PATH", "Running legacy prompt + response parser.");
        runWorkerSafely(() -> {
            String prompt = buildAgentPrompt(userMessage);
            appendDebugTrace("LEGACY_PROMPT", prompt);
            updateState(AssistantState.THINKING);

            StringBuilder jsonAccumulator = new StringBuilder();
            StringBuilder speechAccumulator = new StringBuilder();
            final int[] lastSpokenIndex = {0};
            final boolean[] streamedAnySentence = {false};

            geminiManager.generateResponseStream(prompt, new hcmute.edu.vn.tickticktodo.helper.GeminiManager.StreamResponseCallback() {
                @Override
                public void onNext(String chunk) {
                    if (!isServiceAlive) return;
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
                    if (!isServiceAlive) return;
                    appendDebugTrace("LEGACY_MODEL_ERROR", errorMessage);
                    showAssistantMessage(errorMessage);
                    updateState(AssistantState.IDLE);
                }

                @Override
                public void onComplete() {
                    if (!isServiceAlive) return;

                    if (flushRemainingSpeechBuffer(speechAccumulator)) {
                        streamedAnySentence[0] = true;
                    }

                    if (streamedAnySentence[0]) {
                        autoListenAfterAssistantReply = voiceAutoTurnTakingEnabled;
                        if (!isAssistantSpeaking && voiceAutoTurnTakingEnabled) {
                            safePostMainDelayed(autoListenAfterSpeechRunnable, VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS);
                        }
                    }

                    boolean shouldSpeakParsedReply = !streamedAnySentence[0];
                    runWorkerSafely(() -> handleAIResponse(jsonAccumulator.toString(), shouldSpeakParsedReply));
                }
            });
        });
    }

    private String extractReplyFieldFromPartialJson(String jsonBuffer) {
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

    private boolean speakCompletedSentencesFromBuffer(StringBuilder speechBuffer) {
        if (overlayInteractionMode != OverlayInteractionMode.VOICE_ONLY) {
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
                speakAssistantMessage(sentence, TextToSpeech.QUEUE_ADD, false);
                spokeAny = true;
            }
            lastMatchEnd = matcher.end();
        }

        if (lastMatchEnd > 0) {
            speechBuffer.delete(0, lastMatchEnd);
        }
        return spokeAny;
    }

    private boolean flushRemainingSpeechBuffer(StringBuilder speechBuffer) {
        if (overlayInteractionMode != OverlayInteractionMode.VOICE_ONLY) {
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
        speakAssistantMessage(remaining, TextToSpeech.QUEUE_ADD, true);
        return true;
    }

    private void handleAIResponse(String responseText) {
        handleAIResponse(responseText, true);
    }

    private void handleAIResponse(String responseText, boolean allowAssistantReply) {
        if (responseText == null || responseText.trim().isEmpty()) {
            if (allowAssistantReply) {
                showAssistantMessage("AI chưa trả về nội dung. Bạn thử lại nhé.");
            }
            return;
        }

        AgentResponseEnvelope response = responseParser.parse(responseText);
        String action = response.getAction();
        JSONObject payload = response.getPayload();
        String reply = response.getReply();
        appendDebugTrace("LEGACY_PARSED_ENVELOPE", envelopeTraceJson(response).toString());

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
                    showAssistantMessage(fallbackReply);
                } else if (!TextUtils.isEmpty(fallbackReply)) {
                    showAssistantMessage(fallbackReply, true, false);
                }
                break;
        }
    }

    private void maybeShowAssistantReply(String reply, String fallbackMessage, boolean allowAssistantReply) {
        String finalMessage = TextUtils.isEmpty(reply) ? fallbackMessage : reply;
        if (TextUtils.isEmpty(finalMessage)) {
            return;
        }

        if (allowAssistantReply) {
            showAssistantMessage(finalMessage);
        } else {
            showAssistantMessage(finalMessage, true, false);
        }
    }

    private void createTaskFromPayload(JSONObject payload, String reply) {
        createTaskFromPayload(payload, reply, true);
    }

    private void createTaskFromPayload(JSONObject payload, String reply, boolean allowAssistantReply) {
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
            long newId = TaskDatabase.getInstance(this).taskDao().insert(task);
            task.setId(newId);
            ReminderScheduler.scheduleReminder(this, task);

            maybeShowAssistantReply(reply,
                    "Đã tạo công việc \"" + title + "\" thành công.",
                    allowAssistantReply);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create task", e);
            showAssistantMessage("Không thể tạo công việc lúc này. Bạn thử lại nhé.");
        }
    }

    private void completeTaskFromPayload(JSONObject payload, String reply) {
        completeTaskFromPayload(payload, reply, true);
    }

    private void completeTaskFromPayload(JSONObject payload, String reply, boolean allowAssistantReply) {
        if (payload == null) {
            maybeShowAssistantReply(reply,
                    "Mình chưa nhận được thông tin task cần hoàn thành.",
                    allowAssistantReply);
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
                maybeShowAssistantReply(reply,
                        "Mình chưa tìm thấy task phù hợp để hoàn thành.",
                        allowAssistantReply);
                return;
            }

            TaskDatabase.getInstance(this).taskDao()
                    .markTaskAsCompletedWithDate(target.getId(), true, System.currentTimeMillis());

                maybeShowAssistantReply(reply,
                    "Đã hoàn thành task: \"" + target.getTitle() + "\".",
                    allowAssistantReply);
        } catch (Exception e) {
            Log.e(TAG, "Failed to complete task", e);
            showAssistantMessage("Không thể cập nhật task lúc này. Bạn thử lại nhé.");
        }
    }

    private void listTodayTasks(String reply) {
        listTodayTasks(reply, true);
    }

    private void listTodayTasks(String reply, boolean allowAssistantReply) {
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

            String fallback = count == 0
                    ? "Hôm nay bạn chưa có task nào."
                    : "Danh sách hôm nay:\n" + builder.toString().trim();
            maybeShowAssistantReply(reply, fallback, allowAssistantReply);
        } catch (Exception e) {
            Log.e(TAG, "Failed to list today tasks", e);
            showAssistantMessage("Mình chưa lấy được danh sách task hôm nay. Bạn thử lại nhé.");
        }
    }

    private void executeWifiCommand(boolean enable) {
        executeWifiCommand(enable, null, true);
    }

    private void executeWifiCommand(boolean enable, String reply, boolean allowAssistantReply) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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

        safePostMain(this::renderDebugTrace);
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
        showUserMessage(message, true);
    }

    private void showUserMessage(String message, boolean persist) {
        rememberTurn("user", message);
        if (persist) {
            persistChatMessageAsync("user", message);
        }
        safePostMain(() -> appendChatMessage(message, true));
    }

    private void showAssistantMessage(String message) {
        showAssistantMessage(message, true, true);
    }

    private void showAssistantMessage(String message, boolean persist) {
        showAssistantMessage(message, persist, true);
    }

    private void showAssistantMessage(String message, boolean persist, boolean allowVoiceOutput) {
        rememberTurn("assistant", message);
        if (persist) {
            persistChatMessageAsync("assistant", message);
        }
        safePostMain(() -> appendChatMessage(message, false));

        if (allowVoiceOutput && overlayInteractionMode == OverlayInteractionMode.VOICE_ONLY) {
            boolean shouldAutoContinue = autoListenAfterAssistantReply && voiceAutoTurnTakingEnabled;
            speakAssistantMessage(message, shouldAutoContinue);
            if (!shouldAutoContinue) {
                autoListenAfterAssistantReply = false;
            }
        }
    }

    private void appendChatMessage(String message, boolean isUser) {
        if (TextUtils.isEmpty(message) || !isServiceAlive) {
            return;
        }

        if (floatingChatAdapter == null || floatingChatRecyclerView == null || floatingChatView == null) {
            return;
        }

        try {
            floatingChatAdapter.addMessage(new ChatMessage(message, isUser));
            if (isChatAttached()) {
                floatingChatRecyclerView.smoothScrollToPosition(
                        Math.max(0, floatingChatAdapter.getItemCount() - 1)
                );
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to append chat message", e);
        }
    }

    private void restoreFloatingHistory() {
        try {
            ChatHistoryDao dao = TaskDatabase.getInstance(this).chatHistoryDao();
            long sessionId = ensureHistorySessionSync(dao);
            List<ChatHistoryMessage> rows = dao.getMessagesForSessionSync(sessionId);

            if (rows == null || rows.isEmpty()) {
                showAssistantMessage("Xin chào, mình là trợ lý nổi. Bạn có thể nói hoặc nhập lệnh tạo/hoàn thành task.", true, false);
                return;
            }

            List<ChatMessage> restored = new ArrayList<>();
            conversationMemory.clear();
            for (ChatHistoryMessage row : rows) {
                boolean isUser = "user".equalsIgnoreCase(row.role);
                String content = row.content == null ? "" : row.content;
                restored.add(new ChatMessage(content, isUser));
                rememberTurn(isUser ? "user" : "assistant", content);
            }

            safePostMain(() -> {
                if (floatingChatAdapter != null) {
                    floatingChatAdapter.setMessages(restored);
                    if (floatingChatRecyclerView != null && !restored.isEmpty()) {
                        floatingChatRecyclerView.scrollToPosition(restored.size() - 1);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unable to restore floating chat history", e);
        }
    }

    private void persistChatMessageAsync(String role, String message) {
        if (TextUtils.isEmpty(message) || workerExecutor == null || workerExecutor.isShutdown()) {
            return;
        }

        runWorkerSafely(() -> {
            try {
                ChatHistoryDao dao = TaskDatabase.getInstance(this).chatHistoryDao();
                long sessionId = ensureHistorySessionSync(dao);
                long now = System.currentTimeMillis();

                ChatHistoryMessage row = new ChatHistoryMessage();
                row.sessionId = sessionId;
                row.role = role;
                row.content = message;
                row.source = tempVoiceSessionActive
                    ? CHAT_SOURCE_FLOATING_TEMP_VOICE
                    : CHAT_SOURCE_FLOATING;
                row.createdAt = now;
                dao.insertMessage(row);

                String preview = abbreviateForPreview(message);
                dao.updateSessionAfterMessage(sessionId, now, preview);
                if ("user".equals(role)) {
                    dao.updateSessionTitleIfEmpty(sessionId, preview);
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to persist floating message", e);
            }
        });
    }

    private long ensureHistorySessionSync(ChatHistoryDao dao) {
        if (currentSessionId > 0L && dao.getSessionByIdSync(currentSessionId) != null) {
            return currentSessionId;
        }

        ChatSession latest = dao.getLatestSessionSync();
        if (latest != null) {
            currentSessionId = latest.id;
            return currentSessionId;
        }

        String source = tempVoiceSessionActive
            ? CHAT_SOURCE_FLOATING_TEMP_VOICE
            : CHAT_SOURCE_FLOATING;
        currentSessionId = createHistorySessionSync(dao, source, "");
        return currentSessionId;
    }

    private String abbreviateForPreview(String text) {
        String clean = text == null ? "" : text.trim().replace('\n', ' ');
        if (clean.length() <= 64) {
            return clean;
        }
        return clean.substring(0, 64) + "...";
    }

    private boolean tryHandleQuickCreateIntent(String userText) {
        String lower = userText.toLowerCase(Locale.ROOT).trim();
        String normalized = lower.replaceAll("\\s+", " ");
        boolean hasCreateVerb = normalized.startsWith("nhắc tôi")
            || normalized.startsWith("nhac toi")
            || normalized.startsWith("tạo task")
            || normalized.startsWith("tao task")
            || normalized.startsWith("thêm task")
            || normalized.startsWith("them task")
            || normalized.startsWith("tạo việc")
            || normalized.startsWith("tao viec")
            || normalized.startsWith("thêm việc")
            || normalized.startsWith("them viec")
            || normalized.startsWith("tạo công việc")
            || normalized.startsWith("tao cong viec")
            || normalized.startsWith("thêm công việc")
            || normalized.startsWith("them cong viec")
            || normalized.startsWith("create task")
            || normalized.startsWith("add task");
        boolean hasTaskNoun = normalized.contains("task")
            || normalized.contains("việc")
            || normalized.contains("viec")
            || normalized.contains("công việc")
            || normalized.contains("cong viec");
        boolean likelyCreate = hasCreateVerb
            || (hasTaskNoun
            && (normalized.contains("tạo")
            || normalized.contains("tao")
            || normalized.contains("thêm")
            || normalized.contains("them")
            || normalized.contains("nhắc")
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
        runWorkerSafely(() -> createTaskFromPayload(quickPayload, "Mình đã tạo nhanh công việc \"" + quickTitle + "\" cho bạn."));
        return true;
    }

    private Long parseNaturalDueDate(String lowerText) {
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

        collapseRadialMenu(false);
        stopVoiceCaptureForOverlay();
        cleanupTemporaryVoiceSessionIfNeeded();
        hideDismissTarget();
        hideChatSettingsOverlay();
        if (bubbleWindowManager != null) {
            bubbleWindowManager.release();
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
        runWorkerSafely(() -> {
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

            safePostMain(() -> {
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
            safePostMainDelayed(toneGenerator::release, 300);
        } catch (Exception e) {
            Log.w(TAG, "Unable to play ting sound", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mainHandler != null) {
            String action = intent != null ? intent.getAction() : null;
            if (ACTION_TOGGLE_BUBBLE.equals(action)) {
                refreshOverlayTheme(false);
                boolean isBubbleVisible = floatingBubbleView != null && floatingBubbleView.getParent() != null && floatingBubbleView.getVisibility() == View.VISIBLE;
                if (isBubbleVisible || isChatAttached()) {
                    safePostMain(this::hideAssistantOverlay);
                } else {
                    safePostMain(this::ensureBubbleVisible);
                }
            } else if (ACTION_REFRESH_THEME.equals(action)) {
                safePostMain(() -> refreshOverlayTheme(true));
            } else if (ACTION_HIDE_BUBBLE.equals(action)) {
                refreshOverlayTheme(false);
                safePostMain(this::hideAssistantOverlay);
            } else if (ACTION_SHOW_BUBBLE.equals(action)) {
                refreshOverlayTheme(false);
                safePostMain(this::ensureBubbleVisible);
            } else if (ACTION_SHOW_DAILY_REVIEW.equals(action)) {
                refreshOverlayTheme(false);
                String reviewText = intent != null
                        ? intent.getStringExtra(EXTRA_DAILY_REVIEW_TEXT)
                        : null;
                long[] unfinishedIds = intent != null
                        ? intent.getLongArrayExtra(EXTRA_UNFINISHED_TASK_IDS)
                        : null;

                String safeReview = TextUtils.isEmpty(reviewText)
                        ? "Bạn đã hoàn thành nhiều nỗ lực hôm nay. Hãy nghỉ ngơi và chuẩn bị cho ngày mai nhé."
                        : reviewText;

                safePostMain(() -> {
                    playTingSound();
                    ensureBubbleVisible();
                    showDailyReview(safeReview, unfinishedIds);
                    showAssistantMessage(safeReview);
                });
            } else if (ACTION_SHOW_HABIT_NUDGE.equals(action)) {
                refreshOverlayTheme(false);
                String nudgeText = intent != null ? intent.getStringExtra(EXTRA_HABIT_NUDGE_TEXT) : null;
                String safeNudge = TextUtils.isEmpty(nudgeText)
                        ? "Bạn đã nghỉ thói quen vài ngày rồi. Thử quay lại với một bước nhỏ hôm nay nhé."
                        : nudgeText;
                safePostMain(() -> {
                    playTingSound();
                    ensureBubbleVisible();
                    showAssistantMessage(safeNudge);
                });
            } else {
                refreshOverlayTheme(false);
            }
        }
        return START_NOT_STICKY;
    }

    private void refreshOverlayTheme(boolean force) {
        collapseRadialMenu(false);

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (!force && currentNightMode == lastUiNightMode) {
            return;
        }
        lastUiNightMode = currentNightMode;

        boolean chatWasVisible = isChatAttached();
        boolean bubbleWasVisible = floatingBubbleView != null
                && floatingBubbleView.getParent() != null
                && floatingBubbleView.getVisibility() == View.VISIBLE;
        boolean settingsVisible = chatSettingsOverlayView != null && chatSettingsOverlayView.getParent() != null;

        int savedX = bubbleParams != null ? bubbleParams.x : getSavedBubbleX();
        int savedY = bubbleParams != null ? bubbleParams.y : getSavedBubbleY();
        int savedChatX = chatParams != null ? chatParams.x : getSavedChatX();
        int savedChatY = chatParams != null ? chatParams.y : getSavedChatY();
        float savedAlpha = currentChatAlpha;

        hideChatSettingsOverlay();

        try {
            if (floatingChatView != null && floatingChatView.getParent() != null) {
                windowManager.removeView(floatingChatView);
            }
        } catch (Exception ignored) {
        }

        try {
            if (floatingBubbleView != null && floatingBubbleView.getParent() != null) {
                windowManager.removeView(floatingBubbleView);
            }
        } catch (Exception ignored) {
        }

        initFloatingBubble();
        initFloatingChat();

        if (bubbleParams != null && floatingBubbleView != null && floatingBubbleView.getParent() != null) {
            int collapsedSize = bubbleCollapsedSizePx > 0 ? bubbleCollapsedSizePx : dpToPx(BUBBLE_COLLAPSED_SIZE_DP);
            bubbleParams.width = collapsedSize;
            bubbleParams.height = collapsedSize;
            bubbleParams.x = clampBubbleX(savedX, collapsedSize);
            bubbleParams.y = clampBubbleY(savedY, collapsedSize);
            try {
                windowManager.updateViewLayout(floatingBubbleView, bubbleParams);
            } catch (Exception ignored) {
            }
        }

        if (chatParams != null) {
            chatParams.x = savedChatX;
            chatParams.y = savedChatY;
            saveChatPosition(savedChatX, savedChatY);
        }
        applyChatAlpha(savedAlpha, true);

        if (chatWasVisible) {
            openChatView();
            if (settingsVisible) {
                showChatSettingsOverlay();
            }
        } else if (!bubbleWasVisible) {
            hideAssistantOverlay();
        }
    }

    private void ensureBubbleVisible() {
        if (windowManager == null || floatingBubbleView == null) {
            return;
        }

        collapseRadialMenu(false);
        hideDismissTarget();
        hideChatSettingsOverlay();

        try {
            if (floatingChatView != null && floatingChatView.getParent() != null) {
                // If chat is already open, keep it open.
                // This avoids race conditions where a delayed SHOW action closes chat immediately.
                floatingBubbleView.setVisibility(View.GONE);
                return;
            }

            if (bubbleParams != null) {
                int collapsedSize = bubbleCollapsedSizePx > 0 ? bubbleCollapsedSizePx : dpToPx(BUBBLE_COLLAPSED_SIZE_DP);
                bubbleParams.width = collapsedSize;
                bubbleParams.height = collapsedSize;
            }

            if (floatingBubbleView.getParent() == null && bubbleParams != null) {
                bubbleParams.x = clampBubbleX(getSavedBubbleX(), bubbleParams.width);
                bubbleParams.y = clampBubbleY(getSavedBubbleY(), bubbleParams.height);
                windowManager.addView(floatingBubbleView, bubbleParams);
                synchronized (OVERLAY_LOCK) {
                    sWindowManager = windowManager;
                    sBubbleView = floatingBubbleView;
                }
            } else if (bubbleParams != null) {
                bubbleParams.x = clampBubbleX(bubbleParams.x, bubbleParams.width);
                bubbleParams.y = clampBubbleY(bubbleParams.y, bubbleParams.height);
                windowManager.updateViewLayout(floatingBubbleView, bubbleParams);
            }
            floatingBubbleView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.e(TAG, "Unable to ensure bubble visibility", e);
        }
    }

    private int getSavedBubbleX() {
        int collapsedSize = bubbleCollapsedSizePx > 0 ? bubbleCollapsedSizePx : dpToPx(BUBBLE_COLLAPSED_SIZE_DP);
        if (bubbleWindowManager == null) {
            return DEFAULT_BUBBLE_X;
        }
        return bubbleWindowManager.getSavedBubbleX(collapsedSize);
    }

    private int getSavedBubbleY() {
        int collapsedSize = bubbleCollapsedSizePx > 0 ? bubbleCollapsedSizePx : dpToPx(BUBBLE_COLLAPSED_SIZE_DP);
        if (bubbleWindowManager == null) {
            return DEFAULT_BUBBLE_Y;
        }
        return bubbleWindowManager.getSavedBubbleY(collapsedSize);
    }

    private void saveBubblePosition(int x, int y) {
        if (bubbleWindowManager == null) {
            return;
        }
        int collapsedSize = bubbleCollapsedSizePx > 0 ? bubbleCollapsedSizePx : dpToPx(BUBBLE_COLLAPSED_SIZE_DP);
        bubbleWindowManager.saveBubblePosition(x, y, collapsedSize, collapsedSize);
    }

    private void snapBubbleToNearestHorizontalEdge(boolean animated) {
        if (bubbleWindowManager == null || bubbleParams == null || floatingBubbleView == null) {
            return;
        }
        bubbleWindowManager.snapBubbleToNearestHorizontalEdge(bubbleParams, floatingBubbleView, animated);
    }

    private int clampBubbleX(int x, int bubbleWidth) {
        if (bubbleWindowManager == null) {
            return x;
        }
        return bubbleWindowManager.clampBubbleX(x, bubbleWidth);
    }

    private int clampBubbleY(int y, int bubbleHeight) {
        if (bubbleWindowManager == null) {
            return y;
        }
        return bubbleWindowManager.clampBubbleY(y, bubbleHeight);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int currentNightMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNightMode != lastUiNightMode) {
            lastUiNightMode = currentNightMode;
            refreshOverlayTheme(true);
        }

        if (bubbleParams != null && floatingBubbleView != null && floatingBubbleView.getParent() != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                snapBubbleToNearestHorizontalEdge(false);
            }, 100);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (tempVoiceSessionActive && tempVoiceSessionId > 0L) {
            long deleteSessionId = tempVoiceSessionId;
            try {
                TaskDatabase.getInstance(this).chatHistoryDao().deleteSessionById(deleteSessionId);
            } catch (Exception e) {
                Log.w(TAG, "Unable to delete temporary voice session on destroy", e);
            }
            tempVoiceSessionActive = false;
            tempVoiceSessionId = -1L;
            previousPersistentSessionId = -1L;
        }

        collapseRadialMenu(false);

        isServiceAlive = false;

        if (bubbleParams != null) {
            saveBubblePosition(bubbleParams.x, bubbleParams.y);
        }
        if (chatParams != null) {
            saveChatPosition(chatParams.x, chatParams.y);
        }
        saveChatAlpha(currentChatAlpha);

        hideDismissTarget();
        hideChatSettingsOverlay();

        cancelVoiceRetry();
        if (mainHandler != null) {
            mainHandler.removeCallbacks(autoListenAfterSpeechRunnable);
            mainHandler.removeCallbacks(hideVoiceStatusRunnable);
            mainHandler.removeCallbacks(bubbleLongPressRunnable);
            mainHandler.removeCallbacksAndMessages(null);
        }

        if (voiceInputHandler != null) {
            voiceInputHandler.destroy();
            voiceInputHandler = null;
        }
        isVoiceListening = false;
        voiceSessionRequested = false;
        autoListenAfterAssistantReply = false;
        releaseVoiceAudioFocus();
        shutdownAssistantTts();
        stopVoicePulseAnimation();

        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            workerExecutor.shutdown();
            try {
                if (!workerExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    workerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (windowManager != null) {
            try {
                if (floatingBubbleView != null && floatingBubbleView.getParent() != null) {
                    windowManager.removeView(floatingBubbleView);
                }
            } catch (Exception ignored) {
            }
            try {
                if (floatingChatView != null && floatingChatView.getParent() != null) {
                    windowManager.removeView(floatingChatView);
                }
            } catch (Exception ignored) {
            }
        }

        synchronized (OVERLAY_LOCK) {
            sWindowManager = null;
            sBubbleView = null;
            sChatView = null;
        }
    }
}