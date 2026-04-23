package hcmute.edu.vn.tickticktodo.core.background;

import android.text.TextUtils;

final class FloatingPlanOptionCardFormatter {

    private FloatingPlanOptionCardFormatter() {
    }

    static String formatOptionCard(
            String optionId,
            String label,
            String description,
            int scheduled,
            int unscheduled
    ) {
        StringBuilder card = new StringBuilder();
        card.append("[OPTION] ").append(label);
        if (!TextUtils.isEmpty(optionId)) {
            card.append(" (ID: ").append(optionId).append(")");
        }
        if (!TextUtils.isEmpty(description)) {
            card.append("\n").append(description);
        }
        card.append("\nXep lich: ").append(scheduled).append(" phut");
        if (unscheduled > 0) {
            card.append(" | Con ton: ").append(unscheduled).append(" phut");
        }
        return card.toString();
    }
}