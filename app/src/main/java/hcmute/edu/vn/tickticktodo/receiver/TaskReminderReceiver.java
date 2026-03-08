package hcmute.edu.vn.tickticktodo.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import hcmute.edu.vn.tickticktodo.MainActivity;
import hcmute.edu.vn.tickticktodo.R;

/**
 * BroadcastReceiver nhận Intent từ AlarmManager và hiển thị Notification nhắc nhở Task.
 *
 * Luồng hoạt động:
 *   AlarmManager (khi đến giờ)
 *       └─► TaskReminderReceiver.onReceive()
 *               └─► NotificationManager.notify() → Notification xuất hiện trên thanh trạng thái
 *
 * Extras cần có trong Intent:
 *   EXTRA_TASK_ID    (long)   — ID của task (dùng để định danh notification & deep-link)
 *   EXTRA_TASK_TITLE (String) — Tên task hiển thị trong notification
 */
public class TaskReminderReceiver extends BroadcastReceiver {

    // ── Intent Extras (dùng chung với ReminderScheduler) ────────────────────────
    public static final String EXTRA_TASK_ID    = "reminder_task_id";
    public static final String EXTRA_TASK_TITLE = "reminder_task_title";

    // ── Notification Channel ─────────────────────────────────────────────────────
    public static final String CHANNEL_ID   = "task_reminder_channel";
    public static final String CHANNEL_NAME = "Task Reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        long   taskId    = intent.getLongExtra(EXTRA_TASK_ID, -1L);
        String taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE);

        if (taskId == -1L || taskTitle == null) return; // dữ liệu không hợp lệ

        // Đảm bảo notification channel tồn tại (bắt buộc từ Android 8.0+)
        createNotificationChannel(context);

        // PendingIntent: khi user bấm vào notification → mở MainActivity
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) taskId,           // requestCode riêng biệt theo taskId
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Xây dựng Notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(context.getString(R.string.reminder_notification_title))
                .setContentText(taskTitle)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(taskTitle))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)           // tự dismiss khi user bấm vào
                .setContentIntent(pendingIntent);

        // Hiển thị notification (ID = taskId để mỗi task có notification riêng)
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) taskId, builder.build());
        }
    }

    /**
     * Tạo Notification Channel bắt buộc cho Android 8.0+ (API 26+).
     * Gọi nhiều lần không có hại — hệ thống tự bỏ qua nếu channel đã tồn tại.
     */
    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(context.getString(R.string.reminder_channel_description));
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}

