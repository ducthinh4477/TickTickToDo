import re

with open('app/src/main/java/hcmute/edu/vn/tickticktodo/service/FloatingAssistantService.java', 'r', encoding='utf-8') as f:
    content = f.read()

imports = """
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;
"""

if "import android.Manifest;" not in content:
    content = content.replace("import android.app.Service;", imports + "\nimport android.app.Service;")

class_vars = """
    private GenerativeModelFutures modelFutures;
    private ExecutorService executor;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private Handler mainHandler;
    private static final String GEMINI_API_KEY = "YOUR_API_KEY_HERE";
    private static final String TAG = "FloatingAssistant";
"""

content = re.sub(r'private static final String CHANNEL_ID = "floating_ai_channel";', 
                 r'private static final String CHANNEL_ID = "floating_ai_channel";\n' + class_vars, content)


init_generative_model = """
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        GenerativeModel gm = new GenerativeModel(
                "gemini-pro", // Có thể thay thế bằng gemini-1.5-pro/flash tuỳ library
                GEMINI_API_KEY
        );
        modelFutures = GenerativeModelFutures.from(gm);
        
        initSpeechRecognizer();
"""

content = content.replace("initFloatingChat();", "initFloatingChat();\n" + init_generative_model)


speech_init = """
    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is not available");
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(android.os.Bundle params) {}
            @Override
            public void onBeginningOfSpeech() {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}
            @Override
            public void onEndOfSpeech() {}
            @Override
            public void onError(int error) {
                Log.e(TAG, "Speech error: " + error);
                mainHandler.post(() -> Toast.makeText(FloatingAssistantService.this, "Lỗi thu âm: " + error, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResults(android.os.Bundle results) {
                ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (data != null && !data.isEmpty()) {
                    String text = data.get(0);
                    Log.d(TAG, "Recognized text: " + text);
                    if (floatingChatView != null) {
                        EditText etInput = floatingChatView.findViewById(R.id.et_message_floating);
                        if (etInput != null) {
                            etInput.setText(text);
                        }
                    }
                    sendToGemini(text);
                }
            }

            @Override
            public void onPartialResults(android.os.Bundle partialResults) {}
            @Override
            public void onEvent(int eventType, android.os.Bundle params) {}
        });
    }
"""

send_to_gemini = """
    private void sendToGemini(String message) {
        String systemPrompt = "Bạn là trợ lý ảo. " +
            "Nếu muốn mở/bật Wifi, CHỈ TRẢ VỀ: {\\"action\\": \\"wifi_on\\"} " +
            "Nếu muốn tắt Wifi, CHỈ TRẢ VỀ: {\\"action\\": \\"wifi_off\\"} " +
            "Nếu hỏi chung, trả về text.";
            
        Content content = new Content.Builder().addText(systemPrompt + "\\nUser request: " + message).build();
        ListenableFuture<GenerateContentResponse> response = modelFutures.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String resultText = result.getText();
                if (resultText != null) {
                    Log.d(TAG, "Gemini Response: " + resultText);
                    executor.execute(() -> handleAIResponse(resultText.trim()));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Gemini failed", t);
            }
        }, executor);
    }
    
    private void handleAIResponse(String responseText) {
        try {
            JSONObject json = new JSONObject(responseText);
            if (json.has("action")) {
                String action = json.getString("action");
                if ("wifi_on".equals(action)) {
                    executeWifiCommand(true);
                } else if ("wifi_off".equals(action)) {
                    executeWifiCommand(false);
                }
            }
        } catch (JSONException e) {
            mainHandler.post(() -> {
                Toast.makeText(FloatingAssistantService.this, "AI: " + responseText, Toast.LENGTH_LONG).show();
            });
        }
    }

    private void executeWifiCommand(boolean enable) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            try {
                // setWifiEnabled is deprecated in API 29+ but works on older or system apps.
                boolean success = wifiManager.setWifiEnabled(enable);
                String state = enable ? "Bật" : "Tắt";
                mainHandler.post(() -> Toast.makeText(FloatingAssistantService.this, "Đã " + state + " Wifi: " + success, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Cannot change WiFi state", e);
                mainHandler.post(() -> Toast.makeText(FloatingAssistantService.this, "Không thể thao tác Wifi trên Android mới.", Toast.LENGTH_SHORT).show());
            }
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (executor != null) {
            executor.shutdown();
        }
        if (windowManager != null) {
            if (floatingBubbleView != null && floatingBubbleView.getParent() != null) windowManager.removeView(floatingBubbleView);
            if (floatingChatView != null && floatingChatView.getParent() != null) windowManager.removeView(floatingChatView);
        }
    }
"""

content = content.replace("private void toggleChatView()", speech_init + send_to_gemini + "\n    private void toggleChatView()")

chat_init_replace = """        View btnSend = floatingChatView.findViewById(R.id.btn_send_floating);
        View btnMic = floatingChatView.findViewById(R.id.btn_mic_floating);
        EditText etInput = floatingChatView.findViewById(R.id.et_message_floating);

        btnSend.setOnClickListener(v -> {
            String msg = etInput.getText().toString().trim();
            if (!msg.isEmpty()) {
                sendToGemini(msg);
                etInput.setText("");
            }
        });

        btnMic.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Đang lắng nghe...", Toast.LENGTH_SHORT).show();
                    speechRecognizer.startListening(speechRecognizerIntent);
                } else {
                    Toast.makeText(this, "Chưa có quyền Micro. Hãy cấp quyền!", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                speechRecognizer.stopListening();
                return true;
            }
            return false;
        });"""

content = re.sub(r'        View btnSend = floatingChatView.*?;.*?\}\);', chat_init_replace, content, flags=re.DOTALL)

with open('app/src/main/java/hcmute/edu/vn/tickticktodo/service/FloatingAssistantService.java', 'w', encoding='utf-8') as f:
    f.write(content)
