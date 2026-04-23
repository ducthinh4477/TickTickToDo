package hcmute.edu.vn.tickticktodo.agent.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ConflictReport;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.SchedulableTask;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ScheduleProposal;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.TimeSlot;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;

public class WeeklyPlanGenerator extends BasePlanGenerator {

    public ScheduleProposal generate(LocalDate weekAnchorDate,
                                     List<SchedulableTask> tasks,
                                     List<TimeSlot> timeSlots,
                                     UserProfileEntity profile,
                                     ConflictReport conflictReport) {
        LocalDate anchorDate = weekAnchorDate == null ? LocalDate.now() : weekAnchorDate;
        ZoneId zoneId = ZoneId.systemDefault();

        long startMillis = anchorDate
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli();
        long endMillis = anchorDate
                .plusDays(SchedulerConfig.WEEKLY_WINDOW_DAYS)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli();

        return buildProposal(
                ScheduleProposal.TYPE_WEEKLY,
                anchorDate,
                startMillis,
                endMillis,
                tasks,
                timeSlots,
                profile,
                conflictReport
        );
    }
}
