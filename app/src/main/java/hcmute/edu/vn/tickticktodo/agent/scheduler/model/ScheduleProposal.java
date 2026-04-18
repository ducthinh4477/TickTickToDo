package hcmute.edu.vn.tickticktodo.agent.scheduler.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScheduleProposal {

    public static final String TYPE_DAILY = "DAILY";
    public static final String TYPE_WEEKLY = "WEEKLY";

    private final String proposalId;
    private final String proposalType;
    private final LocalDate anchorDate;
    private final long windowStartMillis;
    private final long windowEndMillis;
    private final long generatedAtMillis;
    private final ConflictReport conflictReport;
    private final List<PlanOption> options;

    public ScheduleProposal(String proposalId,
                            String proposalType,
                            LocalDate anchorDate,
                            long windowStartMillis,
                            long windowEndMillis,
                            long generatedAtMillis,
                            ConflictReport conflictReport,
                            List<PlanOption> options) {
        this.proposalId = proposalId == null ? "" : proposalId;
        this.proposalType = proposalType == null ? TYPE_DAILY : proposalType;
        this.anchorDate = anchorDate;
        this.windowStartMillis = Math.min(windowStartMillis, windowEndMillis);
        this.windowEndMillis = Math.max(windowStartMillis, windowEndMillis);
        this.generatedAtMillis = generatedAtMillis;
        this.conflictReport = conflictReport;
        this.options = options == null ? new ArrayList<>() : new ArrayList<>(options);
    }

    public String getProposalId() {
        return proposalId;
    }

    public String getProposalType() {
        return proposalType;
    }

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public long getWindowStartMillis() {
        return windowStartMillis;
    }

    public long getWindowEndMillis() {
        return windowEndMillis;
    }

    public long getGeneratedAtMillis() {
        return generatedAtMillis;
    }

    public ConflictReport getConflictReport() {
        return conflictReport;
    }

    public List<PlanOption> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public PlanOption getOptionById(String optionId) {
        if (optionId == null) {
            return null;
        }
        for (PlanOption option : options) {
            if (option != null && optionId.equalsIgnoreCase(option.getOptionId())) {
                return option;
            }
        }
        return null;
    }
}
