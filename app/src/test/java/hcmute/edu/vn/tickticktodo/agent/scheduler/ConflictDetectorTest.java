package hcmute.edu.vn.tickticktodo.agent.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ConflictItem;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ConflictReport;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.SchedulableTask;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.TimeSlot;

public class ConflictDetectorTest {

    @Test
    public void detectConflicts_returnsNoConflict_whenFreeSlotsAreEnoughAndNoOverlap() {
        ConflictDetector detector = new ConflictDetector();
        LocalDate date = LocalDate.of(2026, 4, 20);

        List<SchedulableTask> tasks = new ArrayList<>();
        tasks.add(createTask(1L, "Write summary", 60, 2, 0L, false));
        tasks.add(createTask(2L, "Review docs", 45, 1, 0L, false));

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slot(date, 9, 0, 11, 0, true));
        slots.add(slot(date, 14, 0, 16, 0, true));

        ConflictReport report = detector.detectConflicts(tasks, slots);

        assertEquals(0, report.getItems().size());
        assertEquals(105, report.getTotalRequiredMinutes());
        assertEquals(240, report.getTotalFreeMinutes());
        assertFalse(report.hasOverlaps());
        assertFalse(report.hasOverload());
        assertFalse(report.hasDeadlineInfeasible());
        assertTrue(report.isFeasible());
    }

    @Test
    public void detectConflicts_reportsOverload_whenTotalWorkExceedsFreeMinutes() {
        ConflictDetector detector = new ConflictDetector();
        LocalDate date = LocalDate.of(2026, 4, 21);

        List<SchedulableTask> tasks = new ArrayList<>();
        tasks.add(createTask(11L, "Deep work", 180, 3, 0L, true));
        tasks.add(createTask(12L, "Essay", 120, 3, 0L, true));

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slot(date, 9, 0, 11, 0, true));
        slots.add(slot(date, 13, 0, 14, 0, true));

        ConflictReport report = detector.detectConflicts(tasks, slots);

        assertTrue(report.hasOverload());
        assertEquals(300, report.getTotalRequiredMinutes());
        assertEquals(180, report.getTotalFreeMinutes());
        assertEquals(120, report.getOverloadMinutes());
        assertEquals(1, countByType(report, ConflictItem.TYPE_OVERLOAD));
    }

    @Test
    public void detectConflicts_reportsDeadlineInfeasible_whenSlackIsNegative() {
        ConflictDetector detector = new ConflictDetector();
        LocalDate date = LocalDate.of(2026, 4, 22);

        long deadline = toMillis(date, 11, 0);

        List<SchedulableTask> tasks = new ArrayList<>();
        tasks.add(createTask(21L, "Prepare presentation", 120, 3, deadline, true));

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slot(date, 9, 0, 10, 0, true));
        slots.add(slot(date, 12, 0, 14, 0, true));

        ConflictReport report = detector.detectConflicts(tasks, slots);

        assertFalse(report.hasOverload());
        assertTrue(report.hasDeadlineInfeasible());
        assertEquals(1, report.getInfeasibleDeadlineCount());
        assertEquals(1, countByType(report, ConflictItem.TYPE_DEADLINE_INFEASIBLE));
    }

    private SchedulableTask createTask(long id,
                                       String title,
                                       int durationMin,
                                       int priority,
                                       long deadlineMillis,
                                       boolean splitAllowed) {
        return new SchedulableTask(
                id,
                title,
                durationMin,
                priority,
                deadlineMillis,
                splitAllowed,
                25,
                45,
                SchedulableTask.ENERGY_MEDIUM,
                SchedulableTask.TASK_TYPE_GENERAL
        );
    }

    private TimeSlot slot(LocalDate date,
                          int startHour,
                          int startMinute,
                          int endHour,
                          int endMinute,
                          boolean isFree) {
        return new TimeSlot(
                toMillis(date, startHour, startMinute),
                toMillis(date, endHour, endMinute),
                isFree,
                "test",
                ""
        );
    }

    private long toMillis(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private int countByType(ConflictReport report, String type) {
        int count = 0;
        for (ConflictItem item : report.getItems()) {
            if (item != null && type.equals(item.getType())) {
                count++;
            }
        }
        return count;
    }
}
