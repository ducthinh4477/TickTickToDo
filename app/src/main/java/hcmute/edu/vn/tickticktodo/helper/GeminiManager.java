package hcmute.edu.vn.tickticktodo.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hcmute.edu.vn.tickticktodo.core.ai.LlmProvider;

public class GeminiManager implements LlmProvider {

    public interface StreamResponseCallback extends LlmProvider.StreamResponseCallback {
    }


    private static final String TAG = "GeminiManager";

    public interface ResponseCallback extends LlmProvider.ResponseCallback {
    }

    private static final String DEFAULT_MODEL_NAME = SecurePreferencesHelper.DEFAULT_MODEL;
    private static final String CONFIG_ERROR_MESSAGE =
            "Vui lòng nhập API Key và chọn mô hình trong Cài đặt Trợ lý AI.";
    private static final String NETWORK_ERROR_MESSAGE =
            "Không thể kết nối mạng tới Model. Hãy kiểm tra Internet và thử lại.";
    private static final String GENERIC_ERROR_MESSAGE =
            "Không thể kết nối Model lúc này. Vui lòng thử lại.";
        private static final String QUOTA_ERROR_MESSAGE =
            "Đang chạm giới hạn tốc độ tạm thời (429) của model. Vui lòng thử lại sau ít phút.";
        private static final String MODEL_NOT_FOUND_ERROR_MESSAGE =
            "Model bạn nhập không tồn tại hoặc chưa hỗ trợ generateContent. Vui lòng kiểm tra lại tên model trong Cài đặt Trợ lý AI.";
        private static final long DEFAULT_QUOTA_COOLDOWN_MILLIS = 00_000L;
        private static final long MIN_QUOTA_COOLDOWN_MILLIS = 00_000L;
        private static final Pattern RETRY_AFTER_SECONDS_PATTERN =
            Pattern.compile("retry\\s+in\\s+([0-9]+(?:\\.[0-9]+)?)s", Pattern.CASE_INSENSITIVE);

    private static volatile GeminiManager instance;
    private static volatile Context appContext;

    private final ExecutorService workerExecutor;
    private final Handler mainHandler;
    private final Object modelLock = new Object();
    private final Object quotaCooldownLock = new Object();
    private volatile GenerativeModelFutures modelFutures;
    private volatile String activeModelName = DEFAULT_MODEL_NAME;
    private volatile long quotaCooldownUntilMillis = 0L;

    private GeminiManager() {
        workerExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        reloadConfigurationInternal();
    }

    public static synchronized void initialize(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        if (instance != null) {
            instance.reloadConfiguration();
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

    public boolean isQuotaCooldownActive() {
        return getQuotaCooldownRemainingMillis() > 0L;
    }

    public String getQuotaCooldownMessage() {
        long remainingMillis = getQuotaCooldownRemainingMillis();
        if (remainingMillis <= 0L) {
            return "";
        }
        return buildQuotaErrorMessage(remainingMillis);
    }

    public static String getConfigErrorMessage() {
        return CONFIG_ERROR_MESSAGE;
    }

    @Override
    public void generateResponseStream(String prompt, LlmProvider.StreamResponseCallback callback) {
        generateResponseStreamInternal(prompt, callback);
    }

    private void generateResponseStreamInternal(String prompt,
                                                LlmProvider.StreamResponseCallback callback) {
        if (callback == null) return;
        GenerativeModelFutures currentModel = modelFutures;
        if (currentModel == null) {
            mainHandler.post(() -> callback.onError(CONFIG_ERROR_MESSAGE));
            return;
        }
        long quotaCooldownRemainingMillis = getQuotaCooldownRemainingMillis();
        if (quotaCooldownRemainingMillis > 0L) {
            mainHandler.post(() -> callback.onError(buildQuotaErrorMessage(quotaCooldownRemainingMillis)));
            return;
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Tin nhắn không hợp lệ. Vui lòng thử lại."));
            return;
        }

        try {
            Content content = new Content.Builder().addText(prompt).build();
            Publisher<GenerateContentResponse> publisher = currentModel.generateContentStream(content);
            
            publisher.subscribe(new Subscriber<GenerateContentResponse>() {
                Subscription subscription;
                
                @Override
                public void onSubscribe(Subscription s) {
                    this.subscription = s;
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(GenerateContentResponse generateContentResponse) {
                    String text = generateContentResponse.getText();
                    if (text != null) {
                        mainHandler.post(() -> callback.onNext(text));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e(TAG, "Model stream error", t);
                    mainHandler.post(() -> callback.onError(mapErrorMessage(t)));
                }

                @Override
                public void onComplete() {
                    mainHandler.post(callback::onComplete);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Model stream setup failed", e);
            mainHandler.post(() -> callback.onError(mapErrorMessage(e)));
        }
    }

    public void generateResponseStream(String prompt, StreamResponseCallback callback) {
        generateResponseStream(prompt, (LlmProvider.StreamResponseCallback) callback);
    }

    @Override
    public void generateResponse(String prompt, LlmProvider.ResponseCallback callback) {
        generateResponseInternal(prompt, callback);
    }

    private void generateResponseInternal(String prompt,
                                          LlmProvider.ResponseCallback callback) {
        if (callback == null) {
            return;
        }
        GenerativeModelFutures currentModel = modelFutures;
        if (currentModel == null) {
            postError(callback, CONFIG_ERROR_MESSAGE);
            return;
        }
        long quotaCooldownRemainingMillis = getQuotaCooldownRemainingMillis();
        if (quotaCooldownRemainingMillis > 0L) {
            postError(callback, buildQuotaErrorMessage(quotaCooldownRemainingMillis));
            return;
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            postError(callback, "Tin nhắn không hợp lệ. Vui lòng thử lại.");
            return;
        }

        try {
            Content content = new Content.Builder().addText(prompt).build();
            ListenableFuture<GenerateContentResponse> future = currentModel.generateContent(content);

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

    public void generateResponse(String prompt, ResponseCallback callback) {
        generateResponse(prompt, (LlmProvider.ResponseCallback) callback);
    }

    public void generateVisionResponse(Bitmap bitmap, String prompt, ResponseCallback callback) {
        if (callback == null) {
            return;
        }
        GenerativeModelFutures currentModel = modelFutures;
        if (currentModel == null) {
            postError(callback, CONFIG_ERROR_MESSAGE);
            return;
        }
        long quotaCooldownRemainingMillis = getQuotaCooldownRemainingMillis();
        if (quotaCooldownRemainingMillis > 0L) {
            postError(callback, buildQuotaErrorMessage(quotaCooldownRemainingMillis));
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

                    ListenableFuture<GenerateContentResponse> future = currentModel.generateContent(content);
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

    @Override
    public String generateResponseBlocking(String prompt, long timeoutMillis) throws Exception {
        return generateResponseBlockingInternal(prompt, timeoutMillis);
    }

    private String generateResponseBlockingInternal(String prompt,
                                                    long timeoutMillis) throws Exception {
        GenerativeModelFutures currentModel = modelFutures;
        if (currentModel == null) {
            throw new IllegalStateException(CONFIG_ERROR_MESSAGE);
        }
        long quotaCooldownRemainingMillis = getQuotaCooldownRemainingMillis();
        if (quotaCooldownRemainingMillis > 0L) {
            throw new IllegalStateException(buildQuotaErrorMessage(quotaCooldownRemainingMillis));
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt rỗng.");
        }

        Content content = new Content.Builder().addText(prompt).build();
        ListenableFuture<GenerateContentResponse> future = currentModel.generateContent(content);
        GenerateContentResponse result = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        String text = result != null ? result.getText() : null;
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalStateException("AI chưa trả về nội dung.");
        }
        return text.trim();
    }

    @Override
    public void reloadConfiguration() {
        reloadConfigurationInternal();
    }

    public String getActiveModelName() {
        return activeModelName;
    }

    private String mapErrorMessage(Throwable t) {
        if (t == null) {
            return GENERIC_ERROR_MESSAGE;
        }

        if (isQuotaExceeded(t)) {
            long retryAfterMillis = resolveQuotaRetryAfterMillis(t);
            applyQuotaCooldown(retryAfterMillis);
            return buildQuotaErrorMessage(retryAfterMillis);
        }

        if (isModelNotFound(t)) {
            return MODEL_NOT_FOUND_ERROR_MESSAGE;
        }

        String message = t.getMessage();
        String loweredMessage = message == null ? "" : message.toLowerCase();
        if (containsAny(loweredMessage,
                "api key", "invalid", "unauthenticated", "permission_denied", "forbidden", "401", "403")) {
            return CONFIG_ERROR_MESSAGE;
        }

        if (containsAny(loweredMessage,
                "timeout", "timed out", "unable to resolve host", "failed to connect", "network", "connection")) {
            return NETWORK_ERROR_MESSAGE;
        }

        return GENERIC_ERROR_MESSAGE;
    }

    private void reloadConfigurationInternal() {
        String apiKey = resolveApiKey();
        String modelName = resolveModelName();

        synchronized (modelLock) {
            activeModelName = modelName;
            if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(modelName)) {
                modelFutures = null;
                return;
            }

            try {
                GenerativeModel model = new GenerativeModel(modelName, apiKey);
                modelFutures = GenerativeModelFutures.from(model);
                quotaCooldownUntilMillis = 0L;
            } catch (Exception exception) {
                Log.e(TAG, "Failed to initialize Gemini model", exception);
                modelFutures = null;
            }
        }
    }

    private String resolveApiKey() {
        Context context = appContext;
        if (context == null) {
            return "";
        }

        try {
            String configuredKey = SecurePreferencesHelper.getInstance(context).getApiKey();
            if (TextUtils.isEmpty(configuredKey)) {
                return "";
            }
            return configuredKey.trim();
        } catch (Exception exception) {
            Log.w(TAG, "Failed to read API key from secure preferences", exception);
            return "";
        }
    }

    private String resolveModelName() {
        Context context = appContext;
        if (context != null) {
            try {
                String configuredModel = SecurePreferencesHelper.getInstance(context).getAiModel();
                if (!TextUtils.isEmpty(configuredModel)) {
                    String normalizedModel = normalizeModelName(configuredModel);
                    if (!TextUtils.equals(normalizedModel, configuredModel.trim())) {
                        persistModelSelection(normalizedModel);
                    }
                    return normalizedModel;
                }
            } catch (Exception exception) {
                Log.w(TAG, "Failed to read model from secure preferences", exception);
            }
        }
        return DEFAULT_MODEL_NAME;
    }

    private void persistModelSelection(String modelName) {
        Context context = appContext;
        if (context == null) {
            return;
        }

        try {
            SecurePreferencesHelper.getInstance(context).saveAiModel(modelName);
        } catch (Exception exception) {
            Log.w(TAG, "Failed to persist selected model " + modelName, exception);
        }
    }

    private boolean isQuotaExceeded(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String className = cursor.getClass().getSimpleName();
            String message = cursor.getMessage();
            String lowered = message == null ? "" : message.toLowerCase();

            if ("QuotaExceededException".equals(className)
                    || containsAny(lowered,
                    "quota exceeded",
                    "resource_exhausted",
                    "rate limit",
                    "429",
                    "limit: 0",
                    "free_tier")) {
                return true;
            }

            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean isModelNotFound(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            String lowered = message == null ? "" : message.toLowerCase();

            if (containsAny(lowered,
                    "models/",
                    "is not found",
                    "not supported for generatecontent",
                    "\"status\": \"not_found\"",
                    " 404",
                    "\"code\": 404",
                    "status: not_found")) {
                return true;
            }

            cursor = cursor.getCause();
        }
        return false;
    }

    private long getQuotaCooldownRemainingMillis() {
        long remaining = quotaCooldownUntilMillis - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    private void applyQuotaCooldown(long retryAfterMillis) {
        long normalizedRetryAfterMillis = Math.max(MIN_QUOTA_COOLDOWN_MILLIS, retryAfterMillis);
        long targetUntil = System.currentTimeMillis() + normalizedRetryAfterMillis;
        synchronized (quotaCooldownLock) {
            if (targetUntil > quotaCooldownUntilMillis) {
                quotaCooldownUntilMillis = targetUntil;
            }
        }
    }

    private long resolveQuotaRetryAfterMillis(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            long parsed = parseRetryAfterMillis(cursor.getMessage());
            if (parsed > 0L) {
                return parsed;
            }
            cursor = cursor.getCause();
        }
        return DEFAULT_QUOTA_COOLDOWN_MILLIS;
    }

    private long parseRetryAfterMillis(String message) {
        if (TextUtils.isEmpty(message)) {
            return -1L;
        }

        Matcher matcher = RETRY_AFTER_SECONDS_PATTERN.matcher(message);
        if (!matcher.find()) {
            return -1L;
        }

        try {
            double seconds = Double.parseDouble(matcher.group(1));
            return Math.max(MIN_QUOTA_COOLDOWN_MILLIS, (long) Math.ceil(seconds * 1000.0d));
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private String buildQuotaErrorMessage(long retryAfterMillis) {
        long safeRetryAfterMillis = Math.max(MIN_QUOTA_COOLDOWN_MILLIS, retryAfterMillis);
        long seconds = Math.max(1L, (long) Math.ceil(safeRetryAfterMillis / 1000.0d));
        return "Đang chạm giới hạn theo phút (RPM) của model. Vui lòng thử lại sau khoảng "
                + seconds
                + " giây. Nếu bạn dùng key trả phí, hãy kiểm tra key đang trỏ đúng project và đúng model.";
    }

    private String normalizeModelName(String modelName) {
        if (TextUtils.isEmpty(modelName)) {
            return DEFAULT_MODEL_NAME;
        }

        String normalized = modelName.trim();
        if (normalized.startsWith("models/")) {
            normalized = normalized.substring("models/".length()).trim();
        }

        return normalized;
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

    private void postSuccess(LlmProvider.ResponseCallback callback, String text) {
        mainHandler.post(() -> callback.onSuccess(text));
    }

    private void postError(LlmProvider.ResponseCallback callback, String errorMessage) {
        mainHandler.post(() -> callback.onError(errorMessage));
    }

    private void postError(ResponseCallback callback, String errorMessage) {
        mainHandler.post(() -> callback.onError(errorMessage));
    }
}
