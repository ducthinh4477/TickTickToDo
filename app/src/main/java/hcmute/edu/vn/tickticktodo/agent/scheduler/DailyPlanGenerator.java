package hcmute.edu.vn.tickticktodo.agent.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ConflictReport;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.SchedulableTask;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ScheduleProposal;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.TimeSlot;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;

public class DailyPlanGenerator extends BasePlanGenerator {

    public ScheduleProposal generate(LocalDate date,
                                     List<SchedulableTask> tasks,
                                     List<TimeSlot> timeSlots,
                                     UserProfileEntity profile,
                                     ConflictReport conflictReport) {
        LocalDate anchorDate = date == null ? LocalDate.now() : date;
        ZoneId zoneId = ZoneId.systemDefault();

        long startMillis = anchorDate
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli();
        long endMillis = anchorDate
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli();

        return buildProposal(
                ScheduleProposal.TYPE_DAILY,
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
