package hcmute.edu.vn.tickticktodo.core.background.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;

public class VoiceInputHandler {

    public interface Callback {
        void onReadyForSpeech(Bundle params);
        void onBeginningOfSpeech();
        void onRmsChanged(float rmsdB);
        void onEndOfSpeech();
        void onError(int error);
        void onResults(ArrayList<String> results);
        void onPartialResults(ArrayList<String> partialResults);
    }

    private final Context context;
    private final String logTag;
    private final Callback callback;

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    public VoiceInputHandler(Context context, String logTag, Callback callback) {
        this.context = context;
        this.logTag = TextUtils.isEmpty(logTag) ? "VoiceInputHandler" : logTag;
        this.callback = callback;
    }

    public boolean initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(logTag, "Speech recognition is not available");
            return false;
        }

        destroy();

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizerIntent = createSpeechRecognizerIntent();
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    if (callback != null) {
                        callback.onReadyForSpeech(params);
                    }
                }

                @Override
                public void onBeginningOfSpeech() {
                    if (callback != null) {
                        callback.onBeginningOfSpeech();
                    }
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    if (callback != null) {
                        callback.onRmsChanged(rmsdB);
                    }
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    if (callback != null) {
                        callback.onEndOfSpeech();
                    }
                }

                @Override
                public void onError(int error) {
                    if (callback != null) {
                        callback.onError(error);
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> data = results == null
                            ? null
                            : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (callback != null) {
                        callback.onResults(data);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> partial = partialResults == null
                            ? null
                            : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (callback != null) {
                        callback.onPartialResults(partial);
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
            return true;
        } catch (Exception e) {
            Log.e(logTag, "Unable to initialize speech recognizer", e);
            destroy();
            return false;
        }
    }

    public boolean ensureReady() {
        if (speechRecognizer != null && speechRecognizerIntent != null) {
            return true;
        }
        return initialize();
    }

    public boolean startListening() {
        if (!ensureReady()) {
            return false;
        }

        try {
            speechRecognizer.startListening(speechRecognizerIntent);
            return true;
        } catch (Exception e) {
            Log.e(logTag, "Unable to start speech recognizer", e);
            return false;
        }
    }

    public void stopListening() {
        try {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
            }
        } catch (Exception e) {
            Log.w(logTag, "Unable to stop speech recognizer", e);
        }
    }

    public void reset() {
        try {
            if (speechRecognizer != null) {
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            }
        } catch (Exception e) {
            Log.w(logTag, "Unable to reset speech recognizer", e);
        }
        speechRecognizer = null;
        speechRecognizerIntent = null;
        ensureReady();
    }

    public void destroy() {
        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
        } catch (Exception e) {
            Log.w(logTag, "Unable to destroy speech recognizer", e);
        }
        speechRecognizer = null;
        speechRecognizerIntent = null;
    }

    private Intent createSpeechRecognizerIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2400L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1700L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);
        return intent;
    }
}
