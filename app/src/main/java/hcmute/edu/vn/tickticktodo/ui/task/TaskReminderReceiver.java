package hcmute.edu.vn.tickticktodo.ui.task;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import hcmute.edu.vn.tickticktodo.R; // Added import
import hcmute.edu.vn.tickticktodo.helper.NotificationHelper;

/**
 * BroadcastReceiver nhận Intent từ AlarmManager và hiển thị Notification nhắc nhở Task.
 * Uses NotificationHelper to display notifications.
 */
public class TaskReminderReceiver extends BroadcastReceiver {

    // ── Intent Extras (dùng chung với ReminderScheduler) ────────────────────────
    public static final String EXTRA_TASK_ID    = "reminder_task_id";
    public static final String EXTRA_TASK_TITLE = "reminder_task_title";
    public static final String EXTRA_TIME_LEFT  = "reminder_time_left";

    @Override
    public void onReceive(Context context, Intent intent) {
        long   taskId    = intent.getLongExtra(EXTRA_TASK_ID, -1L);
        String taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE);
        String timeLeft  = intent.getStringExtra(EXTRA_TIME_LEFT);

        if (taskId == -1L || taskTitle == null) return; // dữ liệu không hợp lệ

        // Delegate to Helper
        // Create channels (safe to call multiple times)
        NotificationHelper.createNotificationChannels(context);

        String title = context.getString(R.string.reminder_notification_title);
        if (timeLeft != null && !timeLeft.isEmpty()) {
            title = "Sắp đến hạn (" + timeLeft + ")";
        }

        NotificationHelper.showTaskNotification(
            context,
            title, 
            taskTitle,
            (int) taskId
        );

        // Kích hoạt Tầng tương tác (AI Assistant Overlay)
        Intent popupIntent = new Intent(context, hcmute.edu.vn.tickticktodo.ui.AiReminderPopupActivity.class);
        popupIntent.putExtra(EXTRA_TASK_ID, taskId);
        popupIntent.putExtra(EXTRA_TASK_TITLE, taskTitle);
        // Bắt buộc cờ này tĩnh khi gọi Activity từ BroadcastReceiver
        popupIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        try {
            // Kiểm tra quyền SYSTEM_ALERT_WINDOW từ Android 10 (cần thiết để start Activity từ Background)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(context)) {
                // Trên API 29+, nếu không có quyền canDrawOverlays, hệ thống sẽ chặn start Activity từ background
                // Tuy nhiên ta vẫn có thể dùng Full-Screen Intent của Notification để thay thế (NotificationHelper)
            } else {
                context.startActivity(popupIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
