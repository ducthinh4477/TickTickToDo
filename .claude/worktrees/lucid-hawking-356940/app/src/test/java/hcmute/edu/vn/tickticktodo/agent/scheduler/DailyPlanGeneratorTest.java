package hcmute.edu.vn.doinbot.agent.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hcmute.edu.vn.doinbot.agent.scheduler.model.ConflictReport;
import hcmute.edu.vn.doinbot.agent.scheduler.model.PlanBlock;
import hcmute.edu.vn.doinbot.agent.scheduler.model.PlanOption;
import hcmute.edu.vn.doinbot.agent.scheduler.model.SchedulableTask;
import hcmute.edu.vn.doinbot.agent.scheduler.model.ScheduleProposal;
import hcmute.edu.vn.doinbot.agent.scheduler.model.TimeSlot;
import hcmute.edu.vn.doinbot.data.model.UserProfileEntity;

public class DailyPlanGeneratorTest {

    @Test
    public void generate_returnsExactlyThreeOptions_forTypicalDailyInputs() {
        LocalDate date = LocalDate.of(2026, 4, 23);
        DailyPlanGenerator generator = new DailyPlanGenerator();
        ConflictDetector detector = new ConflictDetector();

        List<SchedulableTask> tasks = new ArrayList<>();
        tasks.add(createTask(1L, "Task A", 40, 3, toMillis(date, 12, 0), false, 25, 45));
        tasks.add(createTask(2L, "Task B", 50, 2, toMillis(date, 16, 0), false, 25, 45));
        tasks.add(createTask(3L, "Task C", 35, 2, toMillis(date, 18, 0), false, 25, 45));
        tasks.add(createTask(4L, "Task D", 30, 1, toMillis(date, 20, 0), false, 25, 45));

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slot(date, 9, 0, 12, 0));
        slots.add(slot(date, 13, 0, 15, 0));

        ConflictReport report = detector.detectConflicts(tasks, slots);
        ScheduleProposal proposal = generator.generate(date, tasks, slots, createProfile(), report);

        assertNotNull(proposal);
        assertEquals(ScheduleProposal.TYPE_DAILY, proposal.getProposalType());
        assertEquals(3, proposal.getOptions().size());
        assertNotNull(proposal.getOptionById("AGGRESSIVE"));
        assertNotNull(proposal.getOptionById("BALANCED"));
        assertNotNull(proposal.getOptionById("LOW_STRESS"));
    }

    @Test
    public void generate_aggressiveSchedulesMoreTasksThanLowStress() {
        LocalDate date = LocalDate.of(2026, 4, 24);
        DailyPlanGenerator generator = new DailyPlanGenerator();
        ConflictDetector detector = new ConflictDetector();

        List<SchedulableTask> tasks = new ArrayList<>();
        tasks.add(createTask(11L, "Focus 1", 50, 3, toMillis(date, 12, 0), false, 25, 45));
        tasks.add(createTask(12L, "Focus 2", 50, 3, toMillis(date, 14, 0), false, 25, 45));
        tasks.add(createTask(13L, "Focus 3", 50, 2, toMillis(date, 16, 0), false, 25, 45));
        tasks.add(createTask(14L, "Focus 4", 50, 2, toMillis(date, 18, 0), false, 25, 45));

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slot(date, 9, 0, 11, 0));
        slots.add(slot(date, 13, 0, 14, 40));

        ConflictReport report = detector.detectConflicts(tasks, slots);
        ScheduleProposal proposal = generator.generate(date, tasks, slots, createProfile(), report);

        PlanOption aggressive = proposal.getOptionById("AGGRESSIVE");
        PlanOption lowStress = proposal.getOptionById("LOW_STRESS");

        assertNotNull(aggressive);
        assertNotNull(lowStress);
        assertTrue(aggressive.getScheduledMinutes() > lowStress.getScheduledMinutes());
        assertTrue(countDistinctTaskIds(aggressive.getBlocks()) > countDistinctTaskIds(lowStress.getBlocks()));
    }

    @Test
    public void generate_splitsLongTaskIntoMultipleBlocks_whenSplitAllowed() {
        LocalDate date = LocalDate.of(2026, 4, 25);
        DailyPlanGenerator generator = new DailyPlanGenerator();
        ConflictDetector detector = new ConflictDetector();

        List<SchedulableTask> tasks = new ArrayList<>();
        tasks.add(createTask(
                101L,
                "Deep focus session",
                120,
                3,
                toMillis(date, 23, 0),
                true,
                25,
                30
        ));

        List<TimeSlot> slots = new ArrayList<>();
        slots.add(slot(date, 8, 0, 11, 0));

        ConflictReport report = detector.detectConflicts(tasks, slots);
        ScheduleProposal proposal = generator.generate(date, tasks, slots, createProfile(), report);

        PlanOption aggressive = proposal.getOptionById("AGGRESSIVE");
        assertNotNull(aggressive);

        int blockCountForTask = countBlocksForTask(aggressive.getBlocks(), 101L);
        int totalDurationForTask = sumDurationForTask(aggressive.getBlocks(), 101L);

        assertTrue(blockCountForTask >= 2);
        assertEquals(120, totalDurationForTask);
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
                                       boolean splitAllowed,
                                       int minBlock,
                                       int preferredBlock) {
        return new SchedulableTask(
                id,
                title,
                durationMin,
                priority,
                deadlineMillis,
                splitAllowed,
                minBlock,
                preferredBlock,
                SchedulableTask.ENERGY_HIGH_FOCUS,
                SchedulableTask.TASK_TYPE_DEEP_WORK
        );
    }

    private TimeSlot slot(LocalDate date, int startHour, int startMinute, int endHour, int endMinute) {
        return new TimeSlot(
                toMillis(date, startHour, startMinute),
                toMillis(date, endHour, endMinute),
                true,
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

    private int countDistinctTaskIds(List<PlanBlock> blocks) {
        Set<Long> ids = new HashSet<>();
        for (PlanBlock block : blocks) {
            if (block != null && block.getTaskId() > 0L) {
                ids.add(block.getTaskId());
            }
        }
        return ids.size();
    }

    private int countBlocksForTask(List<PlanBlock> blocks, long taskId) {
        int count = 0;
        for (PlanBlock block : blocks) {
            if (block != null && block.getTaskId() == taskId) {
                count++;
            }
        }
        return count;
    }

    private int sumDurationForTask(List<PlanBlock> blocks, long taskId) {
        int sum = 0;
        for (PlanBlock block : blocks) {
            if (block != null && block.getTaskId() == taskId) {
                sum += block.getDurationMinutes();
            }
        }
        return sum;
    }
}
