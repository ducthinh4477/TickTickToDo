package hcmute.edu.vn.tickticktodo.ui.main;

import android.content.Intent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.ui.MoodleActivity;
import hcmute.edu.vn.tickticktodo.ui.chat.AiAssistantActivity;
import hcmute.edu.vn.tickticktodo.ui.countdown.CountdownActivity;
import hcmute.edu.vn.tickticktodo.ui.calendar.CalendarActivity;
import hcmute.edu.vn.tickticktodo.ui.habit.HabitTrackerActivity;

public class MainNavigationHelper {

    public interface NavSelectionCallback {
        void onNavSelected(int selectedId);

        void onOpenSettings();
    }

    private final MainActivity activity;

    public MainNavigationHelper(MainActivity activity) {
        this.activity = activity;
    }

    public void setupNavRail(LinearLayout navItemHome,
                             LinearLayout navItemCalendar,
                             LinearLayout navItemFocus,
                             LinearLayout navItemSchool,
                             LinearLayout navItemHabits,
                             LinearLayout navItemSettings,
                             LinearLayout navItemAiAssistant,
                             NavSelectionCallback callback) {
        if (callback == null) {
            return;
        }

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

        if (navItemSchool != null) {
            navItemSchool.setOnClickListener(v -> {
                callback.onNavSelected(R.id.nav_item_school);
                activity.startActivity(new Intent(activity, MoodleActivity.class));
            });
        }

        if (navItemHabits != null) {
            navItemHabits.setOnClickListener(v -> {
                callback.onNavSelected(R.id.nav_item_habits);
                activity.startActivity(HabitTrackerActivity.newIntent(activity));
            });
        }

        if (navItemSettings != null) {
            navItemSettings.setOnClickListener(v -> {
                callback.onNavSelected(R.id.nav_item_settings);
                callback.onOpenSettings();
            });
        }

        if (navItemAiAssistant != null) {
            navItemAiAssistant.setOnClickListener(v -> {
                callback.onNavSelected(R.id.nav_item_ai_assistant);
                activity.startActivity(new Intent(activity, AiAssistantActivity.class));
            });
        }
    }

    public void selectNavItem(int selectedId,
                              int[] ids,
                              ImageView[] icons,
                              TextView[] labels) {
        int accent = activity.getResources().getColor(R.color.accent_primary, activity.getTheme());
        int secondary = activity.getResources().getColor(R.color.text_secondary, activity.getTheme());

        for (int i = 0; i < ids.length; i++) {
            if (icons[i] == null || labels[i] == null) {
                continue;
            }
            boolean selected = ids[i] == selectedId;
            int color = selected ? accent : secondary;
            icons[i].setImageTintList(android.content.res.ColorStateList.valueOf(color));
            labels[i].setTextColor(color);
        }
    }
}
