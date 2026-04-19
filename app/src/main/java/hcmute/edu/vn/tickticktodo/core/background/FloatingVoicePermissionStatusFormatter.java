package hcmute.edu.vn.tickticktodo.core.background;

final class FloatingVoicePermissionStatusFormatter {

    private FloatingVoicePermissionStatusFormatter() {
    }

    static String formatMissingPermissionShortStatus() {
        return "Thiếu quyền Microphone.";
    }

    static String formatMissingPermissionDetailedStatus() {
        return "Thiếu quyền Microphone. Chuyển sang màn hình cấp quyền...";
    }

    static String formatMissingPermissionToast() {
        return "Chưa có quyền Microphone. Mình sẽ mở phần cài đặt app để bạn cấp quyền.";
    }
}