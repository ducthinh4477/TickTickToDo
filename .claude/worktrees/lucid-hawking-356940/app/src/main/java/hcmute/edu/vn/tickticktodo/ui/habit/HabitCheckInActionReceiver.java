package hcmute.edu.vn.doinbot.ui.habit;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import java.util.Calendar;
import java.util.concurrent.Executors;

import hcmute.edu.vn.doinbot.data.database.TaskDatabase;
import hcmute.edu.vn.doinbot.helper.HabitAlarmManager;
import hcmute.edu.vn.doinbot.model.HabitLog;

/**
 * Handles the quick "Tích hôm nay" action directly from the habit reminder notification.
 * Marks the habit completed for today and dismisses the notification.
 */
public class HabitCheckInActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long habitId = intent.getLongExtra(HabitAlarmManager.EXTRA_HABIT_ID, -1L);
        String habitName = intent.getStringExtra(HabitAlarmManager.EXTRA_HABIT_NAME);
        if (habitId < 0) return;

        // Dismiss notification
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel((int) habitId);

        // Write check-in to DB
        long todayStart = startOfDayMillis();
        Executors.newSingleThreadExecutor().execute(() -> {
            TaskDatabase.getInstance(context)
                    .habitDao()
                    .upsertHabitLog(new HabitLog(habitId, todayStart, true));
        });

        String name = habitName != null ? habitName : "thói quen";
        Toast.makeText(context, "✅ Đã tích: " + name, Toast.LENGTH_SHORT).show();
    }

    private long startOfDayMillis() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
