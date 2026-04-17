package hcmute.edu.vn.tickticktodo.ui;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.data.repository.TaskRepository;

public class VoicePromptActivity extends BaseActivity {

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private TextView tvStatus;
    private View pulseView;
    private AnimatorSet pulseAnimator;

    private GeminiManager geminiManager;
    private TaskRepository taskRepository;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isHandlingResult = false;

    private final ActivityResultLauncher<String> requestAudioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startListening();
                } else {
                    Toast.makeText(this, R.string.voice_permission_required, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_prompt);

        tvStatus = findViewById(R.id.tv_voice_status);
        pulseView = findViewById(R.id.voice_pulse);
        findViewById(R.id.btn_cancel_voice_prompt).setOnClickListener(v -> finish());

        geminiManager = GeminiManager.getInstance();
        taskRepository = new TaskRepository(getApplication());

        startPulseAnimation();
        setupSpeechRecognizer();
        ensureAudioPermissionAndStart();
    }

    @Override
    protected void onDestroy() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
    }

    private void ensureAudioPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.voice_not_available, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                tvStatus.setText(R.string.voice_listening);
            }

            @Override
            public void onBeginningOfSpeech() {
                tvStatus.setText(R.string.voice_hearing);
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                if (!isHandlingResult) {
                    tvStatus.setText(R.string.voice_processing);
                }
            }

            @Override
            public void onError(int error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (isHandlingResult) {
                    return;
                }
                tvStatus.setText(R.string.voice_retrying);
                mainHandler.postDelayed(VoicePromptActivity.this::startListening, 500);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (data == null || data.isEmpty()) {
                    tvStatus.setText(R.string.voice_retrying);
                    mainHandler.postDelayed(VoicePromptActivity.this::startListening, 500);
                    return;
                }
                String text = data.get(0);
                processVoiceText(text);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private void startListening() {
        if (speechRecognizer == null || recognizerIntent == null || isHandlingResult) {
            return;
        }
        try {
            tvStatus.setText(R.string.voice_listening);
            speechRecognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.voice_not_available, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void processVoiceText(String spokenText) {
        isHandlingResult = true;
        tvStatus.setText(getString(R.string.voice_processing_text, spokenText));

        String prompt = "Người dùng vừa nói: '" + spokenText + "'. " +
            "Hãy trích xuất Tên công việc và Thời gian (nếu có). " +
            "Trả về JSON: {title: string, hasTime: boolean, timeInMs: long}. " +
            "Chỉ trả về JSON, không thêm markdown.";

        geminiManager.generateResponse(prompt, new GeminiManager.ResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                saveTaskFromAiResponse(spokenText, responseText);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(VoicePromptActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void saveTaskFromAiResponse(String spokenText, String aiResponse) {
        try {
            String jsonText = extractJsonObject(aiResponse);
            JSONObject jsonObject = new JSONObject(jsonText);

            String title = jsonObject.optString("title", "").trim();
            boolean hasTime = jsonObject.optBoolean("hasTime", false);
            long timeInMs = jsonObject.optLong("timeInMs", 0L);

            if (title.isEmpty()) {
                title = spokenText;
            }

            Task task = new Task();
            task.setTitle(title);
            task.setDescription(getString(R.string.voice_created_description));
            task.setPriority(1);
            if (hasTime && timeInMs > 0L) {
                task.setDueDate(timeInMs);
            }

            taskRepository.insert(task, () -> {
                Toast.makeText(VoicePromptActivity.this, R.string.voice_task_added_success, Toast.LENGTH_SHORT).show();
                finish();
            });
        } catch (Exception e) {
            Toast.makeText(this, R.string.voice_parse_failed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }

        String trimmed = raw.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak != -1 && lastFence > firstLineBreak) {
                trimmed = trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private void startPulseAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(pulseView, View.SCALE_X, 1f, 1.12f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(pulseView, View.SCALE_Y, 1f, 1.12f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(pulseView, View.ALPHA, 0.55f, 1f, 0.55f);

        pulseAnimator = new AnimatorSet();
        pulseAnimator.playTogether(scaleX, scaleY, alpha);
        pulseAnimator.setDuration(1000);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (!isFinishing() && !isDestroyed()) {
                    pulseAnimator.start();
                }
            }
        });
        pulseAnimator.start();
    }
}
