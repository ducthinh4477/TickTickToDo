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

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.tickticktodo.data.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.NotificationHelper;
import hcmute.edu.vn.tickticktodo.model.Task;

/**
 * DailyDigestWorker — Chạy mỗi ngày, gửi thông báo tổng hợp công việc buổi sáng.
 *
 * Lịch chạy: mỗi 24 giờ, với initial delay tính đến 8:00 AM tiếp theo.
 * Hành động:
 *   1. Đếm số task chưa hoàn thành có due_date hôm nay.
 *   2. Hiển thị thông báo tổng hợp qua CHANNEL_ID_DAILY_DIGEST.
 */
public class DailyDigestWorker extends Worker {

    private static final String TAG          = "DailyDigestWorker";
    private static final String WORK_NAME    = "DailyDigestWork";
    private static final int    DIGEST_HOUR  = 8; // 8:00 AM

    public DailyDigestWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Running daily digest...");
        try {
            TaskDao dao = TaskDatabase.getInstance(getApplicationContext()).taskDao();

            Calendar cal = Calendar.getInstance();
            // Đầu ngày hôm nay (00:00:00.000)
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();

            // Cuối ngày hôm nay (23:59:59.999)
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
            long endOfDay = cal.getTimeInMillis();

            List<Task> todayTasks = dao.getIncompleteTasksForDaySync(startOfDay, endOfDay);
            int taskCount = todayTasks != null ? todayTasks.size() : 0;

            Log.d(TAG, "Today's incomplete tasks: " + taskCount);
            NotificationHelper.showDailyDigestNotification(getApplicationContext(), taskCount);

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error in DailyDigestWorker", e);
            return Result.failure();
        }
    }

    /**
     * Đặt lịch chạy mỗi ngày vào 8:00 AM.
     * Tính initial delay = khoảng thời gian từ bây giờ đến 8:00 AM tiếp theo.
     */
    public static void schedule(Context context) {
        long initialDelay = computeInitialDelayToHour(DIGEST_HOUR);

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(DailyDigestWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag("daily_digest")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
        Log.d(TAG, "Scheduled daily digest in " + (initialDelay / 60000) + " minutes");
    }

    /**
     * Tính thời gian delay (ms) đến giờ targetHour tiếp theo trong ngày.
     * Nếu targetHour đã qua, delay đến targetHour ngày mai.
     */
    private static long computeInitialDelayToHour(int targetHour) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, targetHour);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.before(now)) {
            // Đã qua giờ target hôm nay → chuyển sang ngày mai
            target.add(Calendar.DAY_OF_MONTH, 1);
        }

        return target.getTimeInMillis() - now.getTimeInMillis();
    }
}
