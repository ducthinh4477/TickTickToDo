package hcmute.edu.vn.tickticktodo.ui.chat;

final class PlanPreviewMessageFormatter {

    String buildProposalSummary(String proposalType, String anchorDate, int optionCount) {
        return "[KE HOACH " + proposalType + "] " + anchorDate
                + "\nMình đã tạo " + optionCount + " phương án cho bạn chọn.";
    }

    String buildOptionCard(String label,
                           String optionId,
                           String description,
                           int scheduledMinutes,
                           int unscheduledMinutes) {
        StringBuilder card = new StringBuilder();
        card.append("[OPTION] ").append(label);
        if (optionId != null && !optionId.isEmpty()) {
            card.append(" (ID: ").append(optionId).append(")");
        }
        if (description != null && !description.isEmpty()) {
            card.append("\n").append(description);
        }
        card.append("\nXep lich: ").append(scheduledMinutes).append(" phut");
        if (unscheduledMinutes > 0) {
            card.append(" | Con ton: ").append(unscheduledMinutes).append(" phut");
        }
        return card.toString();
    }
}