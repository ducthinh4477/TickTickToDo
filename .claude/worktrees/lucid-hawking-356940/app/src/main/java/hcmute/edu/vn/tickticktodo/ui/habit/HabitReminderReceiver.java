package hcmute.edu.vn.doinbot.ui.habit;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import hcmute.edu.vn.doinbot.R;
import hcmute.edu.vn.doinbot.helper.HabitAlarmManager;
import hcmute.edu.vn.doinbot.ui.habit.HabitTrackerActivity;

/**
 * Fired by AlarmManager when it's time to remind the user to complete a habit.
 * Shows a notification with a quick "Tích hôm nay" action.
 */
public class HabitReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "habit_reminder_channel";
    private static final String ACTION_CHECKIN =
            "hcmute.edu.vn.doinbot.ACTION_HABIT_CHECKIN";

    @Override
    public void onReceive(Context context, Intent intent) {
        long habitId = intent.getLongExtra(HabitAlarmManager.EXTRA_HABIT_ID, -1L);
        String habitName = intent.getStringExtra(HabitAlarmManager.EXTRA_HABIT_NAME);
        String habitIcon = intent.getStringExtra(HabitAlarmManager.EXTRA_HABIT_ICON);
        if (habitId < 0 || habitName == null) return;

        if (habitIcon == null || habitIcon.isEmpty()) habitIcon = "✅";

        createNotificationChannel(context);
        showNotification(context, habitId, habitName, habitIcon);
    }

    private void showNotification(Context context, long habitId,
                                  String habitName, String habitIcon) {
        // Tap → open HabitTrackerActivity
        Intent openIntent = new Intent(context, HabitTrackerActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
                context, (int) habitId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Quick check-in action
        Intent checkInIntent = new Intent(context, HabitCheckInActionReceiver.class);
        checkInIntent.setAction(ACTION_CHECKIN);
        checkInIntent.putExtra(HabitAlarmManager.EXTRA_HABIT_ID, habitId);
        checkInIntent.putExtra(HabitAlarmManager.EXTRA_HABIT_NAME, habitName);
        PendingIntent checkInPi = PendingIntent.getBroadcast(
                context, (int) (habitId + 10_000),
                checkInIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = habitIcon + " Đến giờ " + habitName + "!";
        String body = "Hôm nay bạn đã hoàn thành chưa? Tích ngay để giữ chuỗi streak!";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_health)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .addAction(R.drawable.ic_checkbox_square_checked, "Tích hôm nay ✓", checkInPi);

        try {
            NotificationManagerCompat.from(context)
                    .notify((int) habitId, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS permission not granted — silently skip
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nhắc nhở thói quen",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Thông báo nhắc bạn thực hiện thói quen hàng ngày");
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}
