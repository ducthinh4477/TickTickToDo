package hcmute.edu.vn.tickticktodo.ui.chat;

import android.text.TextUtils;

import hcmute.edu.vn.tickticktodo.data.model.SuggestionEntity;

final class SuggestionCardMessageFormatter {

    String buildSuggestionCard(SuggestionEntity suggestion) {
        if (suggestion == null) {
            return "";
        }

        StringBuilder card = new StringBuilder();
        card.append("[GOI Y] ").append(TextUtils.isEmpty(suggestion.title) ? "Co goi y moi" : suggestion.title);
        if (!TextUtils.isEmpty(suggestion.reason)) {
            card.append("\nLy do: ").append(suggestion.reason);
        }
        card.append("\nDo tin cay: ").append(Math.round(suggestion.confidence * 100f)).append("%");
        if (!TextUtils.isEmpty(suggestion.id)) {
            card.append("\nID: ").append(suggestion.id);
        }
        if (suggestion.requiresConfirmation) {
            card.append("\nCan xac nhan truoc khi ap dung.");
        }
        card.append("\nLenh nhanh: /accept last | /dismiss last | /apply last");
        return card.toString();
    }
}