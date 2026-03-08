package hcmute.edu.vn.tickticktodo.helper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.receiver.TaskReminderReceiver;

/**
 * Helper class quản lý việc đặt và huỷ Alarm nhắc nhở cho Task.
 *
 * Cách dùng:
 *   // Đặt alarm khi thêm/cập nhật task có dueDate
 *   ReminderScheduler.scheduleReminder(context, task);
 *
 *   // Huỷ alarm khi task bị xóa hoặc đánh dấu hoàn thành
 *   ReminderScheduler.cancelReminder(context, task.getId());
 *
 * Lưu ý:
 *   - Chỉ đặt alarm nếu dueDate trong tương lai.
 *   - Từ Android 12+ cần quyền SCHEDULE_EXACT_ALARM hoặc dùng setWindow().
 *   - Alarm bị mất khi thiết bị khởi động lại → cần BootReceiver để restore
 *     (xem BOOT_COMPLETED trong AndroidManifest).
 */
public class ReminderScheduler {

    /**
     * Đặt alarm nhắc nhở cho task.
     * Alarm sẽ kích hoạt đúng vào thời điểm dueDate của task.
     *
     * @param context Context (Activity hoặc Application)
     * @param task    Task cần nhắc nhở (phải có dueDate và id hợp lệ)
     */
    public static void scheduleReminder(Context context, Task task) {
        if (task.getDueDate() == null) return;               // không có ngày → bỏ qua
        if (task.getDueDate() <= System.currentTimeMillis()) return; // đã qua → bỏ qua
        if (task.isCompleted()) return;                      // đã hoàn thành → bỏ qua

        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = buildPendingIntent(context, task);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: kiểm tra quyền SCHEDULE_EXACT_ALARM trước khi gọi setExact
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        task.getDueDate(),
                        pendingIntent
                );
            } else {
                // Fallback: setWindow (chính xác trong khoảng ±1 phút)
                alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        task.getDueDate(),
                        60_000L,
                        pendingIntent
                );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6–11: setExactAndAllowWhileIdle hoạt động cả khi máy ngủ (Doze)
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    task.getDueDate(),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    task.getDueDate(),
                    pendingIntent
            );
        }
    }

    /**
     * Huỷ alarm đã đặt cho task.
     * Gọi khi task bị xoá hoặc đánh dấu hoàn thành.
     *
     * @param context Context
     * @param taskId  ID của task cần huỷ alarm
     */
    public static void cancelReminder(Context context, long taskId) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Dùng FLAG_NO_CREATE: chỉ lấy PendingIntent nếu đã tồn tại, không tạo mới
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) taskId,
                buildIntent(context, taskId, ""),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private static PendingIntent buildPendingIntent(Context context, Task task) {
        return PendingIntent.getBroadcast(
                context,
                (int) task.getId(),
                buildIntent(context, task.getId(), task.getTitle()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static Intent buildIntent(Context context, long taskId, String taskTitle) {
        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, taskTitle);
        return intent;
    }
}

