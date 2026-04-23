package hcmute.edu.vn.tickticktodo.helper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.Habit;
import hcmute.edu.vn.tickticktodo.ui.habit.HabitReminderReceiver;

/**
 * Manages daily AlarmManager alarms for habit reminders.
 * Each habit has its own PendingIntent keyed by habitId.
 */
public class HabitAlarmManager {

    public static final String EXTRA_HABIT_ID = "habit_id";
    public static final String EXTRA_HABIT_NAME = "habit_name";
    public static final String EXTRA_HABIT_ICON = "habit_icon";

    private HabitAlarmManager() {}

    /** Schedule (or reschedule) a daily repeating alarm for this habit. */
    public static void scheduleHabitReminder(Context context, Habit habit) {
        if (!habit.hasReminder()) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPendingIntent(context, habit);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, habit.getReminderHour());
        cal.set(Calendar.MINUTE, habit.getReminderMinute());
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // If time has already passed today, start from tomorrow
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        long intervalDay = AlarmManager.INTERVAL_DAY;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            // Fallback to inexact repeating
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), intervalDay, pi);
        } else {
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), intervalDay, pi);
        }
    }

    /** Cancel the alarm for this habit. */
    public static void cancelHabitReminder(Context context, Habit habit) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildPendingIntent(context, habit));
    }

    /** Reschedule all habits that have reminders (call on BOOT_COMPLETED). */
    public static void rescheduleAll(Context context) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Habit> habits = TaskDatabase.getInstance(context).habitDao().getAllHabitsSync();
            if (habits == null) return;
            for (Habit habit : habits) {
                if (habit.hasReminder()) {
                    scheduleHabitReminder(context, habit);
                }
            }
        });
    }

    private static PendingIntent buildPendingIntent(Context context, Habit habit) {
        Intent intent = new Intent(context, HabitReminderReceiver.class);
        intent.putExtra(EXTRA_HABIT_ID, habit.getId());
        intent.putExtra(EXTRA_HABIT_NAME, habit.getName() == null ? "" : habit.getName());
        intent.putExtra(EXTRA_HABIT_ICON, habit.getIcon() == null ? "✅" : habit.getIcon());

        int requestCode = (int) (habit.getId() & 0x7FFFFFFF); // safe int
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
