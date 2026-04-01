package hcmute.edu.vn.tickticktodo.helper;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.tickticktodo.BuildConfig;

public class GeminiManager {

    private static final String TAG = "GeminiManager";

    public interface ResponseCallback {
        void onSuccess(String responseText);
        void onError(String errorMessage);
    }

    // Use a currently available model on v1beta generateContent.
    private static final String MODEL_NAME = "gemini-2.5-flash";
    private static final String CONFIG_ERROR_MESSAGE =
            "Vui lòng kiểm tra cấu hình API Key trong local.properties";
        private static final String NETWORK_ERROR_MESSAGE =
            "Không thể kết nối mạng tới Gemini. Hãy kiểm tra Internet và thử lại.";
        private static final String GENERIC_ERROR_MESSAGE =
            "Không thể kết nối Gemini lúc này. Vui lòng thử lại.";

    private static GeminiManager instance;

    private final ExecutorService workerExecutor;
    private final Handler mainHandler;
    private final GenerativeModelFutures modelFutures;

    private GeminiManager() {
        workerExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        String apiKey = BuildConfig.GEMINI_API_KEY == null ? "" : BuildConfig.GEMINI_API_KEY.trim();
        if (apiKey.isEmpty() || "YOUR_KEY_HERE".equals(apiKey)) {
            modelFutures = null;
        } else {
            GenerativeModel model = new GenerativeModel(MODEL_NAME, apiKey);
            modelFutures = GenerativeModelFutures.from(model);
        }
    }

    public static synchronized GeminiManager getInstance() {
        if (instance == null) {
            instance = new GeminiManager();
        }
        return instance;
    }

    public boolean hasConfiguredApiKey() {
        return modelFutures != null;
    }

    public void generateResponse(String prompt, ResponseCallback callback) {
        if (callback == null) {
            return;
        }
        if (modelFutures == null) {
            postError(callback, CONFIG_ERROR_MESSAGE);
            return;
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            postError(callback, "Tin nhắn không hợp lệ. Vui lòng thử lại.");
            return;
        }

        try {
            Content content = new Content.Builder().addText(prompt).build();
            ListenableFuture<GenerateContentResponse> future = modelFutures.generateContent(content);

            Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    String text = result != null ? result.getText() : null;
                    if (text == null || text.trim().isEmpty()) {
                        postError(callback, "AI chưa trả về nội dung. Vui lòng thử lại.");
                        return;
                    }
                    postSuccess(callback, text.trim());
                }

                @Override
                public void onFailure(Throwable t) {
                    Log.e(TAG, "Gemini request failed", t);
                    postError(callback, mapErrorMessage(t));
                }
            }, workerExecutor);
        } catch (Exception e) {
            Log.e(TAG, "Gemini request setup failed", e);
            postError(callback, mapErrorMessage(e));
        }
    }

    public void generateVisionResponse(Bitmap bitmap, String prompt, ResponseCallback callback) {
        if (callback == null) {
            return;
        }
        if (modelFutures == null) {
            postError(callback, CONFIG_ERROR_MESSAGE);
            return;
        }
        if (bitmap == null) {
            postError(callback, "Ảnh đầu vào không hợp lệ.");
            return;
        }

        try {
            Content content = new Content.Builder()
                    .addText(prompt)
                    .addImage(bitmap)
                    .build();

            ListenableFuture<GenerateContentResponse> future = modelFutures.generateContent(content);
            Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
                @Override
                public void onSuccess(GenerateContentResponse result) {
                    String text = result != null ? result.getText() : null;
                    if (text == null || text.trim().isEmpty()) {
                        postError(callback, "AI chưa trả về nội dung. Vui lòng thử lại.");
                        return;
                    }
                    postSuccess(callback, text.trim());
                }

                @Override
                public void onFailure(Throwable t) {
                    Log.e(TAG, "Gemini vision request failed", t);
                    postError(callback, mapErrorMessage(t));
                }
            }, workerExecutor);
        } catch (Exception e) {
            Log.e(TAG, "Gemini vision setup failed", e);
            postError(callback, mapErrorMessage(e));
        }
    }

    public String generateResponseBlocking(String prompt, long timeoutMillis) throws Exception {
        if (modelFutures == null) {
            throw new IllegalStateException(CONFIG_ERROR_MESSAGE);
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt rỗng.");
        }

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> future = modelFutures.generateContent(content);
        GenerateContentResponse result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        String text = result != null ? result.getText() : null;
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalStateException("AI chưa trả về nội dung.");
        }
        return text.trim();
    }

    private String mapErrorMessage(Throwable t) {
        if (t == null || t.getMessage() == null) {
            return GENERIC_ERROR_MESSAGE;
        }

        String message = t.getMessage().toLowerCase();
        if (containsAny(message,
                "api key", "invalid", "unauthenticated", "permission_denied", "forbidden", "401", "403")) {
            return CONFIG_ERROR_MESSAGE;
        }

        if (containsAny(message, "not found", "model", "404")) {
            return "Model Gemini hiện không khả dụng. Vui lòng cập nhật ứng dụng hoặc thử lại sau.";
        }

        if (containsAny(message,
                "timeout", "timed out", "unable to resolve host", "failed to connect", "network", "connection")) {
            return NETWORK_ERROR_MESSAGE;
        }

        return GENERIC_ERROR_MESSAGE;
    }

    private boolean containsAny(String source, String... tokens) {
        for (String token : tokens) {
            if (source.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private void postSuccess(ResponseCallback callback, String text) {
        mainHandler.post(() -> callback.onSuccess(text));
    }

    private void postError(ResponseCallback callback, String errorMessage) {
        mainHandler.post(() -> callback.onError(errorMessage));
    }
}
