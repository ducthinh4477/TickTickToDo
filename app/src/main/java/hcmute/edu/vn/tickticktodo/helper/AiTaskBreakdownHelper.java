package hcmute.edu.vn.tickticktodo.helper;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiTaskBreakdownHelper {

    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private AiTaskBreakdownHelper() {
    }

    public static String buildPrompt(String taskTitle) {
        return "Tôi có một công việc là: " + taskTitle + ". " +
                "Hãy chia nhỏ nó thành 3-5 bước thực hiện cụ thể. " +
                "Trả về CHỈ dùng JSON array, mỗi phần tử là chuỗi String. " +
                "Ví dụ: [\\\"Bước 1\\\", \\\"Bước 2\\\"]";
    }

    public static List<String> parseSteps(String rawResponse) throws JSONException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new JSONException("Empty AI response");
        }

        String normalized = rawResponse.trim();
        Matcher matcher = CODE_FENCE_PATTERN.matcher(normalized);
        if (matcher.find()) {
            normalized = matcher.group(1).trim();
        }

        int start = normalized.indexOf('[');
        int end = normalized.lastIndexOf(']');
        if (start >= 0 && end > start) {
            normalized = normalized.substring(start, end + 1);
        }

        JSONArray jsonArray = new JSONArray(normalized);
        List<String> steps = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            String step = jsonArray.optString(i, "").trim();
            if (!step.isEmpty()) {
                steps.add(step);
            }
            if (steps.size() == 5) {
                break;
            }
        }

        if (steps.isEmpty()) {
            throw new JSONException("No valid breakdown steps");
        }

        return steps;
    }

    public static String mergeChecklistIntoDescription(String currentDescription, List<String> steps) {
        StringBuilder builder = new StringBuilder();
        if (currentDescription != null && !currentDescription.trim().isEmpty()) {
            builder.append(currentDescription.trim()).append("\n\n");
        }

        builder.append("AI Breakdown:").append("\n");
        for (String step : steps) {
            builder.append("[ ] ").append(step).append("\n");
        }

        return builder.toString().trim();
    }
}