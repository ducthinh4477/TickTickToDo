package hcmute.edu.vn.tickticktodo.ui.chat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SuggestionFeedbackCommandParser {

    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("^/(accept|dismiss|apply)\\s+([A-Za-z0-9-]+|last)$", Pattern.CASE_INSENSITIVE);

    ParsedSuggestionFeedbackCommand parse(String userText) {
        if (userText == null) {
            return null;
        }

        Matcher matcher = COMMAND_PATTERN.matcher(userText.trim());
        if (!matcher.matches()) {
            return null;
        }

        String feedbackType = matcher.group(1) == null
                ? null
                : matcher.group(1).trim().toUpperCase(Locale.ROOT);
        String targetToken = matcher.group(2);
        return new ParsedSuggestionFeedbackCommand(feedbackType, targetToken);
    }

    static final class ParsedSuggestionFeedbackCommand {

        private final String feedbackType;
        private final String targetToken;

        ParsedSuggestionFeedbackCommand(String feedbackType, String targetToken) {
            this.feedbackType = feedbackType;
            this.targetToken = targetToken;
        }

        String getFeedbackType() {
            return feedbackType;
        }

        String getTargetToken() {
            return targetToken;
        }
    }
}