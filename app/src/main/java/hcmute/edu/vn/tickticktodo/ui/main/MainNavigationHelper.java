package hcmute.edu.vn.tickticktodo.ui.main;

import android.content.Intent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.ui.chat.AiAssistantActivity;
import hcmute.edu.vn.tickticktodo.ui.countdown.CountdownActivity;
import hcmute.edu.vn.tickticktodo.ui.calendar.CalendarActivity;

public class MainNavigationHelper {

    public interface NavSelectionCallback {
        void onNavSelected(int selectedId);
        void onOpenSettings();
    }

    private final MainActivity activity;

    public MainNavigationHelper(MainActivity activity) {
        this.activity = activity;
    }

    /**
     * Wire click listeners for the 5 bottom nav items.
     * School / Habits / Settings are now in the More popup — pass null for those.
     */
    public void setupNavRail(LinearLayout navItemHome,
                             LinearLayout navItemCalendar,
                             LinearLayout navItemFocus,
                             LinearLayout navItemSchool,   // unused — kept for API compat
                             LinearLayout navItemHabits,   // unused — kept for API compat
                             LinearLayout navItemSettings, // unused — kept for API compat
                             LinearLayout navItemAiAssistant,
                             NavSelectionCallback callback) {
        if (callback == null) return;

        if (navItemHome != null) {
            navItemHome.setOnClickListener(v -> callback.onNavSelected(R.id.nav_item_home));
        }

        if (navItemCalendar != null) {
            navItemCalendar.setOnClickListener(v -> {
                callback.onNavSelected(R.id.nav_item_calendar);
                activity.startActivity(CalendarActivity.newIntent(activity));
            });
        }

        if (navItemFocus != null) {
            navItemFocus.setOnClickListener(v -> {
                callback.onNavSelected(R.id.nav_item_focus);
                activity.startActivity(CountdownActivity.newIntent(activity));
            });
        }

        if (navItemAiAssistant != null) {
            navItemAiAssistant.setOnClickListener(v -> {
                callback.onNavSelected(R.id.nav_item_ai_assistant);
                activity.startActivity(new Intent(activity, AiAssistantActivity.class));
            });
        }

        // navItemSchool, navItemHabits, navItemSettings are handled via the More popup
    }

    /**
     * Update tint on all bottom nav icons + labels to reflect the selected item.
     * Null entries are silently skipped.
     */
    public void selectNavItem(int selectedId,
                              int[] ids,
                              ImageView[] icons,
                              TextView[] labels) {
        int accent    = activity.getResources().getColor(R.color.accent_primary, activity.getTheme());
        int secondary = activity.getResources().getColor(R.color.text_secondary, activity.getTheme());

        for (int i = 0; i < ids.length; i++) {
            if (icons[i] == null || labels[i] == null) continue;
            boolean selected = ids[i] == selectedId;
            int color = selected ? accent : secondary;
            icons[i].setImageTintList(android.content.res.ColorStateList.valueOf(color));
            labels[i].setTextColor(color);
            if (selected) {
                labels[i].setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                labels[i].setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }
}
