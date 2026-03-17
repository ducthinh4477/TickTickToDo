package hcmute.edu.vn.tickticktodo.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

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

    @Override
    public void onReceive(Context context, Intent intent) {
        long   taskId    = intent.getLongExtra(EXTRA_TASK_ID, -1L);
        String taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE);

        if (taskId == -1L || taskTitle == null) return; // dữ liệu không hợp lệ

        // Delegate to Helper
        // Create channels (safe to call multiple times)
        NotificationHelper.createNotificationChannels(context);

        NotificationHelper.showTaskNotification(
            context,
            context.getString(R.string.reminder_notification_title), // Use resource string
            taskTitle,
            (int) taskId
        );
    }
}
