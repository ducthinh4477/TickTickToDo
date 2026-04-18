package hcmute.edu.vn.tickticktodo.agent.scheduler;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ConflictReport;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.PlanBlock;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.PlanOption;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.SchedulableTask;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ScheduleProposal;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.TimeSlot;
import hcmute.edu.vn.tickticktodo.data.model.UserProfileEntity;

abstract class BasePlanGenerator {

    private static final long MINUTE_MILLIS = 60000L;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    protected ScheduleProposal buildProposal(String proposalType,
                                             LocalDate anchorDate,
                                             long windowStartMillis,
                                             long windowEndMillis,
                                             List<SchedulableTask> tasks,
                                             List<TimeSlot> timeSlots,
                                             UserProfileEntity profile,
                                             ConflictReport conflictReport) {
        List<SchedulableTask> safeTasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        List<TimeSlot> safeSlots = timeSlots == null ? new ArrayList<>() : new ArrayList<>(timeSlots);

        OptionPolicy aggressive = new OptionPolicy(
                "AGGRESSIVE",
                "Aggressive",
                "Ưu tiên xử lý tối đa task gấp, chấp nhận lịch dày.",
                0.92f,
                1.2f,
                1.3f,
                0.8f,
                0.7f,
                50,
                3
        );

        OptionPolicy balanced = new OptionPolicy(
                "BALANCED",
                "Balanced",
                "Cân bằng giữa độ gấp và sức bền trong ngày.",
                0.78f,
                1.0f,
                1.0f,
                1.0f,
                0.9f,
                40,
                5
        );

        OptionPolicy lowStress = new OptionPolicy(
                "LOW_STRESS",
                "Low-stress",
                "Giữ nhịp làm việc nhẹ, ưu tiên giảm quá tải.",
                0.62f,
                0.85f,
                0.8f,
                1.2f,
                1.0f,
                30,
                8
        );

        List<PlanOption> options = new ArrayList<>();
        options.add(buildOption(aggressive, safeTasks, safeSlots, profile, windowStartMillis));
        options.add(buildOption(balanced, safeTasks, safeSlots, profile, windowStartMillis));
        options.add(buildOption(lowStress, safeTasks, safeSlots, profile, windowStartMillis));

        return new ScheduleProposal(
                UUID.randomUUID().toString(),
                proposalType,
                anchorDate,
                windowStartMillis,
                windowEndMillis,
                System.currentTimeMillis(),
                conflictReport,
                options
        );
    }

    private PlanOption buildOption(OptionPolicy policy,
                                   List<SchedulableTask> tasks,
                                   List<TimeSlot> timeSlots,
                                   UserProfileEntity profile,
                                   long windowStartMillis) {
        List<MutableSlot> freeSlots = toMutableFreeSlots(timeSlots);
        int totalFreeMinutes = totalFreeMinutes(freeSlots);
        int capacityMinutes = Math.max(0, Math.round(totalFreeMinutes * policy.fillRatio));

        List<SchedulableTask> rankedTasks = rankTasks(tasks, profile, policy, windowStartMillis);
        List<PlanBlock> blocks = new ArrayList<>();

        int scheduledMinutes = 0;
        int unscheduledMinutes = 0;
        int unscheduledTaskCount = 0;
        float scheduleQuality = 0f;

        for (SchedulableTask task : rankedTasks) {
            if (task == null) {
                continue;
            }

            int remaining = task.getEstimatedDurationMin();

            while (remaining > 0) {
                if (capacityMinutes > 0 && scheduledMinutes >= capacityMinutes) {
                    break;
                }

                MutableSlot slot = chooseBestSlot(freeSlots, task);
                if (slot == null) {
                    break;
                }

                int available = slot.getRemainingMinutes();
                if (available <= 0) {
                    freeSlots.remove(slot);
                    continue;
                }

                int targetBlock = resolveTargetBlockMinutes(task, remaining, available, policy);
                if (targetBlock <= 0) {
                    break;
                }

                if (capacityMinutes > 0 && scheduledMinutes + targetBlock > capacityMinutes) {
                    targetBlock = capacityMinutes - scheduledMinutes;
                }

                if (targetBlock <= 0) {
                    break;
                }

                long blockStart = slot.startMillis;
                long blockEnd = blockStart + targetBlock * MINUTE_MILLIS;

                blocks.add(new PlanBlock(
                        policy.optionId,
                        task.getTaskId(),
                        task.getTitle(),
                        blockStart,
                        blockEnd,
                        PlanBlock.BLOCK_TYPE_WORK,
                        slot.source
                ));

                scheduledMinutes += targetBlock;
                remaining -= targetBlock;
                scheduleQuality += computeSlotFit(task, slot);

                slot.startMillis = blockEnd;
                if (remaining > 0 && task.isSplitAllowed() && policy.breakMinutes > 0) {
                    slot.startMillis += policy.breakMinutes * MINUTE_MILLIS;
                }

                if (slot.startMillis >= slot.endMillis) {
                    freeSlots.remove(slot);
                }
            }

            if (remaining > 0) {
                unscheduledMinutes += remaining;
                unscheduledTaskCount++;
            }
        }

        float score = (scheduledMinutes * 1.0f)
                - (unscheduledMinutes * 1.35f)
                + (scheduleQuality * 8f)
                - (unscheduledTaskCount * 5f);

        return new PlanOption(
                policy.optionId,
                policy.label,
                policy.description,
                score,
                scheduledMinutes,
                unscheduledMinutes,
                unscheduledTaskCount,
                blocks
        );
    }

    private List<SchedulableTask> rankTasks(List<SchedulableTask> tasks,
                                            UserProfileEntity profile,
                                            OptionPolicy policy,
                                            long windowStartMillis) {
        List<SchedulableTask> ranked = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        ranked.sort((left, right) -> {
            float leftScore = computeTaskPriorityScore(left, profile, policy, windowStartMillis);
            float rightScore = computeTaskPriorityScore(right, profile, policy, windowStartMillis);
            int byScore = Float.compare(rightScore, leftScore);
            if (byScore != 0) {
                return byScore;
            }
            long leftDeadline = normalizeDeadline(left == null ? 0L : left.getDeadlineMillis());
            long rightDeadline = normalizeDeadline(right == null ? 0L : right.getDeadlineMillis());
            return Long.compare(leftDeadline, rightDeadline);
        });
        return ranked;
    }

    private float computeTaskPriorityScore(SchedulableTask task,
                                           UserProfileEntity profile,
                                           OptionPolicy policy,
                                           long windowStartMillis) {
        if (task == null) {
            return 0f;
        }

        float importance = (task.getPriority() / 3f) * policy.importanceWeight;
        float urgency = computeUrgency(task.getDeadlineMillis(), windowStartMillis) * policy.urgencyWeight;
        float affinity = computeProfileAffinity(task, profile) * policy.profileWeight;
        float energy = estimateEnergyPotential(task) * policy.energyWeight;
        return importance + urgency + affinity + energy;
    }

    private float computeUrgency(long deadlineMillis, long windowStartMillis) {
        if (deadlineMillis <= 0L) {
            return 0.35f;
        }

        long delta = deadlineMillis - windowStartMillis;
        if (delta <= 0L) {
            return 1.0f;
        }

        float days = delta / (float) DAY_MILLIS;
        if (days <= 1f) {
            return 0.92f;
        }
        if (days <= 2f) {
            return 0.78f;
        }
        if (days <= 4f) {
            return 0.60f;
        }
        if (days <= 7f) {
            return 0.45f;
        }
        return 0.25f;
    }

    private float computeProfileAffinity(SchedulableTask task, UserProfileEntity profile) {
        if (task == null || profile == null) {
            return 0.5f;
        }

        int preferredSession = clamp(profile.preferredSessionMinutes, 20, 120);
        int sessionGap = Math.abs(task.getPreferredBlockMin() - preferredSession);
        float sessionAffinity = 1f - Math.min(1f, sessionGap / 80f);

        float focusAffinity = 0.5f;
        if (SchedulableTask.ENERGY_HIGH_FOCUS.equals(task.getEnergyType())) {
            focusAffinity = profile.focusStartHour <= 11 ? 1.0f : 0.65f;
        } else if (SchedulableTask.ENERGY_LOW.equals(task.getEnergyType())) {
            focusAffinity = profile.focusEndHour >= 15 ? 0.9f : 0.65f;
        }

        return (sessionAffinity * 0.6f) + (focusAffinity * 0.4f);
    }

    private float estimateEnergyPotential(SchedulableTask task) {
        if (task == null) {
            return 0.5f;
        }

        if (SchedulableTask.ENERGY_HIGH_FOCUS.equals(task.getEnergyType())) {
            return 0.95f;
        }
        if (SchedulableTask.ENERGY_LOW.equals(task.getEnergyType())) {
            return 0.7f;
        }
        return 0.82f;
    }

    private MutableSlot chooseBestSlot(List<MutableSlot> freeSlots, SchedulableTask task) {
        MutableSlot best = null;
        float bestScore = Float.NEGATIVE_INFINITY;

        for (MutableSlot slot : freeSlots) {
            if (slot == null || slot.getRemainingMinutes() <= 0) {
                continue;
            }

            if (!task.isSplitAllowed() && slot.getRemainingMinutes() < task.getEstimatedDurationMin()) {
                continue;
            }

            float fit = computeSlotFit(task, slot);
            long normalizedDeadline = normalizeDeadline(task.getDeadlineMillis());
            if (normalizedDeadline != Long.MAX_VALUE && slot.startMillis > normalizedDeadline) {
                fit -= 0.6f;
            } else if (normalizedDeadline != Long.MAX_VALUE) {
                fit += 0.2f;
            }

            if (fit > bestScore) {
                bestScore = fit;
                best = slot;
            }
        }

        return best;
    }

    private float computeSlotFit(SchedulableTask task, MutableSlot slot) {
        if (task == null || slot == null) {
            return 0f;
        }

        int hour = slot.getStartHour();
        String slotEnergy;
        if (hour >= 8 && hour <= 11) {
            slotEnergy = SchedulableTask.ENERGY_HIGH_FOCUS;
        } else if (hour >= 12 && hour <= 17) {
            slotEnergy = SchedulableTask.ENERGY_MEDIUM;
        } else {
            slotEnergy = SchedulableTask.ENERGY_LOW;
        }

        if (slotEnergy.equals(task.getEnergyType())) {
            return 1.0f;
        }

        if (SchedulableTask.ENERGY_MEDIUM.equals(task.getEnergyType())) {
            return 0.82f;
        }

        return 0.55f;
    }

    private int resolveTargetBlockMinutes(SchedulableTask task,
                                          int remaining,
                                          int available,
                                          OptionPolicy policy) {
        if (task == null) {
            return 0;
        }

        if (!task.isSplitAllowed()) {
            if (available < remaining) {
                return 0;
            }
            return remaining;
        }

        int target = Math.min(remaining, Math.max(task.getMinBlockMin(), Math.min(task.getPreferredBlockMin(), policy.defaultBlockMinutes)));
        target = Math.min(target, available);

        if (target < task.getMinBlockMin()) {
            if (available >= task.getMinBlockMin()) {
                target = task.getMinBlockMin();
            } else if (available >= remaining) {
                target = remaining;
            } else {
                return 0;
            }
        }

        return Math.max(0, target);
    }

    private List<MutableSlot> toMutableFreeSlots(List<TimeSlot> timeSlots) {
        List<MutableSlot> free = new ArrayList<>();
        if (timeSlots == null) {
            return free;
        }

        for (TimeSlot slot : timeSlots) {
            if (slot == null || !slot.isFree()) {
                continue;
            }
            if (slot.getEndMillis() <= slot.getStartMillis()) {
                continue;
            }
            free.add(new MutableSlot(slot.getStartMillis(), slot.getEndMillis(), slot.getSource(), slot.getPlaceHint()));
        }

        free.sort(Comparator.comparingLong(a -> a.startMillis));
        return free;
    }

    private int totalFreeMinutes(List<MutableSlot> slots) {
        int sum = 0;
        for (MutableSlot slot : slots) {
            if (slot == null) {
                continue;
            }
            sum += slot.getRemainingMinutes();
        }
        return Math.max(0, sum);
    }

    private long normalizeDeadline(long deadlineMillis) {
        return deadlineMillis > 0L ? deadlineMillis : Long.MAX_VALUE;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class OptionPolicy {
        final String optionId;
        final String label;
        final String description;
        final float fillRatio;
        final float importanceWeight;
        final float urgencyWeight;
        final float profileWeight;
        final float energyWeight;
        final int defaultBlockMinutes;
        final int breakMinutes;

        OptionPolicy(String optionId,
                     String label,
                     String description,
                     float fillRatio,
                     float importanceWeight,
                     float urgencyWeight,
                     float profileWeight,
                     float energyWeight,
                     int defaultBlockMinutes,
                     int breakMinutes) {
            this.optionId = optionId;
            this.label = label;
            this.description = description;
            this.fillRatio = fillRatio;
            this.importanceWeight = importanceWeight;
            this.urgencyWeight = urgencyWeight;
            this.profileWeight = profileWeight;
            this.energyWeight = energyWeight;
            this.defaultBlockMinutes = defaultBlockMinutes;
            this.breakMinutes = breakMinutes;
        }
    }

    private static class MutableSlot {
        long startMillis;
        final long endMillis;
        final String source;
        final String placeHint;

        MutableSlot(long startMillis, long endMillis, String source, String placeHint) {
            this.startMillis = Math.min(startMillis, endMillis);
            this.endMillis = Math.max(startMillis, endMillis);
            this.source = source == null ? "free" : source;
            this.placeHint = placeHint == null ? "" : placeHint;
        }

        int getRemainingMinutes() {
            long remaining = Math.max(0L, endMillis - startMillis);
            return (int) (remaining / MINUTE_MILLIS);
        }

        int getStartHour() {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTimeInMillis(startMillis);
            return calendar.get(java.util.Calendar.HOUR_OF_DAY);
        }
    }
}
