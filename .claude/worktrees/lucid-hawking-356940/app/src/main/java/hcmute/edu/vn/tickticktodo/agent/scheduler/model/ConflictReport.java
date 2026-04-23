package hcmute.edu.vn.doinbot.agent.scheduler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConflictReport {

    private final List<ConflictItem> items;
    private final int totalRequiredMinutes;
    private final int totalFreeMinutes;
    private final int overloadMinutes;
    private final int infeasibleDeadlineCount;

    public ConflictReport(List<ConflictItem> items,
                          int totalRequiredMinutes,
                          int totalFreeMinutes,
                          int overloadMinutes,
                          int infeasibleDeadlineCount) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
        this.totalRequiredMinutes = Math.max(0, totalRequiredMinutes);
        this.totalFreeMinutes = Math.max(0, totalFreeMinutes);
        this.overloadMinutes = Math.max(0, overloadMinutes);
        this.infeasibleDeadlineCount = Math.max(0, infeasibleDeadlineCount);
    }

    public List<ConflictItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public int getTotalRequiredMinutes() {
        return totalRequiredMinutes;
    }

    public int getTotalFreeMinutes() {
        return totalFreeMinutes;
    }

    public int getOverloadMinutes() {
        return overloadMinutes;
    }

    public int getInfeasibleDeadlineCount() {
        return infeasibleDeadlineCount;
    }

    public boolean hasOverlaps() {
        return hasType(ConflictItem.TYPE_SLOT_OVERLAP);
    }

    public boolean hasOverload() {
        return hasType(ConflictItem.TYPE_OVERLOAD);
    }

    public boolean hasDeadlineInfeasible() {
        return hasType(ConflictItem.TYPE_DEADLINE_INFEASIBLE);
    }

    public boolean isFeasible() {
        return !hasOverload() && !hasDeadlineInfeasible();
    }

    private boolean hasType(String type) {
        for (ConflictItem item : items) {
            if (item != null && type.equals(item.getType())) {
                return true;
            }
        }
        return false;
    }
}
