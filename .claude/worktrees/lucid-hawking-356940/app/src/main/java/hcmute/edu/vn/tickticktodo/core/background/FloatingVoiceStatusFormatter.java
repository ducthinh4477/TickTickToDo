package hcmute.edu.vn.doinbot.core.background;

import java.util.Locale;

final class FloatingVoiceStatusFormatter {

    private FloatingVoiceStatusFormatter() {
    }

    static String formatListeningStatus() {
        return "Đang lắng nghe...";
    }

    static String formatThinkingStatus() {
        return "Đã nghe xong, đang xử lý...";
    }

    static String formatAssistantReplyingStatus() {
        return "Trợ lý đang phản hồi...";
    }

    static String formatSpeechRecognizerNotReadyStatus() {
        return "Speech recognizer chưa sẵn sàng.";
    }

    static String formatSpeechRecognizerNotReadyToast() {
        return "Speech recognizer chưa sẵn sàng. Vui lòng thử lại.";
    }

    static String formatStoppingListeningStatus() {
        return "Đang dừng nghe...";
    }

    static String formatVoiceUnavailableStatus() {
        return "Không thể bật voice lúc này.";
    }

    static String formatVoiceStartStatus(boolean fromRetry) {
        return fromRetry
                ? "Đang thử nghe lại..."
                : "Đang lắng nghe... chạm mic lần nữa để dừng";
    }

    static String formatVoiceStartFailureStatus() {
        return "Không thể khởi động nhận diện giọng nói.";
    }

    static String formatVoiceStoppedStatus() {
        return "Đã dừng nghe.";
    }

    static String formatVoiceStoppedClientIgnoredStatus() {
        return "Voice đã dừng.";
    }

    static String formatNoMatchRetryExhaustedStatus() {
        return "Mình chưa nghe rõ. Bạn thử nói chậm và gần mic hơn nhé.";
    }

    static String formatVoiceRetryStatus(int attempt, int maxRetry, long retryDelayMs) {
        return "Không nghe rõ, đang thử lại ("
                + attempt
                + "/"
                + maxRetry
                + ") sau "
                + String.format(Locale.getDefault(), "%.1fs", retryDelayMs / 1000f)
                + "...";
    }

    static String formatRecognizedVoiceAppliedStatus(boolean fromPartial) {
        return fromPartial
                ? "Đã dùng kết quả nghe tạm thời."
                : "Đã nhận giọng nói.";
    }

    static String formatLiveListeningPreviewStatus(String abbreviatedText) {
        return "Đang nghe: " + (abbreviatedText == null ? "" : abbreviatedText);
    }

    static String formatVoiceErrorToast(int errorCode) {
        return "Loi thu am: " + errorCode;
    }
}