package hcmute.edu.vn.tickticktodo.agent.scheduler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ConflictItem;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ConflictReport;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.PlanBlock;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.PlanOption;
import hcmute.edu.vn.tickticktodo.agent.scheduler.model.ScheduleProposal;

public final class SchedulerJsonMapper {

    private SchedulerJsonMapper() {
    }

    public static JSONObject toProposalJson(ScheduleProposal proposal, boolean includeBlocks) {
        JSONObject json = new JSONObject();
        if (proposal == null) {
            return json;
        }

        safePut(json, "proposalId", proposal.getProposalId());
        safePut(json, "proposalType", proposal.getProposalType());
        safePut(json, "anchorDate", proposal.getAnchorDate() == null ? "" : proposal.getAnchorDate().toString());
        safePut(json, "windowStartMillis", proposal.getWindowStartMillis());
        safePut(json, "windowEndMillis", proposal.getWindowEndMillis());
        safePut(json, "generatedAtMillis", proposal.getGeneratedAtMillis());
        safePut(json, "conflictReport", toConflictReportJson(proposal.getConflictReport()));
        safePut(json, "options", toOptionsJson(proposal.getOptions(), includeBlocks));
        return json;
    }

    public static JSONArray toOptionsJson(List<PlanOption> options, boolean includeBlocks) {
        JSONArray array = new JSONArray();
        if (options == null) {
            return array;
        }

        for (PlanOption option : options) {
            if (option == null) {
                continue;
            }

            JSONObject item = new JSONObject();
            safePut(item, "optionId", option.getOptionId());
            safePut(item, "label", option.getLabel());
            safePut(item, "description", option.getDescription());
            safePut(item, "score", option.getScore());
            safePut(item, "scheduledMinutes", option.getScheduledMinutes());
            safePut(item, "unscheduledMinutes", option.getUnscheduledMinutes());
            safePut(item, "unscheduledTaskCount", option.getUnscheduledTaskCount());
            if (includeBlocks) {
                safePut(item, "blocks", toBlocksJson(option.getBlocks()));
            }
            array.put(item);
        }

        return array;
    }

    public static JSONArray toBlocksJson(List<PlanBlock> blocks) {
        JSONArray array = new JSONArray();
        if (blocks == null) {
            return array;
        }

        for (PlanBlock block : blocks) {
            if (block == null) {
                continue;
            }

            JSONObject item = new JSONObject();
            safePut(item, "optionId", block.getOptionId());
            safePut(item, "taskId", block.getTaskId());
            safePut(item, "taskTitle", block.getTaskTitle());
            safePut(item, "startMillis", block.getStartMillis());
            safePut(item, "endMillis", block.getEndMillis());
            safePut(item, "durationMinutes", block.getDurationMinutes());
            safePut(item, "blockType", block.getBlockType());
            safePut(item, "note", block.getNote());
            array.put(item);
        }

        return array;
    }

    public static JSONObject toConflictReportJson(ConflictReport report) {
        JSONObject json = new JSONObject();
        if (report == null) {
            return json;
        }

        safePut(json, "totalRequiredMinutes", report.getTotalRequiredMinutes());
        safePut(json, "totalFreeMinutes", report.getTotalFreeMinutes());
        safePut(json, "overloadMinutes", report.getOverloadMinutes());
        safePut(json, "infeasibleDeadlineCount", report.getInfeasibleDeadlineCount());
        safePut(json, "hasOverlaps", report.hasOverlaps());
        safePut(json, "hasOverload", report.hasOverload());
        safePut(json, "hasDeadlineInfeasible", report.hasDeadlineInfeasible());
        safePut(json, "isFeasible", report.isFeasible());

        JSONArray conflictItems = new JSONArray();
        for (ConflictItem item : report.getItems()) {
            if (item == null) {
                continue;
            }

            JSONObject conflictJson = new JSONObject();
            safePut(conflictJson, "type", item.getType());
            safePut(conflictJson, "severity", item.getSeverity());
            safePut(conflictJson, "message", item.getMessage());
            safePut(conflictJson, "taskId", item.getTaskId());
            safePut(conflictJson, "startMillis", item.getStartMillis());
            safePut(conflictJson, "endMillis", item.getEndMillis());
            safePut(conflictJson, "requiredMinutes", item.getRequiredMinutes());
            safePut(conflictJson, "availableMinutes", item.getAvailableMinutes());
            conflictItems.put(conflictJson);
        }
        safePut(json, "items", conflictItems);

        return json;
    }

    private static void safePut(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
