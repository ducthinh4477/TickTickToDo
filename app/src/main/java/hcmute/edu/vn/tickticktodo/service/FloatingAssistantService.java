package hcmute.edu.vn.tickticktodo.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.R;

public class FloatingAssistantService extends Service {

    private WindowManager windowManager;
    private View floatingBubbleView;
    private View floatingChatView;

    private static final int NOTIFICATION_ID = 200;
    private static final String CHANNEL_ID = "floating_ai_channel";
    private static final String TAG = "FloatingAssistant";
    private static final String GEMINI_API_KEY = "YOUR_API_KEY_HERE";

    private GenerativeModelFutures modelFutures;
    private ExecutorService executor;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Start as foreground service to keep it running
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Trợ lý AI TickTickToDo")
                .setContentText("Trợ lý nổi đang hoạt động")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        GenerativeModel gm = new GenerativeModel(
                "gemini-pro",
                GEMINI_API_KEY
        );
        modelFutures = GenerativeModelFutures.from(gm);

        initSpeechRecognizer();
        initFloatingBubble();
        initFloatingChat();
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

    private void sendToGemini(String message) {
        String systemPrompt = "Bạn là trợ lý ảo bằng giọng nói của TickTickToDo. " +
            "Nếu người dùng muốn mở Wifi, hãy trả về CHUỖI JSON DUY NHẤT: {\"action\": \"wifi_on\"} " +
            "Nếu người dùng muốn tắt Wifi, hãy trả về CHUỖI JSON DUY NHẤT: {\"action\": \"wifi_off\"} " +
            "Nếu hỏi bình thường, hãy trả lời ngắn gọn bằng text.";
            
        Content content = new Content.Builder().addText(systemPrompt + "\\nNgười dùng nói: " + message).build();
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
                Log.e(TAG, "Gemini call failed", t);
                mainHandler.post(() -> Toast.makeText(FloatingAssistantService.this, "AI đang bận, thử lại sau!", Toast.LENGTH_SHORT).show());
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
            // Text bình thường
            mainHandler.post(() -> {
                Toast.makeText(FloatingAssistantService.this, "AI: " + responseText, Toast.LENGTH_LONG).show();
            });
        }
    }

    private void executeWifiCommand(boolean enable) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            try {
                // setWifiEnabled bị hạn chế từ API 29+ với app thường
                boolean result = wifiManager.setWifiEnabled(enable);
                String state = enable ? "Bật" : "Tắt";
                mainHandler.post(() -> Toast.makeText(FloatingAssistantService.this, "Đã " + state + " Wifi: " + result, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Lỗi bật tắt Wifi", e);
                mainHandler.post(() -> Toast.makeText(FloatingAssistantService.this, "Từ Android 10+, không thể bật tắt Wifi tự động từ app ngoài hệ thống.", Toast.LENGTH_LONG).show());
            }
        }
    }

    private void initFloatingBubble() {
        floatingBubbleView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                : WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        windowManager.addView(floatingBubbleView, params);

        ImageView ivBubble = floatingBubbleView.findViewById(R.id.iv_floating_bubble);

        ivBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long lastInputTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastInputTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingBubbleView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long timeDiff = System.currentTimeMillis() - lastInputTime;
                        int xDiff = (int) Math.abs(event.getRawX() - initialTouchX);
                        int yDiff = (int) Math.abs(event.getRawY() - initialTouchY);
                        
                        if (xDiff < 10 && yDiff < 10 && timeDiff < 200) {
                            toggleChatView();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void initFloatingChat() {
        floatingChatView = LayoutInflater.from(this).inflate(R.layout.layout_floating_chat, null);

        // Xử lý nút Đóng
        View btnClose = floatingChatView.findViewById(R.id.btn_close_chat);
        btnClose.setOnClickListener(v -> toggleChatView());

        // Xử lý Gửi bằng Text
        View btnSend = floatingChatView.findViewById(R.id.btn_send_floating);
        View btnMic = floatingChatView.findViewById(R.id.btn_mic_floating);
        EditText etInput = floatingChatView.findViewById(R.id.et_message_floating);

        btnSend.setOnClickListener(v -> {
            String msg = etInput.getText().toString().trim();
            if (!msg.isEmpty()) {
                sendToGemini(msg);
                etInput.setText(""); // Xoá trắng
            }
        });

        // Xử lý Gửi bằng Giọng nói
        btnMic.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    mainHandler.post(() -> Toast.makeText(this, "Đang lắng nghe...", Toast.LENGTH_SHORT).show());
                    speechRecognizer.startListening(speechRecognizerIntent);
                } else {
                    mainHandler.post(() -> Toast.makeText(this, "Vui lòng cấp quyền Microphone trên Activity trước!", Toast.LENGTH_SHORT).show());
                }
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                speechRecognizer.stopListening();
                return true;
            }
            return false;
        });
        
        // Ẩn khi click ra ngoài
        floatingChatView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                toggleChatView();
                return true;
            }
            return false;
        });
    }

    private void toggleChatView() {
        if (floatingChatView.getParent() != null) {
            windowManager.removeView(floatingChatView);
            floatingBubbleView.setVisibility(View.VISIBLE);
        } else {
            floatingBubbleView.setVisibility(View.GONE);
            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                : WindowManager.LayoutParams.TYPE_PHONE;
                
            WindowManager.LayoutParams chatParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, 
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            chatParams.gravity = Gravity.CENTER;
            windowManager.addView(floatingChatView, chatParams);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating AI Assistant Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Không bind qua IPC
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
}