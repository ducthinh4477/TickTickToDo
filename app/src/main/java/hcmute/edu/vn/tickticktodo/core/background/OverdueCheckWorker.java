package hcmute.edu.vn.tickticktodo.core.background;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.tickticktodo.data.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.NotificationHelper;
import hcmute.edu.vn.tickticktodo.model.Task;

/**
 * OverdueCheckWorker — Chạy mỗi 12 giờ, kiểm tra và thông báo về task quá hạn.
 *
 * Hành động:
 *   1. Query tất cả task có due_date < now() và is_completed = false.
 *   2. Nếu có task quá hạn, hiển thị thông báo cảnh báo qua CHANNEL_ID_OVERDUE.
 *
 * Không thông báo lặp lại cùng 1 task liên tục — chỉ thông báo nếu count > 0.
 */
public class OverdueCheckWorker extends Worker {

    private static final String TAG       = "OverdueCheckWorker";
    private static final String WORK_NAME = "OverdueCheckWork";

    public OverdueCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Checking for overdue tasks...");
        try {
            TaskDao dao = TaskDatabase.getInstance(getApplicationContext()).taskDao();

            // Lấy thời điểm bắt đầu ngày hôm nay (0:00 AM) để không báo cùng ngày
            // Task hôm nay (cùng ngày) chưa tính là "quá hạn" theo UX, chỉ tính từ ngày hôm qua trở về trước
            long startOfToday = getStartOfToday();

            List<Task> overdueTasks = dao.getOverdueIncompleteTasksSync(startOfToday);
            int overdueCount = overdueTasks != null ? overdueTasks.size() : 0;

            Log.d(TAG, "Overdue tasks found: " + overdueCount);

            if (overdueCount > 0) {
                String firstTitle = overdueTasks.get(0).getTitle();
                NotificationHelper.showOverdueNotification(
                        getApplicationContext(),
                        overdueCount,
                        firstTitle
                );
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error in OverdueCheckWorker", e);
            return Result.failure();
        }
    }

    /**
     * Lên lịch chạy định kỳ mỗi 12 giờ.
     * Không yêu cầu network vì chỉ đọc local DB.
     */
    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(OverdueCheckWorker.class, 12, TimeUnit.HOURS)
                .addTag("overdue_check")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
        Log.d(TAG, "OverdueCheckWorker scheduled");
    }

    private long getStartOfToday() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
