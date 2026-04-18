package hcmute.edu.vn.tickticktodo.agent.scheduler.model;

public class ConflictItem {

    public static final String TYPE_SLOT_OVERLAP = "SLOT_OVERLAP";
    public static final String TYPE_OVERLOAD = "OVERLOAD";
    public static final String TYPE_DEADLINE_INFEASIBLE = "DEADLINE_INFEASIBLE";

    public static final String SEVERITY_INFO = "INFO";
    public static final String SEVERITY_WARN = "WARN";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    private final String type;
    private final String severity;
    private final String message;
    private final long taskId;
    private final long startMillis;
    private final long endMillis;
    private final int requiredMinutes;
    private final int availableMinutes;

    public ConflictItem(String type,
                        String severity,
                        String message,
                        long taskId,
                        long startMillis,
                        long endMillis,
                        int requiredMinutes,
                        int availableMinutes) {
        this.type = type == null ? "UNKNOWN" : type;
        this.severity = severity == null ? SEVERITY_WARN : severity;
        this.message = message == null ? "" : message;
        this.taskId = taskId;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.requiredMinutes = requiredMinutes;
        this.availableMinutes = availableMinutes;
    }

    public String getType() {
        return type;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public long getTaskId() {
        return taskId;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public int getRequiredMinutes() {
        return requiredMinutes;
    }

    public int getAvailableMinutes() {
        return availableMinutes;
    }
}
