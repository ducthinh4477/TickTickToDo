package hcmute.edu.vn.tickticktodo.core.background;

import android.text.TextUtils;

import hcmute.edu.vn.tickticktodo.data.model.SuggestionEntity;

final class FloatingSuggestionMessageFormatter {

    private FloatingSuggestionMessageFormatter() {
    }

    static String formatHighPrioritySuggestionMessage(SuggestionEntity suggestion) {
        if (suggestion == null || TextUtils.isEmpty(suggestion.title)) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("[GOI Y UU TIEN] ").append(suggestion.title);
        if (!TextUtils.isEmpty(suggestion.reason)) {
            builder.append("\n").append(suggestion.reason);
        }
        if (!TextUtils.isEmpty(suggestion.id)) {
            builder.append("\nID: ").append(suggestion.id);
        }
        builder.append("\nLenh nhanh: /accept last | /dismiss last | /apply last");
        return builder.toString();
    }
}