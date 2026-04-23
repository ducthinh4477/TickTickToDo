package hcmute.edu.vn.tickticktodo.agent.scheduler.model;

public class PlanBlock {

    public static final String BLOCK_TYPE_WORK = "WORK";
    public static final String BLOCK_TYPE_BUFFER = "BUFFER";

    private final String optionId;
    private final long taskId;
    private final String taskTitle;
    private final long startMillis;
    private final long endMillis;
    private final String blockType;
    private final String note;

    public PlanBlock(String optionId,
                     long taskId,
                     String taskTitle,
                     long startMillis,
                     long endMillis,
                     String blockType,
                     String note) {
        this.optionId = optionId == null ? "" : optionId;
        this.taskId = taskId;
        this.taskTitle = taskTitle == null ? "" : taskTitle;
        this.startMillis = Math.min(startMillis, endMillis);
        this.endMillis = Math.max(startMillis, endMillis);
        this.blockType = blockType == null ? BLOCK_TYPE_WORK : blockType;
        this.note = note == null ? "" : note;
    }

    public String getOptionId() {
        return optionId;
    }

    public long getTaskId() {
        return taskId;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public String getBlockType() {
        return blockType;
    }

    public String getNote() {
        return note;
    }

    public int getDurationMinutes() {
        long duration = Math.max(0L, endMillis - startMillis);
        return (int) Math.max(0L, duration / 60000L);
    }
}
