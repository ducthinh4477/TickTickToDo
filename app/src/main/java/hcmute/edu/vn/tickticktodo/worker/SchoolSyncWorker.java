package hcmute.edu.vn.tickticktodo.worker;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.tickticktodo.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.NotificationHelper;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.receiver.TaskReminderReceiver;
import hcmute.edu.vn.tickticktodo.ui.SchoolLoginActivity;
import android.app.PendingIntent;

public class SchoolSyncWorker extends Worker {

    private static final String TAG = "SchoolSyncWorker";
    private final Context context;

    public SchoolSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting sync...");

        // 1. Lấy URL iCal đã lưu
        SharedPreferences settings = context.getSharedPreferences(SchoolLoginActivity.PREFS_NAME, 0);
        String iCalUrl = settings.getString(SchoolLoginActivity.KEY_ICAL_URL, null);

        if (iCalUrl == null) {
            Log.e(TAG, "No iCal URL found!");
            return Result.failure();
        }

        try {
            // 2. Download iCal file
            InputStream in = downloadUrl(iCalUrl);
            if (in == null) return Result.retry(); // Mạng lỗi thì thử lại

            // 3. Parse iCal using biweekly
            ICalendar ical = Biweekly.parse(in).first();
            if (ical == null) return Result.failure();

            TaskDatabase db = TaskDatabase.getInstance(context);
            TaskDao dao = db.taskDao();
            int newTasksCount = 0;

            // 4. Update Database
            List<VEvent> events = ical.getEvents();
            for (VEvent event : events) {
                String uid = event.getUid().getValue();
                String summary = event.getSummary().getValue();
                Date start = event.getDateStart().getValue();
                Date end = event.getDateEnd() != null ? event.getDateEnd().getValue() : start;

                // Moodle UteX LMS thường dùng description cho nội dung bài tập
                String description = event.getDescription() != null ? event.getDescription().getValue() : "";

                // Kiểm tra xem Task này đã tồn tại chưa (cần sửa Task entity thêm field 'external_uid' hoặc dùng title)
                // Giả sử dùng title + date làm key unique đơn giản nếu chưa sửa DB
                // ... Ở bước thực tế bạn nên thêm cột 'moodle_uid' vào bảng Task

                Task existingTask = dao.findTaskByTitleAndDate(summary, end.getTime());;
                // existingTask = dao.findTaskByMoodleUid(uid); // Cần query này trong DAO

                if (existingTask == null) {
                    Task newTask = new Task(summary, description, end.getTime(), false, 2); // Priority Medium
                    // newTask.setMoodleId(uid);

                    long newId = dao.insert(newTask);
                    scheduleDeadlineAlarm(context, newId, end.getTime(), summary);
                    newTasksCount++;
                } else {
                    // Update nếu cần (ví dụ thay đổi deadline)
                }
            }

            // 5. Notify user if new tasks found
            if (newTasksCount > 0) {
                NotificationHelper.showTaskNotification(context,
                        "Đồng bộ hoàn tất",
                        "Đã thêm " + newTasksCount + " bài tập mới từ trường.",
                        9999);
            }

            return Result.success();

        } catch (IOException e) {
            Log.e(TAG, "Network error", e);
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Parsing error", e);
            return Result.failure();
        }
    }

    private InputStream downloadUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000);
        conn.setConnectTimeout(15000);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        conn.connect();
        return conn.getInputStream();
    }

    // Helper to schedule notification 24h before deadline
    private void scheduleDeadlineAlarm(Context context, long taskId, long deadlineMillis, String title) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, "Sắp đến hạn: " + title);

        // Alarm trước 24h
        long triggerTime24h = deadlineMillis - 24 * 60 * 60 * 1000;
        if (triggerTime24h > System.currentTimeMillis()) {
            PendingIntent pendingIntent24h = PendingIntent.getBroadcast(
                    context,
                    (int) taskId * 10 + 1, // Unique ID
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
             alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime24h, pendingIntent24h);
        }

        // Alarm trước 2h
        long triggerTime2h = deadlineMillis - 2 * 60 * 60 * 1000;
        if (triggerTime2h > System.currentTimeMillis()) {
            PendingIntent pendingIntent2h = PendingIntent.getBroadcast(
                    context,
                    (int) taskId * 10 + 2, // Unique ID
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
             alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime2h, pendingIntent2h);
        }
    }

    // Static helper to start periodic sync
    public static void schedulePeriodicSync(Context context) {
        PeriodicWorkRequest syncRequest =
                new PeriodicWorkRequest.Builder(SchoolSyncWorker.class, 6, TimeUnit.HOURS)
                        .addTag("school_sync")
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "SchoolSyncWork",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
        );
    }

    // Static helper for manual sync
    public static void triggerManualSync(Context context) {
         OneTimeWorkRequest manualRequest = new OneTimeWorkRequest.Builder(SchoolSyncWorker.class).build();
         WorkManager.getInstance(context).enqueue(manualRequest);
    }
}
