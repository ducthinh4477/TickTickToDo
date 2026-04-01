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
import android.graphics.Rect;
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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
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

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.adapter.ChatAdapter;
import hcmute.edu.vn.tickticktodo.agent.AgentAction;
import hcmute.edu.vn.tickticktodo.agent.AgentOrchestrator;
import hcmute.edu.vn.tickticktodo.agent.AgentPromptContract;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseEnvelope;
import hcmute.edu.vn.tickticktodo.agent.AgentResponseParser;
import hcmute.edu.vn.tickticktodo.agent.model.ToolResult;
import hcmute.edu.vn.tickticktodo.dao.ChatHistoryDao;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.helper.ReminderScheduler;
import hcmute.edu.vn.tickticktodo.model.ChatHistoryMessage;
import hcmute.edu.vn.tickticktodo.model.ChatMessage;
import hcmute.edu.vn.tickticktodo.model.ChatSession;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.ui.AiAssistantActivity;

public class FloatingAssistantService extends Service {

    public static final String ACTION_SHOW_BUBBLE = "hcmute.edu.vn.tickticktodo.action.SHOW_FLOATING_BUBBLE";
    public static final String ACTION_HIDE_BUBBLE = "hcmute.edu.vn.tickticktodo.action.HIDE_FLOATING_BUBBLE";
    public static final String ACTION_SHOW_DAILY_REVIEW = "hcmute.edu.vn.tickticktodo.action.SHOW_DAILY_REVIEW";
    public static final String ACTION_SHOW_HABIT_NUDGE = "hcmute.edu.vn.tickticktodo.action.SHOW_HABIT_NUDGE";
    public static final String ACTION_REFRESH_THEME = "hcmute.edu.vn.tickticktodo.action.REFRESH_THEME";

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
    private static final int DEFAULT_BUBBLE_X = 24;
    private static final int DEFAULT_BUBBLE_Y = 220;
    private static final int MAX_DEBUG_TRACE_LINES = 80;
    private static final int MAX_DEBUG_TRACE_CHARS = 2400;
    private static final int MAX_VOICE_AUTO_RETRY = 3;
    private static final long[] VOICE_RETRY_DELAYS_MS = new long[]{700L, 1400L, 2200L};
    private static final long VOICE_START_MIN_INTERVAL_MS = 650L;
    private static final long VOICE_ERROR_DEDUPE_WINDOW_MS = 900L;
    private static final long VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS = 420L;
    private static final long BUBBLE_LONG_PRESS_TRIGGER_MS = 1000L;
    private static final float MIN_CHAT_ALPHA = 0.35f;
    private static final float MAX_CHAT_ALPHA = 1.0f;
    private static final String CHAT_SOURCE_FLOATING = "floating_assistant";

    private static WindowManager sWindowManager;
    private static View sBubbleView;
    private static View sChatView;

    private WindowManager windowManager;
    private View floatingBubbleView;
    private View floatingChatView;
    private View dismissTargetView;
    private View chatSettingsOverlayView;
    private View bubbleVoiceGlow;
    private ImageView bubbleIcon;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams chatParams;
    private WindowManager.LayoutParams dismissTargetParams;
    private WindowManager.LayoutParams chatSettingsOverlayParams;
    private boolean chatCompatMode;
    private boolean isServiceAlive;
    private boolean hasVoiceAudioFocus;
    private boolean bubbleLongPressTriggered;
    private float currentChatAlpha = MAX_CHAT_ALPHA;

    private GeminiManager geminiManager;
    private ExecutorService workerExecutor;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private TextToSpeech assistantTts;
    private boolean isVoiceListening;
    private boolean isAssistantSpeaking;
    private boolean assistantTtsReady;
    private boolean voiceSessionRequested;
    private boolean autoListenAfterAssistantReply;
    private boolean voiceAutoTurnTakingEnabled = true;
    private boolean voiceSpeakRepliesEnabled = true;
    private int voiceAutoRetryCount;
    private int lastSpeechErrorCode = Integer.MIN_VALUE;
    private long lastSpeechErrorAtMs;
    private long lastVoiceStartAtMs;
    private String latestPartialTranscript = "";
    private boolean voiceStopRequestedByUser;
    private Handler mainHandler;
    private AudioManager audioManager;
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

    private final ArrayDeque<String> conversationMemory = new ArrayDeque<>();
    private final ArrayDeque<String> debugTraceMemory = new ArrayDeque<>();
    private final AgentResponseParser responseParser = new AgentResponseParser();
    private volatile long currentSessionId = -1L;
    private int lastUiNightMode = Configuration.UI_MODE_NIGHT_UNDEFINED;

    @Override
    public void onCreate() {
        super.onCreate();
        isServiceAlive = true;

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
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            workerExecutor = Executors.newSingleThreadExecutor();
            geminiManager = GeminiManager.getInstance();
            agentOrchestrator = new AgentOrchestrator(getApplication());
            positionPrefs = getSharedPreferences(POSITION_PREFS, MODE_PRIVATE);
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
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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

    private void loadVoiceBehaviorSettings() {
        if (positionPrefs == null) {
            return;
        }
        voiceAutoTurnTakingEnabled = positionPrefs.getBoolean(KEY_VOICE_AUTO_TURN_TAKING, true);
        voiceSpeakRepliesEnabled = positionPrefs.getBoolean(KEY_VOICE_SPEAK_REPLIES, true);
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

    private Intent createSpeechRecognizerIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2400L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1700L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);
        return intent;
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
                }

                @Override
                public void onDone(String utteranceId) {
                    safePostMain(() -> {
                        isAssistantSpeaking = false;
                        if (voiceAutoTurnTakingEnabled && autoListenAfterAssistantReply) {
                            safePostMainDelayed(autoListenAfterSpeechRunnable, VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS);
                        }
                    });
                }

                @Override
                public void onError(String utteranceId) {
                    safePostMain(() -> {
                        isAssistantSpeaking = false;
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

        String utteranceId = "floating-tts-" + SystemClock.elapsedRealtime();
        appendDebugTrace("VOICE_TTS_SPEAK", abbreviateForStatus(message));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                assistantTts.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            } else {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                assistantTts.speak(message, TextToSpeech.QUEUE_FLUSH, params);
            }
        } catch (Exception e) {
            isAssistantSpeaking = false;
            appendDebugTrace("VOICE_TTS_EXCEPTION", e.getMessage());
            if (shouldAutoContinueListening) {
                safePostMainDelayed(autoListenAfterSpeechRunnable, VOICE_AUTO_LISTEN_AFTER_SPEAK_DELAY_MS);
            }
        }
    }

    private void requestVoiceAudioFocus() {
        if (audioManager == null || hasVoiceAudioFocus) {
            return;
        }
        try {
            int result = audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            );
            hasVoiceAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            appendDebugTrace("VOICE_AUDIO_FOCUS", hasVoiceAudioFocus ? "granted" : "denied");
        } catch (Exception e) {
            appendDebugTrace("VOICE_AUDIO_FOCUS", "error=" + e.getMessage());
        }
    }

    private void releaseVoiceAudioFocus() {
        if (audioManager == null || !hasVoiceAudioFocus) {
            return;
        }
        try {
            audioManager.abandonAudioFocus(null);
        } catch (Exception ignored) {
        }
        hasVoiceAudioFocus = false;
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is not available");
            return;
        }

        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
        } catch (Exception ignored) {
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = createSpeechRecognizerIntent();

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
                releaseVoiceAudioFocus();
                updateVoiceUiState(false, "Đã nghe xong, đang xử lý...");
                appendDebugTrace("VOICE_END", "End of speech captured.");
            }

            @Override
            public void onError(int error) {
                isVoiceListening = false;
                releaseVoiceAudioFocus();
                boolean userStopped = voiceStopRequestedByUser && error == SpeechRecognizer.ERROR_CLIENT;
                voiceStopRequestedByUser = false;

                if (userStopped) {
                    voiceSessionRequested = false;
                    autoListenAfterAssistantReply = false;
                    cancelVoiceRetry();
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

                if (shouldUsePartialFallback(error)) {
                    if (applyRecognizedVoiceText(latestPartialTranscript, true)) {
                        return;
                    }
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
                updateVoiceUiState(false, mapSpeechErrorMessage(error));
                safePostMain(() ->
                        Toast.makeText(FloatingAssistantService.this, "Loi thu am: " + error, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResults(android.os.Bundle results) {
                isVoiceListening = false;
                releaseVoiceAudioFocus();
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
        bubbleVoiceGlow = floatingBubbleView.findViewById(R.id.voice_long_press_glow);
        bubbleIcon = floatingBubbleView.findViewById(R.id.iv_floating_bubble);

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

        ImageView ivBubble = bubbleIcon;
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
                        bubbleLongPressTriggered = false;
                        isDragging = false;
                        if (mainHandler != null) {
                            mainHandler.removeCallbacks(bubbleLongPressRunnable);
                            mainHandler.postDelayed(bubbleLongPressRunnable, BUBBLE_LONG_PRESS_TRIGGER_MS);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        if (!isDragging && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                            isDragging = true;
                            if (mainHandler != null) {
                                mainHandler.removeCallbacks(bubbleLongPressRunnable);
                            }
                        }
                        if (isDragging) {
                            bubbleParams.x = initialX + deltaX;
                            bubbleParams.y = initialY + deltaY;
                            try {
                                windowManager.updateViewLayout(floatingBubbleView, bubbleParams);
                            } catch (Exception e) {
                                Log.w(TAG, "Unable to update bubble position", e);
                            }
                            showDismissTarget();
                            updateDismissTargetHighlight(isBubbleOverDismissTarget());
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (mainHandler != null) {
                            mainHandler.removeCallbacks(bubbleLongPressRunnable);
                        }
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
                            boolean shouldHide = isBubbleOverDismissTarget();
                            hideDismissTarget();
                            if (shouldHide) {
                                hideAssistantOverlay();
                                Toast.makeText(FloatingAssistantService.this,
                                        R.string.floating_chat_hidden_toast,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                saveBubblePosition(bubbleParams.x, bubbleParams.y);
                            }
                            return true;
                        }

                        if (upDeltaX < clickThreshold && upDeltaY < clickThreshold) {
                            openChatView();
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
                try {
                    toggleVoiceListening();
                } catch (Exception e) {
                    Log.w(TAG, "Mic button failed", e);
                }
            });
        }

        applyChatAlpha(currentChatAlpha, false);
        updateVoiceUiState(false, "Chạm mic để bắt đầu ghi âm");
        runWorkerSafely(this::restoreFloatingHistory);
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
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = getSavedChatX();
        params.y = getSavedChatY();
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
        speechRecognizerIntent = createSpeechRecognizerIntent();
        requestVoiceAudioFocus();

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
        if (speechRecognizer != null && speechRecognizerIntent != null) {
            return true;
        }
        try {
            initSpeechRecognizer();
        } catch (Exception e) {
            Log.w(TAG, "Unable to initialize speech recognizer", e);
        }
        return speechRecognizer != null && speechRecognizerIntent != null;
    }

    private void resetSpeechRecognizer(String reason) {
        appendDebugTrace("VOICE_RESET", reason == null ? "unspecified" : reason);
        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            }
        } catch (Exception ignored) {
        }
        speechRecognizer = null;
        speechRecognizerIntent = null;
        ensureSpeechRecognizerReady();
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
        try {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
            }
        } catch (Exception ignored) {
        }
        isVoiceListening = false;
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
        if (windowManager == null) {
            return;
        }

        if (dismissTargetView == null) {
            dismissTargetView = LayoutInflater.from(this).inflate(R.layout.layout_floating_dismiss_target, null);
        }

        if (dismissTargetParams == null) {
            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            dismissTargetParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
            );
            dismissTargetParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            dismissTargetParams.y = dpToPx(24);
        }

        try {
            if (dismissTargetView.getParent() == null) {
                windowManager.addView(dismissTargetView, dismissTargetParams);
            }
            dismissTargetView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Log.w(TAG, "Unable to show dismiss target", e);
        }
    }

    private void hideDismissTarget() {
        if (windowManager == null || dismissTargetView == null) {
            return;
        }
        try {
            if (dismissTargetView.getParent() != null) {
                windowManager.removeView(dismissTargetView);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to hide dismiss target", e);
        }
    }

    private void updateDismissTargetHighlight(boolean hovered) {
        if (dismissTargetView == null) {
            return;
        }
        TextView targetText = dismissTargetView.findViewById(R.id.tv_dismiss_target);
        if (targetText == null) {
            return;
        }
        targetText.setBackgroundResource(
                hovered
                        ? R.drawable.bg_floating_dismiss_target_active
                        : R.drawable.bg_floating_dismiss_target
        );
        targetText.animate()
                .scaleX(hovered ? 1.08f : 1f)
                .scaleY(hovered ? 1.08f : 1f)
                .setDuration(120)
                .start();
    }

    private boolean isBubbleOverDismissTarget() {
        if (!isBubbleAttached() || dismissTargetView == null || dismissTargetView.getParent() == null) {
            return false;
        }

        Rect bubbleRect = new Rect();
        Rect dismissRect = new Rect();
        boolean bubbleVisible = floatingBubbleView.getGlobalVisibleRect(bubbleRect);
        boolean dismissVisible = dismissTargetView.getGlobalVisibleRect(dismissRect);
        return bubbleVisible && dismissVisible && Rect.intersects(bubbleRect, dismissRect);
    }

    private void triggerBubbleLongPressVoice() {
        if (!isServiceAlive || floatingBubbleView == null) {
            return;
        }

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

        safePostMainDelayed(() -> {
            if (!isVoiceListening) {
                startVoiceListening(false);
            }
        }, 180L);
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

        stopVoiceCaptureForOverlay();
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
            geminiManager.generateResponse(prompt, new GeminiManager.ResponseCallback() {
                @Override
                public void onSuccess(String responseText) {
                    if (!isServiceAlive) {
                        return;
                    }
                    appendDebugTrace("LEGACY_MODEL_RAW", responseText);
                    runWorkerSafely(() -> handleAIResponse(responseText));
                }

                @Override
                public void onError(String errorMessage) {
                    if (!isServiceAlive) {
                        return;
                    }
                    appendDebugTrace("LEGACY_MODEL_ERROR", errorMessage);
                    showAssistantMessage(errorMessage);
                }
            });
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
            task.setSource("AI_AGENT");
            if (dueDate > 0L) {
                task.setDueDate(dueDate);
            }
            long newId = TaskDatabase.getInstance(this).taskDao().insert(task);
            task.setId(newId);
            ReminderScheduler.scheduleReminder(this, task);

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

        if (allowVoiceOutput) {
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
                row.source = CHAT_SOURCE_FLOATING;
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

        long now = System.currentTimeMillis();
        ChatSession session = new ChatSession();
        session.title = "";
        session.source = CHAT_SOURCE_FLOATING;
        session.lastMessage = "";
        session.createdAt = now;
        session.updatedAt = now;
        currentSessionId = dao.insertSession(session);
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

        stopVoiceCaptureForOverlay();
        hideDismissTarget();
        hideChatSettingsOverlay();

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
            if (ACTION_REFRESH_THEME.equals(action)) {
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
            bubbleParams.x = savedX;
            bubbleParams.y = savedY;
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

        hideDismissTarget();
        hideChatSettingsOverlay();

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

        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
        } catch (Exception ignored) {
        }
        speechRecognizer = null;
        speechRecognizerIntent = null;
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