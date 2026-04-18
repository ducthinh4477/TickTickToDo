package hcmute.edu.vn.tickticktodo.core.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.ReminderScheduler;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.ui.moodle.SchoolSyncReceiver;

/**
 * BootReceiver — lắng nghe BOOT_COMPLETED để restore toàn bộ alarm nhắc nhở.
 *
 * AlarmManager bị xóa sạch mỗi khi thiết bị tắt nguồn. BootReceiver đọc
 * lại danh sách task từ Room Database và đặt lại alarm cho những task còn
 * dueDate trong tương lai và chưa hoàn thành.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // Truy cập DB trên background thread (không được dùng main thread trong Receiver)
        final PendingResult pendingResult = goAsync();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Lấy tất cả task chưa hoàn thành có dueDate trong tương lai
                List<Task> tasks = TaskDatabase.getInstance(context)
                        .taskDao()
                        .getAllTasksSync();  // query đồng bộ (không trả LiveData)

                long now = System.currentTimeMillis();
                for (Task task : tasks) {
                    if (!task.isCompleted()
                            && task.getDueDate() != null
                            && task.getDueDate() > now) {
                        ReminderScheduler.scheduleReminder(context, task);
                    }
                }
                
                // Khôi phục lại quá trình theo dõi background (Moodle) khi khởi động lại máy
                SchoolSyncReceiver.scheduleExact(context);
                
            } finally {
                pendingResult.finish();
            }
        });
    }
}

