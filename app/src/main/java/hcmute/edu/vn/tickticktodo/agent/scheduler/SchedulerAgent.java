package hcmute.edu.vn.tickticktodo.agent.scheduler;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.agent.context.ContextAgent;
import hcmute.edu.vn.tickticktodo.agent.context.ContextSnapshot;
import hcmute.edu.vn.tickticktodo.agent.profile.ProfileAgent;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ConflictReport;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.PlanBlock;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.PlanOption;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.SchedulableTask;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ScheduleProposal;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.TimeSlot;
import hcmute.edu.vn.tickticktodo.data.dao.ScheduleProposalDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.data.model.ScheduleBlockEntity;
import hcmute.edu.vn.tickticktodo.data.model.ScheduleProposalEntity;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;
import hcmute.edu.vn.tickticktodo.data.repository.TaskRepository;
import hcmute.edu.vn.tickticktodo.model.Task;

public class SchedulerAgent {

    public static final class ApplyPlanResult {
        public final boolean success;
        public final int appliedTaskCount;
        public final String message;

        ApplyPlanResult(boolean success, int appliedTaskCount, String message) {
            this.success = success;
            this.appliedTaskCount = appliedTaskCount;
            this.message = message == null ? "" : message;
        }
    }

    private static final long MINUTE_MILLIS = 60000L;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;
    private static volatile SchedulerAgent INSTANCE;

    private final Context appContext;
    private final TaskDatabase database;
    private final TaskRepository taskRepository;
    private final ProfileAgent profileAgent;
    private final ContextAgent contextAgent;
    private final ConflictDetector conflictDetector;
    private final DailyPlanGenerator dailyPlanGenerator;
    private final WeeklyPlanGenerator weeklyPlanGenerator;

    private SchedulerAgent(Context context) {
        this.appContext = context.getApplicationContext();
        this.database = TaskDatabase.getInstance(appContext);
        this.taskRepository = new TaskRepository((android.app.Application) appContext);
        this.profileAgent = ProfileAgent.getInstance(appContext);
        this.contextAgent = ContextAgent.getInstance(appContext);
        this.conflictDetector = new ConflictDetector();
        this.dailyPlanGenerator = new DailyPlanGenerator();
        this.weeklyPlanGenerator = new WeeklyPlanGenerator();
    }

    public static SchedulerAgent getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SchedulerAgent.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SchedulerAgent(context);
                }
            }
        }
        return INSTANCE;
    }

    public ScheduleProposal proposeDailyPlan(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        UserProfileEntity profile = profileAgent.getCurrentProfile();
        List<Task> tasks = getDailyCandidateTasks(targetDate);
        List<SchedulableTask> schedulableTasks = mapToSchedulableTasks(tasks, profile, targetDate, true);
        List<TimeSlot> slots = buildDailyTimeSlots(targetDate, profile, tasks);

        ConflictReport conflictReport = conflictDetector.detectConflicts(schedulableTasks, slots);
        ScheduleProposal proposal = dailyPlanGenerator.generate(
                targetDate,
                schedulableTasks,
                slots,
                profile,
                conflictReport
        );

        persistProposal(proposal);
        return proposal;
    }

    public ScheduleProposal proposeWeeklyPlan(LocalDate weekAnchorDate) {
        LocalDate anchor = weekAnchorDate == null ? LocalDate.now() : weekAnchorDate;
        UserProfileEntity profile = profileAgent.getCurrentProfile();
        List<Task> tasks = getWeeklyCandidateTasks(anchor);
        List<SchedulableTask> schedulableTasks = mapToSchedulableTasks(tasks, profile, anchor, false);
        List<TimeSlot> slots = buildWeeklyTimeSlots(anchor, profile, tasks);

        ConflictReport conflictReport = conflictDetector.detectConflicts(schedulableTasks, slots);
        ScheduleProposal proposal = weeklyPlanGenerator.generate(
                anchor,
                schedulableTasks,
                slots,
                profile,
                conflictReport
        );

        persistProposal(proposal);
        return proposal;
    }

    public ApplyPlanResult applyPlanOption(String proposalId, String optionId) {
        if (TextUtils.isEmpty(proposalId) || TextUtils.isEmpty(optionId)) {
            return new ApplyPlanResult(false, 0, "Thiếu proposalId hoặc optionId.");
        }

        ScheduleProposalDao dao = database.scheduleProposalDao();
        ScheduleProposalEntity proposalEntity = dao.getProposalByIdSync(proposalId);
        if (proposalEntity == null) {
            return new ApplyPlanResult(false, 0, "Không tìm thấy bản kế hoạch cần áp dụng.");
        }

        List<ScheduleBlockEntity> targetBlocks = dao.getBlocksForOptionSync(proposalId, optionId);
        if (targetBlocks == null || targetBlocks.isEmpty()) {
            List<ScheduleBlockEntity> allBlocks = dao.getBlocksByProposalIdSync(proposalId);
            targetBlocks = new ArrayList<>();
            if (allBlocks != null) {
                for (ScheduleBlockEntity block : allBlocks) {
                    if (block == null || TextUtils.isEmpty(block.optionId)) {
                        continue;
                    }
                    if (optionId.equalsIgnoreCase(block.optionId)) {
                        targetBlocks.add(block);
                    }
                }
            }
        }

        if (targetBlocks.isEmpty()) {
            return new ApplyPlanResult(false, 0, "Không tìm thấy block cho option đã chọn.");
        }

        int appliedCount = 0;
        for (ScheduleBlockEntity block : targetBlocks) {
            if (block == null || block.taskId == null || block.taskId <= 0L) {
                continue;
            }

            Task task = database.taskDao().getTaskByIdSync(block.taskId);
            if (task == null || task.isCompleted()) {
                continue;
            }

            task.setDueDate(block.startMillis);
            taskRepository.update(task);
            appliedCount++;
        }

        dao.markProposalApplied(
                proposalId,
                ScheduleProposalEntity.STATUS_APPLIED,
                optionId,
                System.currentTimeMillis()
        );

        return new ApplyPlanResult(true, appliedCount,
                "Đã áp dụng option " + optionId + " cho " + appliedCount + " task.");
    }

    private void persistProposal(ScheduleProposal proposal) {
        if (proposal == null || TextUtils.isEmpty(proposal.getProposalId())) {
            return;
        }

        ScheduleProposalDao dao = database.scheduleProposalDao();

        ScheduleProposalEntity proposalEntity = new ScheduleProposalEntity();
        proposalEntity.id = proposal.getProposalId();
        proposalEntity.proposalType = proposal.getProposalType();
        proposalEntity.anchorDate = proposal.getAnchorDate() == null ? "" : proposal.getAnchorDate().toString();
        proposalEntity.generatedAtMillis = proposal.getGeneratedAtMillis();
        proposalEntity.windowStartMillis = proposal.getWindowStartMillis();
        proposalEntity.windowEndMillis = proposal.getWindowEndMillis();
        proposalEntity.conflictReportJson = SchedulerJsonMapper.toConflictReportJson(proposal.getConflictReport()).toString();
        proposalEntity.optionsJson = SchedulerJsonMapper.toOptionsJson(proposal.getOptions(), false).toString();
        proposalEntity.status = ScheduleProposalEntity.STATUS_PENDING;
        proposalEntity.appliedOptionId = null;
        proposalEntity.appliedAtMillis = 0L;

        dao.upsertProposal(proposalEntity);
        dao.deleteBlocksByProposalId(proposalEntity.id);

        List<ScheduleBlockEntity> blockEntities = new ArrayList<>();
        for (PlanOption option : proposal.getOptions()) {
            if (option == null || option.getBlocks() == null) {
                continue;
            }

            for (PlanBlock block : option.getBlocks()) {
                if (block == null) {
                    continue;
                }

                ScheduleBlockEntity entity = new ScheduleBlockEntity();
                entity.proposalId = proposalEntity.id;
                entity.optionId = option.getOptionId();
                entity.taskId = block.getTaskId() > 0L ? block.getTaskId() : null;
                entity.taskTitle = block.getTaskTitle();
                entity.startMillis = block.getStartMillis();
                entity.endMillis = block.getEndMillis();
                entity.blockType = block.getBlockType();
                entity.note = block.getNote();
                blockEntities.add(entity);
            }
        }

        if (!blockEntities.isEmpty()) {
            dao.insertBlocks(blockEntities);
        }
    }

    private List<Task> getDailyCandidateTasks(LocalDate date) {
        List<Task> all = safeTasks(database.taskDao().getAllTasksSync());
        List<Task> candidates = new ArrayList<>();

        long dayEnd = date.plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long nextThreeDays = dayEnd + (3L * DAY_MILLIS);

        for (Task task : all) {
            if (task == null || task.isCompleted()) {
                continue;
            }

            Long due = task.getDueDate();
            if (due != null && due <= nextThreeDays) {
                candidates.add(task);
                continue;
            }

            if (task.getPriority() >= 2) {
                candidates.add(task);
            }
        }

        candidates.sort(Comparator
                .comparingInt(Task::getPriority).reversed()
                .thenComparingLong(task -> task.getDueDate() == null ? Long.MAX_VALUE : task.getDueDate()));

        if (candidates.size() > 24) {
            return new ArrayList<>(candidates.subList(0, 24));
        }
        return candidates;
    }

    private List<Task> getWeeklyCandidateTasks(LocalDate anchorDate) {
        List<Task> all = safeTasks(database.taskDao().getAllTasksSync());
        List<Task> candidates = new ArrayList<>();

        long weekEnd = anchorDate.plusDays(7)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long grace = weekEnd + DAY_MILLIS;

        for (Task task : all) {
            if (task == null || task.isCompleted()) {
                continue;
            }

            Long due = task.getDueDate();
            if (due != null && due <= grace) {
                candidates.add(task);
                continue;
            }

            if (task.getPriority() >= 2) {
                candidates.add(task);
            }
        }

        candidates.sort(Comparator
                .comparingInt(Task::getPriority).reversed()
                .thenComparingLong(task -> task.getDueDate() == null ? Long.MAX_VALUE : task.getDueDate()));

        if (candidates.size() > 48) {
            return new ArrayList<>(candidates.subList(0, 48));
        }
        return candidates;
    }

    private List<SchedulableTask> mapToSchedulableTasks(List<Task> tasks,
                                                        UserProfileEntity profile,
                                                        LocalDate anchorDate,
                                                        boolean daily) {
        List<SchedulableTask> mapped = new ArrayList<>();
        long fallbackDeadline = (daily ? anchorDate.plusDays(1) : anchorDate.plusDays(7))
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        int preferredBlockMin = profile == null ? 45 : clamp(profile.preferredSessionMinutes, 25, 120);

        for (Task task : safeTasks(tasks)) {
            if (task == null || task.isCompleted()) {
                continue;
            }

            int estimatedDuration = resolveEstimatedDurationMin(task);
            boolean splitAllowed = estimatedDuration > Math.max(45, preferredBlockMin);
            String energyType = inferEnergyType(task);
            String taskType = inferTaskType(task);

            long deadline = task.getDueDate() == null ? fallbackDeadline : task.getDueDate();
            mapped.add(new SchedulableTask(
                    task.getId(),
                    task.getTitle(),
                    estimatedDuration,
                    task.getPriority(),
                    deadline,
                    splitAllowed,
                    25,
                    Math.min(preferredBlockMin, 90),
                    energyType,
                    taskType
            ));
        }

        return mapped;
    }

    private List<TimeSlot> buildDailyTimeSlots(LocalDate date,
                                               UserProfileEntity profile,
                                               List<Task> candidateTasks) {
        long dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        List<Interval> freeIntervals = new ArrayList<>();
        addInterval(freeIntervals, dayStart + hourToMillis(8), dayStart + hourToMillis(12));
        addInterval(freeIntervals, dayStart + hourToMillis(13), dayStart + hourToMillis(17));
        addInterval(freeIntervals, dayStart + hourToMillis(19), dayStart + hourToMillis(22));

        if (profile != null) {
            addInterval(
                    freeIntervals,
                    dayStart + hourToMillis(clamp(profile.focusStartHour, 6, 21)),
                    dayStart + hourToMillis(clamp(profile.focusEndHour, 7, 23))
            );
        }

        mergeIntervals(freeIntervals);

        List<Interval> busyIntervals = buildBusyIntervalsForDay(dayStart, dayEnd, candidateTasks);
        for (Interval busy : busyIntervals) {
            freeIntervals = subtractIntervalList(freeIntervals, busy);
        }

        ContextSnapshot snapshot = contextAgent.getLatestSnapshot();
        String placeHint = snapshot == null ? "" : snapshot.getConnectivity();

        List<TimeSlot> result = new ArrayList<>();
        for (Interval free : freeIntervals) {
            if (free == null || free.end <= free.start) {
                continue;
            }
            result.add(new TimeSlot(free.start, free.end, true, "generated_daily", placeHint));
        }

        result.sort(Comparator.comparingLong(TimeSlot::getStartMillis));
        return result;
    }

    private List<TimeSlot> buildWeeklyTimeSlots(LocalDate anchorDate,
                                                UserProfileEntity profile,
                                                List<Task> candidateTasks) {
        List<TimeSlot> slots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = anchorDate.plusDays(i);
            slots.addAll(buildDailyTimeSlots(day, profile, candidateTasks));
        }
        slots.sort(Comparator.comparingLong(TimeSlot::getStartMillis));
        return slots;
    }

    private List<Interval> buildBusyIntervalsForDay(long dayStart, long dayEnd, List<Task> tasks) {
        List<Interval> busy = new ArrayList<>();
        for (Task task : safeTasks(tasks)) {
            if (task == null || task.getDueDate() == null) {
                continue;
            }

            long due = task.getDueDate();
            if (due < dayStart || due >= dayEnd) {
                continue;
            }

            int durationMin = resolveEstimatedDurationMin(task);
            long start = Math.max(dayStart, due - durationMin * MINUTE_MILLIS);
            long end = Math.min(dayEnd, due);
            if (end > start) {
                busy.add(new Interval(start, end));
            }
        }

        busy.sort(Comparator.comparingLong(interval -> interval.start));
        return busy;
    }

    private List<Interval> subtractIntervalList(List<Interval> free, Interval busy) {
        List<Interval> result = new ArrayList<>();
        if (free == null || busy == null) {
            return result;
        }

        for (Interval slot : free) {
            if (slot == null || slot.end <= slot.start) {
                continue;
            }

            if (busy.end <= slot.start || busy.start >= slot.end) {
                result.add(slot);
                continue;
            }

            if (busy.start > slot.start) {
                result.add(new Interval(slot.start, busy.start));
            }
            if (busy.end < slot.end) {
                result.add(new Interval(busy.end, slot.end));
            }
        }

        return result;
    }

    private void mergeIntervals(List<Interval> intervals) {
        if (intervals == null || intervals.size() < 2) {
            return;
        }

        intervals.sort(Comparator.comparingLong(i -> i.start));
        List<Interval> merged = new ArrayList<>();

        Interval current = intervals.get(0);
        for (int i = 1; i < intervals.size(); i++) {
            Interval next = intervals.get(i);
            if (next.start <= current.end) {
                current.end = Math.max(current.end, next.end);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        intervals.clear();
        intervals.addAll(merged);
    }

    private void addInterval(List<Interval> intervals, long start, long end) {
        if (intervals == null) {
            return;
        }
        if (end <= start) {
            return;
        }
        intervals.add(new Interval(start, end));
    }

    private int resolveEstimatedDurationMin(Task task) {
        if (task == null) {
            return 30;
        }
        if (task.getDuration() > 0) {
            return clamp(task.getDuration(), 15, 240);
        }
        switch (task.getPriority()) {
            case 3:
                return 90;
            case 2:
                return 60;
            case 1:
                return 40;
            default:
                return 25;
        }
    }

    private String inferEnergyType(Task task) {
        if (task == null) {
            return SchedulableTask.ENERGY_MEDIUM;
        }

        String title = (task.getTitle() == null ? "" : task.getTitle()).toLowerCase(Locale.ROOT);
        if (task.getPriority() >= 3 || containsAny(title, "thi", "report", "code", "essay", "đồ án", "do an", "study")) {
            return SchedulableTask.ENERGY_HIGH_FOCUS;
        }
        if (containsAny(title, "email", "dọn", "mua", "call", "meet", "chat")) {
            return SchedulableTask.ENERGY_LOW;
        }
        return SchedulableTask.ENERGY_MEDIUM;
    }

    private String inferTaskType(Task task) {
        if (task == null) {
            return SchedulableTask.TASK_TYPE_GENERAL;
        }

        String title = (task.getTitle() == null ? "" : task.getTitle()).toLowerCase(Locale.ROOT);
        if (containsAny(title, "meet", "call", "họp", "hop")) {
            return SchedulableTask.TASK_TYPE_MEETING;
        }
        if (containsAny(title, "mua", "đi", "di", "ship", "nộp", "nop")) {
            return SchedulableTask.TASK_TYPE_ERRAND;
        }
        if (containsAny(title, "email", "form", "file", "cleanup")) {
            return SchedulableTask.TASK_TYPE_ADMIN;
        }
        if (containsAny(title, "code", "study", "đồ án", "do an", "thi", "report", "essay")) {
            return SchedulableTask.TASK_TYPE_DEEP_WORK;
        }
        return SchedulableTask.TASK_TYPE_GENERAL;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private long hourToMillis(int hour) {
        int safeHour = clamp(hour, 0, 23);
        return safeHour * 60L * 60L * 1000L;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<Task> safeTasks(List<Task> tasks) {
        return tasks == null ? new ArrayList<>() : tasks;
    }

    private static class Interval {
        long start;
        long end;

        Interval(long start, long end) {
            this.start = Math.min(start, end);
            this.end = Math.max(start, end);
        }
    }
}
