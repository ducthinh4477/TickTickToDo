package hcmute.edu.vn.doinbot.core.background;

import hcmute.edu.vn.doinbot.R;

final class FloatingOverlayStatusFormatter {

    private FloatingOverlayStatusFormatter() {
    }

    static int resolveModeLabelResId(boolean chatOnly) {
        return chatOnly
                ? R.string.floating_chat_mode_chat
                : R.string.floating_chat_mode_voice;
    }
}