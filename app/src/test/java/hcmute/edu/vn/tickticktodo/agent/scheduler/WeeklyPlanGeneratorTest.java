package hcmute.edu.vn.tickticktodo.agent.scheduler;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ConflictReport;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.PlanBlock;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.PlanOption;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.SchedulableTask;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ScheduleProposal;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.TimeSlot;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;

public class WeeklyPlanGeneratorTest {

    @Test
    public void generate_distributesTasksAcrossMultipleDays_basedOnDeadlines() {
        LocalDate anchor = LocalDate.of(2026, 4, 20);
        WeeklyPlanGenerator generator = new WeeklyPlanGenerator();
        ConflictDetector detector = new ConflictDetector();

        List<SchedulableTask> tasks = new ArrayList<>();
        tasks.add(createTask(1L, "Early task", 60, 3, toMillis(anchor, 0, 12, 0), false));
        tasks.add(createTask(2L, "Mid task", 60, 2, toMillis(anchor, 2, 12, 0), false));
        tasks.add(createTask(3L, "Late task", 60, 1, toMillis(anchor, 5, 12, 0), false));

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slot(anchor, 0, 9, 0, 10, 0));
        slots.add(slot(anchor, 2, 9, 0, 10, 0));
        slots.add(slot(anchor, 5, 9, 0, 10, 0));

        ConflictReport report = detector.detectConflicts(tasks, slots);
        ScheduleProposal proposal = generator.generate(anchor, tasks, slots, createProfile(), report);

        PlanOption aggressive = proposal.getOptionById("AGGRESSIVE");
        assertNotNull(aggressive);

        long earlyStart = firstBlockStart(aggressive.getBlocks(), 1L);
        long midStart = firstBlockStart(aggressive.getBlocks(), 2L);
        long lateStart = firstBlockStart(aggressive.getBlocks(), 3L);

        assertTrue(earlyStart > 0L);
        assertTrue(midStart > 0L);
        assertTrue(lateStart > 0L);
        assertTrue(earlyStart < midStart);
        assertTrue(midStart < lateStart);
        assertTrue(countDistinctDays(aggressive.getBlocks()) >= 2);
    }

    @Test
    public void generate_keepsConflictReport_whenWeeklyWorkloadExceedsFreeSlots() {
        LocalDate anchor = LocalDate.of(2026, 4, 27);
        WeeklyPlanGenerator generator = new WeeklyPlanGenerator();
        ConflictDetector detector = new ConflictDetector();

        List<SchedulableTask> tasks = new ArrayList<>();
        tasks.add(createTask(11L, "Heavy 1", 180, 3, toMillis(anchor, 1, 18, 0), true));
        tasks.add(createTask(12L, "Heavy 2", 180, 3, toMillis(anchor, 2, 18, 0), true));
        tasks.add(createTask(13L, "Heavy 3", 180, 2, toMillis(anchor, 3, 18, 0), true));
        tasks.add(createTask(14L, "Heavy 4", 180, 2, toMillis(anchor, 4, 18, 0), true));

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slot(anchor, 0, 9, 0, 10, 0));
        slots.add(slot(anchor, 1, 9, 0, 10, 0));
        slots.add(slot(anchor, 2, 9, 0, 10, 0));
        slots.add(slot(anchor, 3, 9, 0, 10, 0));
        slots.add(slot(anchor, 4, 9, 0, 10, 0));

        ConflictReport conflictReport = detector.detectConflicts(tasks, slots);
        ScheduleProposal proposal = generator.generate(anchor, tasks, slots, createProfile(), conflictReport);

        assertTrue(conflictReport.hasOverload());
        assertNotNull(proposal.getConflictReport());
        assertTrue(proposal.getConflictReport().hasOverload());
        assertTrue(proposal.getConflictReport().getOverloadMinutes() > 0);
    }

    private UserProfileEntity createProfile() {
        UserProfileEntity profile = new UserProfileEntity();
        profile.focusStartHour = 9;
        profile.focusEndHour = 12;
        profile.preferredSessionMinutes = 45;
        return profile;
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
                SchedulableTask.ENERGY_HIGH_FOCUS,
                SchedulableTask.TASK_TYPE_DEEP_WORK
        );
    }

    private TimeSlot slot(LocalDate anchor,
                          int dayOffset,
                          int startHour,
                          int startMinute,
                          int endHour,
                          int endMinute) {
        return new TimeSlot(
                toMillis(anchor, dayOffset, startHour, startMinute),
                toMillis(anchor, dayOffset, endHour, endMinute),
                true,
                "test",
                ""
        );
    }

    private long toMillis(LocalDate anchor, int dayOffset, int hour, int minute) {
        LocalDate target = anchor.plusDays(dayOffset);
        return target.atTime(hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private long firstBlockStart(List<PlanBlock> blocks, long taskId) {
        long minStart = Long.MAX_VALUE;
        for (PlanBlock block : blocks) {
            if (block == null || block.getTaskId() != taskId) {
                continue;
            }
            minStart = Math.min(minStart, block.getStartMillis());
        }
        return minStart == Long.MAX_VALUE ? -1L : minStart;
    }

    private int countDistinctDays(List<PlanBlock> blocks) {
        Set<LocalDate> days = new HashSet<>();
        for (PlanBlock block : blocks) {
            if (block == null) {
                continue;
            }
            LocalDate day = Instant.ofEpochMilli(block.getStartMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            days.add(day);
        }
        return days.size();
    }
}
