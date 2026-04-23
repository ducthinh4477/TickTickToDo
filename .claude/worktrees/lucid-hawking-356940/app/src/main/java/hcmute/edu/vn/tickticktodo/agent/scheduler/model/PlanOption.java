package hcmute.edu.vn.doinbot.agent.scheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlanOption {

    private final String optionId;
    private final String label;
    private final String description;
    private final float score;
    private final int scheduledMinutes;
    private final int unscheduledMinutes;
    private final int unscheduledTaskCount;
    private final List<PlanBlock> blocks;

    public PlanOption(String optionId,
                      String label,
                      String description,
                      float score,
                      int scheduledMinutes,
                      int unscheduledMinutes,
                      int unscheduledTaskCount,
                      List<PlanBlock> blocks) {
        this.optionId = optionId == null ? "" : optionId;
        this.label = label == null ? "" : label;
        this.description = description == null ? "" : description;
        this.score = score;
        this.scheduledMinutes = Math.max(0, scheduledMinutes);
        this.unscheduledMinutes = Math.max(0, unscheduledMinutes);
        this.unscheduledTaskCount = Math.max(0, unscheduledTaskCount);
        this.blocks = blocks == null ? new ArrayList<>() : new ArrayList<>(blocks);
    }

    public String getOptionId() {
        return optionId;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public float getScore() {
        return score;
    }

    public int getScheduledMinutes() {
        return scheduledMinutes;
    }

    public int getUnscheduledMinutes() {
        return unscheduledMinutes;
    }

    public int getUnscheduledTaskCount() {
        return unscheduledTaskCount;
    }

    public List<PlanBlock> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }
}
