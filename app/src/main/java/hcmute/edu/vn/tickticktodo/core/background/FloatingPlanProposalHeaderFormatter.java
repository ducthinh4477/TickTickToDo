package hcmute.edu.vn.tickticktodo.core.background;

final class FloatingPlanProposalHeaderFormatter {

    private FloatingPlanProposalHeaderFormatter() {
    }

    static String formatProposalSummaryHeader(String proposalType, String anchorDate, int optionCount) {
        return "[KE HOACH " + proposalType + "] " + anchorDate + "\n"
                + "Mình đã tạo " + optionCount + " phương án, bạn có thể áp dụng ngay từng option.";
    }
}