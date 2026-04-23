package hcmute.edu.vn.doinbot.helper;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import hcmute.edu.vn.doinbot.ui.main.MainActivity;
import hcmute.edu.vn.doinbot.R;
import hcmute.edu.vn.doinbot.ui.task.NotificationActionReceiver;
import hcmute.edu.vn.doinbot.ui.countdown.TimerService;
import hcmute.edu.vn.doinbot.ui.countdown.CountdownActivity;

/**
 * Helper class to manage Notification Channels and build Notifications
 * for TimerService and Task Reminders.
 */
public class NotificationHelper {

    public static final String CHANNEL_ID_TIMER        = "timer_notification_channel";
    public static final String CHANNEL_ID_TASK         = "task_reminder_channel";
    public static final String CHANNEL_ID_DAILY_DIGEST = "daily_digest_channel";
    public static final String CHANNEL_ID_OVERDUE      = "overdue_channel";

    private static final String CHANNEL_NAME_TIMER        = "Pomodoro Timer";
    private static final String CHANNEL_NAME_TASK         = "Task Reminders";
    private static final String CHANNEL_NAME_DAILY_DIGEST = "Tổng hợp hàng ngày";
    private static final String CHANNEL_NAME_OVERDUE      = "Công việc quá hạn";

    /**
     * Creates notification channels for both Timer and Tasks.
     * Should be called on app startup or before showing notifications.
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;

            // 1. Timer Channel (Low importance to avoid sound on every tick, but visible)
            // Note: For Foreground Service, we usually need at least LOW.
            NotificationChannel timerChannel = new NotificationChannel(
                    CHANNEL_ID_TIMER,
                    CHANNEL_NAME_TIMER,
                    NotificationManager.IMPORTANCE_LOW
            );
            timerChannel.setDescription("Shows the running Pomodoro timer status");
            timerChannel.setShowBadge(false);
            manager.createNotificationChannel(timerChannel);

            // 2. Task Reminder Channel (High importance + Sound)
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            NotificationChannel taskChannel = new NotificationChannel(
                    CHANNEL_ID_TASK,
                    CHANNEL_NAME_TASK,
                    NotificationManager.IMPORTANCE_HIGH
            );
            taskChannel.setDescription(context.getString(R.string.reminder_channel_description));
            taskChannel.setSound(soundUri, audioAttributes);
            taskChannel.enableVibration(true);
            taskChannel.enableLights(true);

            manager.createNotificationChannel(taskChannel);

            // 3. Daily Digest Channel (Default importance — không cần âm thanh to, chỉ cần thấy)
            NotificationChannel dailyChannel = new NotificationChannel(
                    CHANNEL_ID_DAILY_DIGEST,
                    CHANNEL_NAME_DAILY_DIGEST,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            dailyChannel.setDescription("Tóm tắt công việc trong ngày vào mỗi buổi sáng");
            dailyChannel.setShowBadge(true);
            manager.createNotificationChannel(dailyChannel);

            // 4. Overdue Task Channel (High importance — cần user chú ý)
            NotificationChannel overdueChannel = new NotificationChannel(
                    CHANNEL_ID_OVERDUE,
                    CHANNEL_NAME_OVERDUE,
                    NotificationManager.IMPORTANCE_HIGH
            );
            overdueChannel.setDescription("Thông báo khi có công việc đã quá hạn chưa hoàn thành");
            overdueChannel.setSound(soundUri, audioAttributes);
            overdueChannel.enableVibration(true);
            manager.createNotificationChannel(overdueChannel);
        }
    }

    /**
     * Builds the Foreground Notification for the Timer Service.
     */
    public static Notification buildTimerNotification(Context context, long millisRemaining, boolean isRunning) {
        // Prepare content intent (tap to open CountdownActivity)
        Intent openIntent = new Intent(context, CountdownActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingOpenIntent = PendingIntent.getActivity(
                context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Format time
        int minutes = (int) (millisRemaining / 1000) / 60;
        int seconds = (int) (millisRemaining / 1000) % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);

        // Actions
        NotificationCompat.Action actionPause;
        if (isRunning) {
            Intent pauseIntent = new Intent(context, TimerService.class);
            pauseIntent.setAction(TimerService.ACTION_PAUSE);
            PendingIntent pendingPause = PendingIntent.getService(
                    context, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            actionPause = new NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    context.getString(R.string.countdown_btn_pause),
                    pendingPause
            );
        } else {
            Intent resumeIntent = new Intent(context, TimerService.class);
            resumeIntent.setAction(TimerService.ACTION_RESUME);
            PendingIntent pendingResume = PendingIntent.getService(
                    context, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            actionPause = new NotificationCompat.Action(
                    android.R.drawable.ic_media_play,
                    context.getString(R.string.countdown_btn_resume),
                    pendingResume
            );
        }

        Intent stopIntent = new Intent(context, TimerService.class);
        stopIntent.setAction(TimerService.ACTION_STOP);
        PendingIntent pendingStop = PendingIntent.getService(
                context, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationCompat.Action actionStop = new NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.countdown_btn_stop),
                pendingStop
        );

        return new NotificationCompat.Builder(context, CHANNEL_ID_TIMER)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(context.getString(R.string.countdown_title))
                .setContentText(isRunning ? "Focusing: " + timeString : "Paused: " + timeString)
                .setOngoing(true)
                .setOnlyAlertOnce(true) // Updates won't re-alert sound/vibration
                .setContentIntent(pendingOpenIntent)
                .addAction(actionPause)
                .addAction(actionStop)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    /**
     * Displays a notification for a specific Task.
     */
    public static void showTaskNotification(Context context, String title, String content, int taskId) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission check: if not granted, we can't show.
            // In a real app, we might handle this gracefully or request permissions earlier.
            return;
        }

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Note: Can pass task ID if MainActivity handles it
        // openAppIntent.putExtra("task_id", taskId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                taskId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Action: Done
        Intent doneIntent = new Intent(context, NotificationActionReceiver.class);
        doneIntent.setAction(NotificationActionReceiver.ACTION_DONE);
        doneIntent.putExtra(NotificationActionReceiver.EXTRA_TASK_ID, (long) taskId);
        PendingIntent donePendingIntent = PendingIntent.getBroadcast(
                context,
                taskId * 10 + 1, // Unique Request Code
                doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationCompat.Action actionDone = new NotificationCompat.Action(
                R.drawable.ic_completed,
                "Done", // Consider using context.getString(R.string.action_done)
                donePendingIntent
        );

        // Action: Snooze
        Intent snoozeIntent = new Intent(context, NotificationActionReceiver.class);
        snoozeIntent.setAction(NotificationActionReceiver.ACTION_SNOOZE);
        snoozeIntent.putExtra(NotificationActionReceiver.EXTRA_TASK_ID, (long) taskId);
        PendingIntent snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                taskId * 10 + 2, // Unique Request Code
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationCompat.Action actionSnooze = new NotificationCompat.Action(
                android.R.drawable.ic_popup_reminder,
                "Snooze (15m)", // Consider using context.getString(R.string.action_snooze)
                snoozePendingIntent
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_TASK)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent)
                .addAction(actionDone)
                .addAction(actionSnooze);

        NotificationManagerCompat.from(context).notify(taskId, builder.build());
    }

    /**
     * Hiển thị thông báo tổng hợp công việc buổi sáng (Daily Digest).
     * Chỉ hiển thị nếu có ít nhất 1 task chưa hoàn thành hôm nay.
     */
    public static void showDailyDigestNotification(Context context, int taskCount) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 2000, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title;
        String body;
        if (taskCount == 0) {
            title = "Hôm nay bạn không có công việc nào!";
            body = "Tuyệt vời! Hãy tận hưởng ngày hôm nay hoặc lên kế hoạch trước.";
        } else {
            title = "Bạn có " + taskCount + " công việc hôm nay";
            body = "Hãy bắt đầu ngày mới với danh sách công việc của bạn. Chúc bạn làm việc hiệu quả!";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_DAILY_DIGEST)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(context).notify(8001, builder.build());
    }

    /**
     * Hiển thị thông báo cảnh báo công việc quá hạn.
     */
    public static void showOverdueNotification(Context context, int overdueCount, String firstTaskTitle) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 2001, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = overdueCount == 1
                ? "1 công việc đã quá hạn"
                : overdueCount + " công việc đã quá hạn";

        String body = overdueCount == 1
                ? "\"" + firstTaskTitle + "\" đã qua hạn. Hãy hoàn thành hoặc cập nhật deadline."
                : "\"" + firstTaskTitle + "\" và " + (overdueCount - 1) + " công việc khác đã quá hạn. Kiểm tra ngay!";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_OVERDUE)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(context).notify(8002, builder.build());
    }
}
