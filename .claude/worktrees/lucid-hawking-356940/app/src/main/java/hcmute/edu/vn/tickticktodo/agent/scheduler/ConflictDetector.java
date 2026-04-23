package hcmute.edu.vn.doinbot.agent.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import hcmute.edu.vn.doinbot.agent.scheduler.model.ConflictItem;
import hcmute.edu.vn.doinbot.agent.scheduler.model.ConflictReport;
import hcmute.edu.vn.doinbot.agent.scheduler.model.SchedulableTask;
import hcmute.edu.vn.doinbot.agent.scheduler.model.TimeSlot;

public class ConflictDetector {

    private static final long MINUTE_MILLIS = 60000L;

    public ConflictReport detectConflicts(List<SchedulableTask> tasks, List<TimeSlot> timeSlots) {
        List<SchedulableTask> safeTasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        List<TimeSlot> safeSlots = timeSlots == null ? new ArrayList<>() : new ArrayList<>(timeSlots);

        List<ConflictItem> conflicts = new ArrayList<>();
        int totalRequiredMinutes = 0;
        int totalFreeMinutes = 0;

        for (SchedulableTask task : safeTasks) {
            if (task == null) {
                continue;
            }
            totalRequiredMinutes += Math.max(0, task.getEstimatedDurationMin());
        }

        List<TimeSlot> sortedSlots = new ArrayList<>(safeSlots);
        sortedSlots.sort(Comparator.comparingLong(TimeSlot::getStartMillis)
                .thenComparingLong(TimeSlot::getEndMillis));

        for (int i = 1; i < sortedSlots.size(); i++) {
            TimeSlot previous = sortedSlots.get(i - 1);
            TimeSlot current = sortedSlots.get(i);
            if (previous == null || current == null) {
                continue;
            }
            if (current.getStartMillis() < previous.getEndMillis()) {
                conflicts.add(new ConflictItem(
                        ConflictItem.TYPE_SLOT_OVERLAP,
                        ConflictItem.SEVERITY_WARN,
                        "Khung giờ bị chồng lấn, cần rà lại dữ liệu đầu vào.",
                        -1L,
                        current.getStartMillis(),
                        previous.getEndMillis(),
                        0,
                        0
                ));
            }
        }

        List<TimeSlot> freeSlots = new ArrayList<>();
        for (TimeSlot slot : sortedSlots) {
            if (slot == null || !slot.isFree()) {
                continue;
            }
            if (slot.getEndMillis() <= slot.getStartMillis()) {
                continue;
            }
            freeSlots.add(slot);
            totalFreeMinutes += slot.getDurationMinutes();
        }

        int overloadMinutes = Math.max(0, totalRequiredMinutes - totalFreeMinutes);
        if (overloadMinutes > 0) {
            conflicts.add(new ConflictItem(
                    ConflictItem.TYPE_OVERLOAD,
                    ConflictItem.SEVERITY_CRITICAL,
                    "Tổng thời lượng task vượt quá quỹ giờ trống.",
                    -1L,
                    0L,
                    0L,
                    totalRequiredMinutes,
                    totalFreeMinutes
            ));
        }

        List<MutableRange> freeRanges = toRanges(freeSlots);
        List<SchedulableTask> tasksByDeadline = new ArrayList<>(safeTasks);
        tasksByDeadline.sort((a, b) -> {
            long deadlineA = normalizeDeadline(a == null ? 0L : a.getDeadlineMillis());
            long deadlineB = normalizeDeadline(b == null ? 0L : b.getDeadlineMillis());
            int byDeadline = Long.compare(deadlineA, deadlineB);
            if (byDeadline != 0) {
                return byDeadline;
            }
            int priorityA = a == null ? 0 : a.getPriority();
            int priorityB = b == null ? 0 : b.getPriority();
            return Integer.compare(priorityB, priorityA);
        });

        int infeasibleDeadlineCount = 0;

        for (SchedulableTask task : tasksByDeadline) {
            if (task == null) {
                continue;
            }

            long deadline = normalizeDeadline(task.getDeadlineMillis());
            int required = task.getEstimatedDurationMin();
            int remaining = required;
            int allocatedBeforeDeadline = 0;

            for (MutableRange range : freeRanges) {
                if (range == null || remaining <= 0) {
                    continue;
                }

                if (range.endMillis <= range.startMillis) {
                    continue;
                }

                if (range.startMillis >= deadline) {
                    continue;
                }

                long usableEnd = Math.min(range.endMillis, deadline);
                if (usableEnd <= range.startMillis) {
                    continue;
                }

                int available = toMinutes(usableEnd - range.startMillis);
                if (available <= 0) {
                    continue;
                }

                int consume = Math.min(remaining, available);
                remaining -= consume;
                allocatedBeforeDeadline += consume;
                range.startMillis += consume * MINUTE_MILLIS;
            }

            if (remaining > 0 && deadline != Long.MAX_VALUE) {
                infeasibleDeadlineCount++;
                conflicts.add(new ConflictItem(
                        ConflictItem.TYPE_DEADLINE_INFEASIBLE,
                        ConflictItem.SEVERITY_CRITICAL,
                        "Task không đủ quỹ giờ trước hạn chót.",
                        task.getTaskId(),
                        0L,
                        deadline,
                        required,
                        allocatedBeforeDeadline
                ));
            }
        }

        return new ConflictReport(
                conflicts,
                totalRequiredMinutes,
                totalFreeMinutes,
                overloadMinutes,
                infeasibleDeadlineCount
        );
    }

    private List<MutableRange> toRanges(List<TimeSlot> freeSlots) {
        List<MutableRange> ranges = new ArrayList<>();
        for (TimeSlot slot : freeSlots) {
            if (slot == null) {
                continue;
            }
            ranges.add(new MutableRange(slot.getStartMillis(), slot.getEndMillis()));
        }
        ranges.sort(Comparator.comparingLong(a -> a.startMillis));
        return ranges;
    }

    private int toMinutes(long millis) {
        return (int) Math.max(0L, millis / MINUTE_MILLIS);
    }

    private long normalizeDeadline(long deadlineMillis) {
        return deadlineMillis > 0L ? deadlineMillis : Long.MAX_VALUE;
    }

    private static class MutableRange {
        long startMillis;
        final long endMillis;

        MutableRange(long startMillis, long endMillis) {
            this.startMillis = Math.min(startMillis, endMillis);
            this.endMillis = Math.max(startMillis, endMillis);
        }
    }
}
