package hcmute.edu.vn.tickticktodo.model;

public class ChatMessage {
    private String text;
    private boolean isUser;
    private String actionType;
    private String actionLabel;
    private String actionData;

    public ChatMessage(String text, boolean isUser) {
        this(text, isUser, "", "", "");
    }

    public ChatMessage(String text,
                       boolean isUser,
                       String actionType,
                       String actionLabel,
                       String actionData) {
        this.text = text;
        this.isUser = isUser;
        this.actionType = actionType == null ? "" : actionType;
        this.actionLabel = actionLabel == null ? "" : actionLabel;
        this.actionData = actionData == null ? "" : actionData;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return isUser;
    }

    public String getActionType() {
        return actionType;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public String getActionData() {
        return actionData;
    }

    public boolean hasAction() {
        return !isUser && actionLabel != null && !actionLabel.trim().isEmpty();
    }
}