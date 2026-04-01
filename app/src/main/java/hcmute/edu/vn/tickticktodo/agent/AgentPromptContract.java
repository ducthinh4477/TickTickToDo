package hcmute.edu.vn.tickticktodo.agent;

public final class AgentPromptContract {

    public static final String STANDARD_ASSISTANT_PROMPT =
            "Bạn là trợ lý cá nhân của TickTickToDo. Hãy ưu tiên hành động chính xác cho ứng dụng. " +
            "Bạn PHẢI trả về JSON thuần, không markdown, không văn bản ngoài JSON. " +
            "Schema bắt buộc: " +
            "{\"action\":\"CREATE_TASK|COMPLETE_TASK|LIST_TODAY|CHAT\",\"payload\":{...},\"reply\":\"...\"}. " +
            "Quy tắc: " +
            "1) CREATE_TASK payload gồm title (string, bắt buộc), description (string), dueDate (long millis), priority (0-3). " +
            "2) COMPLETE_TASK payload dùng id (long) hoặc title (string). " +
            "3) LIST_TODAY payload có thể rỗng. " +
            "4) CHAT dùng cho hội thoại thông thường. " +
            "5) Nếu thông tin thời gian mơ hồ, vẫn chọn giá trị hợp lý gần nhất thay vì bỏ trống.";

    public static final String FLOATING_ASSISTANT_PROMPT =
            "Bạn là trợ lý nổi của TickTickToDo. " +
            "Bạn PHẢI trả về JSON thuần, không markdown. " +
            "Schema bắt buộc: " +
            "{\"action\":\"CREATE_TASK|COMPLETE_TASK|LIST_TODAY|CHAT|WIFI_ON|WIFI_OFF\",\"payload\":{...},\"reply\":\"...\"}. " +
            "Quy tắc: " +
            "1) CREATE_TASK payload gồm title (string, bắt buộc), description (string), dueDate (long millis), priority (0-3). " +
            "2) COMPLETE_TASK payload dùng id (long) hoặc title (string). " +
            "3) LIST_TODAY payload có thể rỗng. " +
            "4) WIFI_ON/WIFI_OFF không bắt buộc payload. " +
            "5) CHAT cho hội thoại thường.";

    private AgentPromptContract() {
    }

    public static String buildPrompt(String systemPrompt, String runtimeContext, String memoryBlock, String userInput) {
        String safeSystemPrompt = nullToEmpty(systemPrompt);
        String safeRuntimeContext = nullToEmpty(runtimeContext);
        String safeMemoryBlock = nullToEmpty(memoryBlock);
        String safeUserInput = nullToEmpty(userInput);

        return safeSystemPrompt
                + "\n\n[APP_CONTEXT]\n"
                + safeRuntimeContext
                + "\n\n[MEMORY]\n"
                + safeMemoryBlock
                + "\n\n[USER_INPUT]\n"
                + safeUserInput;
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}