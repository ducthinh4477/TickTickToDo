package hcmute.edu.vn.doinbot.core.background.assistant;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import hcmute.edu.vn.doinbot.core.background.assistant.AssistantStateMonitor.AssistantState;

public class AssistantSpeechHandler {

    public interface Callback {
        void onDebugTrace(String stage, String payload);

        void onAssistantSpeechStarted();

        void onAssistantSpeechCompleted();

        void onAssistantSpeechFailed(String utteranceId);

        void onVoiceReadyForSpeech(Bundle params);

        void onVoiceBeginningOfSpeech();

        void onVoiceRmsChanged(float rmsdB);

        void onVoiceEndOfSpeech();

        void onVoiceError(int error);

        void onVoiceResults(ArrayList<String> results);

        void onVoicePartialResults(ArrayList<String> partialResults);

        void onAssistantStateChanged(AssistantState state);
    }

    private final Context context;
    private final String logTag;
    private final Callback callback;

    private VoiceInputHandler voiceInputHandler;
    private TextToSpeech assistantTts;
    private boolean assistantTtsReady;
    private boolean assistantSpeaking;
    private AudioManager audioManager;
    private AudioFocusRequest voiceAudioFocusRequest;
    private boolean hasVoiceAudioFocus;

    public AssistantSpeechHandler(Context context, String logTag, Callback callback) {
        this.context = context;
        this.logTag = TextUtils.isEmpty(logTag) ? "AssistantSpeechHandler" : logTag;
        this.callback = callback;
    }

    public void initAssistantTts() {
        try {
            if (assistantTts != null) {
                assistantTts.stop();
                assistantTts.shutdown();
            }
        } catch (Exception ignored) {
        }

        assistantTtsReady = false;
        assistantSpeaking = false;
        assistantTts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (assistantTts == null) {
                return;
            }
            if (status != TextToSpeech.SUCCESS) {
                trace("VOICE_TTS_INIT", "failed status=" + status);
                return;
            }

            int lang = assistantTts.setLanguage(new Locale("vi", "VN"));
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                trace("VOICE_TTS_LANG", "vi-VN unavailable, fallback default locale.");
                assistantTts.setLanguage(Locale.getDefault());
            }

            assistantTts.setSpeechRate(1.0f);
            assistantTts.setPitch(1.0f);
            assistantTtsReady = true;
            trace("VOICE_TTS_INIT", "ready");
        });

        if (assistantTts != null) {
            assistantTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    assistantSpeaking = true;
                    if (callback != null) {
                        callback.onAssistantStateChanged(AssistantState.SPEAKING);
                        callback.onAssistantSpeechStarted();
                    }
                }

                @Override
                public void onDone(String utteranceId) {
                    assistantSpeaking = false;
                    if (callback != null) {
                        callback.onAssistantStateChanged(AssistantState.IDLE);
                        callback.onAssistantSpeechCompleted();
                    }
                }

                @Override
                public void onError(String utteranceId) {
                    assistantSpeaking = false;
                    if (callback != null) {
                        callback.onAssistantStateChanged(AssistantState.IDLE);
                        callback.onAssistantSpeechFailed(utteranceId);
                    }
                }
            });
        }
    }

    public void shutdownAssistantTts() {
        try {
            if (assistantTts != null) {
                assistantTts.stop();
                assistantTts.shutdown();
            }
        } catch (Exception ignored) {
        }
        assistantTts = null;
        assistantTtsReady = false;
        assistantSpeaking = false;
    }

    public void stopAssistantSpeech() {
        assistantSpeaking = false;
        try {
            if (assistantTts != null) {
                assistantTts.stop();
            }
        } catch (Exception ignored) {
        }
    }

    public boolean speakAssistantMessage(String message, int queueMode) {
        if (TextUtils.isEmpty(message)) {
            return false;
        }

        if (assistantTts == null || !assistantTtsReady) {
            trace("VOICE_TTS_SKIP", "TTS not ready.");
            return false;
        }

        String utteranceId = "floating-tts-" + SystemClock.elapsedRealtime();
        trace("VOICE_TTS_SPEAK", abbreviateForStatus(message));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                assistantTts.speak(message, queueMode, null, utteranceId);
            } else {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
                assistantTts.speak(message, queueMode, params);
            }
            return true;
        } catch (Exception e) {
            assistantSpeaking = false;
            trace("VOICE_TTS_EXCEPTION", e.getMessage());
            return false;
        }
    }

    public boolean requestVoiceAudioFocus() {
        if (audioManager == null) {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) {
            hasVoiceAudioFocus = false;
            return false;
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (voiceAudioFocusRequest == null) {
                voiceAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(focusChange -> {
                            if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                                    || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                stopAssistantSpeech();
                                if (callback != null) {
                                    callback.onAssistantStateChanged(AssistantState.IDLE);
                                }
                            }
                        })
                        .build();
            }
            int result = audioManager.requestAudioFocus(voiceAudioFocusRequest);
            hasVoiceAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            trace("VOICE_AUDIO_FOCUS", hasVoiceAudioFocus ? "granted" : "denied");
            return hasVoiceAudioFocus;
        }

        int result = audioManager.requestAudioFocus(
                focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                            || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        stopAssistantSpeech();
                        if (callback != null) {
                            callback.onAssistantStateChanged(AssistantState.IDLE);
                        }
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        );
        hasVoiceAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        trace("VOICE_AUDIO_FOCUS", hasVoiceAudioFocus ? "granted" : "denied");
        return hasVoiceAudioFocus;
    }

    public void releaseVoiceAudioFocus() {
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

    public void initSpeechRecognizer() {
        if (voiceInputHandler == null) {
            voiceInputHandler = new VoiceInputHandler(context, logTag, new VoiceInputHandler.Callback() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    if (callback != null) {
                        callback.onVoiceReadyForSpeech(params);
                    }
                }

                @Override
                public void onBeginningOfSpeech() {
                    if (callback != null) {
                        callback.onVoiceBeginningOfSpeech();
                    }
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    if (callback != null) {
                        callback.onVoiceRmsChanged(rmsdB);
                    }
                }

                @Override
                public void onEndOfSpeech() {
                    if (callback != null) {
                        callback.onVoiceEndOfSpeech();
                    }
                }

                @Override
                public void onError(int error) {
                    if (callback != null) {
                        callback.onVoiceError(error);
                    }
                }

                @Override
                public void onResults(ArrayList<String> results) {
                    if (callback != null) {
                        callback.onVoiceResults(results);
                    }
                }

                @Override
                public void onPartialResults(ArrayList<String> partialResults) {
                    if (callback != null) {
                        callback.onVoicePartialResults(partialResults);
                    }
                }
            });
        }

        if (!voiceInputHandler.initialize()) {
            trace("VOICE_INIT", "Speech recognition is not available.");
        }
    }

    public boolean ensureSpeechRecognizerReady() {
        if (voiceInputHandler == null) {
            initSpeechRecognizer();
        }
        if (voiceInputHandler == null) {
            return false;
        }
        return voiceInputHandler.ensureReady();
    }

    public boolean startListening() {
        if (!ensureSpeechRecognizerReady()) {
            return false;
        }
        return voiceInputHandler != null && voiceInputHandler.startListening();
    }

    public void stopListening() {
        if (voiceInputHandler != null) {
            voiceInputHandler.stopListening();
        }
    }

    public void resetSpeechRecognizer(String reason) {
        trace("VOICE_RESET", reason == null ? "unspecified" : reason);
        if (voiceInputHandler != null) {
            voiceInputHandler.reset();
        } else {
            initSpeechRecognizer();
        }
    }

    public void destroy() {
        if (voiceInputHandler != null) {
            voiceInputHandler.destroy();
            voiceInputHandler = null;
        }
        shutdownAssistantTts();
        releaseVoiceAudioFocus();
    }

    public boolean isAssistantSpeaking() {
        return assistantSpeaking;
    }

    public static String mapSpeechErrorMessage(int error) {
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

    private void trace(String stage, String payload) {
        if (callback != null) {
            callback.onDebugTrace(stage, payload);
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
}
