package hcmute.edu.vn.tickticktodo.agent.scheduler.model;

public class SchedulableTask {

    public static final String ENERGY_HIGH_FOCUS = "HIGH_FOCUS";
    public static final String ENERGY_MEDIUM = "MEDIUM";
    public static final String ENERGY_LOW = "LOW";

    public static final String TASK_TYPE_DEEP_WORK = "DEEP_WORK";
    public static final String TASK_TYPE_ADMIN = "ADMIN";
    public static final String TASK_TYPE_MEETING = "MEETING";
    public static final String TASK_TYPE_ERRAND = "ERRAND";
    public static final String TASK_TYPE_GENERAL = "GENERAL";

    private final long taskId;
    private final String title;
    private final int estimatedDurationMin;
    private final int priority;
    private final long deadlineMillis;
    private final boolean splitAllowed;
    private final int minBlockMin;
    private final int preferredBlockMin;
    private final String energyType;
    private final String taskType;

    public SchedulableTask(long taskId,
                           String title,
                           int estimatedDurationMin,
                           int priority,
                           long deadlineMillis,
                           boolean splitAllowed,
                           int minBlockMin,
                           int preferredBlockMin,
                           String energyType,
                           String taskType) {
        this.taskId = taskId;
        this.title = title == null ? "" : title;
        this.estimatedDurationMin = Math.max(5, estimatedDurationMin);
        this.priority = clamp(priority, 0, 3);
        this.deadlineMillis = deadlineMillis;
        this.splitAllowed = splitAllowed;
        this.minBlockMin = clamp(minBlockMin, 5, 180);
        this.preferredBlockMin = clamp(preferredBlockMin, this.minBlockMin, 240);
        this.energyType = normalizeEnergy(energyType);
        this.taskType = normalizeTaskType(taskType);
    }

    public long getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public int getEstimatedDurationMin() {
        return estimatedDurationMin;
    }

    public int getPriority() {
        return priority;
    }

    public long getDeadlineMillis() {
        return deadlineMillis;
    }

    public boolean isSplitAllowed() {
        return splitAllowed;
    }

    public int getMinBlockMin() {
        return minBlockMin;
    }

    public int getPreferredBlockMin() {
        return preferredBlockMin;
    }

    public String getEnergyType() {
        return energyType;
    }

    public String getTaskType() {
        return taskType;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeEnergy(String raw) {
        if (ENERGY_HIGH_FOCUS.equalsIgnoreCase(raw)) {
            return ENERGY_HIGH_FOCUS;
        }
        if (ENERGY_LOW.equalsIgnoreCase(raw)) {
            return ENERGY_LOW;
        }
        return ENERGY_MEDIUM;
    }

    private static String normalizeTaskType(String raw) {
        if (TASK_TYPE_DEEP_WORK.equalsIgnoreCase(raw)) {
            return TASK_TYPE_DEEP_WORK;
        }
        if (TASK_TYPE_ADMIN.equalsIgnoreCase(raw)) {
            return TASK_TYPE_ADMIN;
        }
        if (TASK_TYPE_MEETING.equalsIgnoreCase(raw)) {
            return TASK_TYPE_MEETING;
        }
        if (TASK_TYPE_ERRAND.equalsIgnoreCase(raw)) {
            return TASK_TYPE_ERRAND;
        }
        return TASK_TYPE_GENERAL;
    }
}
