package hcmute.edu.vn.doinbot.core.ai;

public interface LlmProvider {

    interface StreamResponseCallback {
        void onNext(String chunk);

        void onComplete();

        void onError(String errorMessage);
    }

    interface ResponseCallback {
        void onSuccess(String responseText);

        void onError(String errorMessage);
    }

    boolean hasConfiguredApiKey();

    void generateResponse(String prompt, ResponseCallback callback);

    void generateResponseStream(String prompt, StreamResponseCallback callback);

    String generateResponseBlocking(String prompt, long timeoutMillis) throws Exception;

    void reloadConfiguration();
}