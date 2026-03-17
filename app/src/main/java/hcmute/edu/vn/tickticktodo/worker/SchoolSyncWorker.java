package hcmute.edu.vn.tickticktodo.worker;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
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
            Log.e(TAG, "No iCal URL found in SharedPreferences!");
            return Result.failure();
        }

        Log.d(TAG, "Syncing from URL: " + iCalUrl);

        try {
            // 2. Download iCal file — có check HTTP response code
            InputStream in = downloadUrl(iCalUrl);
            if (in == null) {
                Log.e(TAG, "downloadUrl returned null, retrying...");
                return Result.retry();
            }

            // 3. Parse iCal using biweekly
            ICalendar ical = Biweekly.parse(in).first();
            if (ical == null) {
                Log.e(TAG, "Failed to parse iCal content — check if URL returns valid .ics data");
                return Result.failure();
            }

            List<VEvent> events = ical.getEvents();
            Log.d(TAG, "Parsed " + events.size() + " events from iCal");

            TaskDatabase db = TaskDatabase.getInstance(context);
            TaskDao dao = db.taskDao();
            int newTasksCount = 0;

            // 4. Update Database
            for (VEvent event : events) {
                // ── Bug Fix 1: Null-safe đọc các field của VEvent ──
                // Moodle có thể bỏ qua UID hoặc Summary với một số loại event
                String summary = event.getSummary() != null ? event.getSummary().getValue() : null;
                if (summary == null || summary.trim().isEmpty()) {
                    Log.w(TAG, "Skipping event with null/empty summary");
                    continue; // Bỏ qua event không có tên
                }

                // DateStart bắt buộc theo chuẩn iCal, nhưng vẫn guard
                if (event.getDateStart() == null || event.getDateStart().getValue() == null) {
                    Log.w(TAG, "Skipping event '" + summary + "': missing DTSTART");
                    continue;
                }
                Date start = event.getDateStart().getValue();

                // Moodle thường dùng DTEND hoặc DURATION, fallback về DTSTART nếu thiếu
                Date end;
                if (event.getDateEnd() != null && event.getDateEnd().getValue() != null) {
                    end = event.getDateEnd().getValue();
                } else {
                    end = start; // Fallback: deadline = thời điểm bắt đầu
                }

                // Moodle UteX LMS thường dùng description cho nội dung bài tập
                String description = (event.getDescription() != null && event.getDescription().getValue() != null)
                        ? event.getDescription().getValue()
                        : "";

                // Kiểm tra trùng lặp theo title + due_date
                Task existingTask = dao.findTaskByTitleAndDate(summary, end.getTime());
                if (existingTask == null) {
                    Task newTask = new Task(summary, description, end.getTime(), false, 2);
                    long newId = dao.insert(newTask);
                    scheduleDeadlineAlarm(context, newId, end.getTime(), summary);
                    newTasksCount++;
                    Log.d(TAG, "Inserted new task: " + summary);
                } else {
                    Log.d(TAG, "Task already exists, skipping: " + summary);
                }
            }

            // 5. Notify user
            Log.d(TAG, "Sync complete. New tasks inserted: " + newTasksCount);
            if (newTasksCount > 0) {
                NotificationHelper.showTaskNotification(context,
                        "Đồng bộ hoàn tất",
                        "Đã thêm " + newTasksCount + " bài tập mới từ trường.",
                        9999);
            } else {
                NotificationHelper.showTaskNotification(context,
                        "Đồng bộ hoàn tất",
                        "Lịch học đã được cập nhật. Không có bài tập mới.",
                        9999);
            }

            return Result.success();

        } catch (IOException e) {
            Log.e(TAG, "Network error during sync", e);
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during sync", e);
            return Result.failure();
        }
    }

    /**
     * Download iCal từ URL.
     * - Set User-Agent để tránh bị server Moodle chặn.
     * - Kiểm tra HTTP response code trước khi đọc body.
     * - Tự follow HTTPS redirect một lần nếu cần.
     *
     * @return InputStream nếu thành công, null nếu server trả lỗi / redirect không theo được.
     */
    private InputStream downloadUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(15000);
        conn.setConnectTimeout(15000);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        // ── Bug Fix 2: Thêm User-Agent giả lập trình duyệt để Moodle không chặn ──
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        conn.setInstanceFollowRedirects(true);
        conn.connect();

        // ── Bug Fix 3: Kiểm tra response code — getInputStream() ném exception cho 4xx/5xx ──
        int responseCode = conn.getResponseCode();
        Log.d(TAG, "HTTP response code: " + responseCode + " for URL: " + urlString);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            return conn.getInputStream();
        } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == 307 || responseCode == 308) {
            // Redirect thủ công (thường xảy ra khi HTTPS → HTTPS khác domain)
            String redirectUrl = conn.getHeaderField("Location");
            Log.d(TAG, "Following redirect to: " + redirectUrl);
            conn.disconnect();
            if (redirectUrl != null) {
                return downloadUrl(redirectUrl); // Đệ quy follow redirect 1 lần
            }
            return null;
        } else {
            Log.e(TAG, "Server returned HTTP " + responseCode + ". Sync failed.");
            conn.disconnect();
            return null;
        }
    }

    // Helper to schedule notification 24h and 2h before deadline
    private void scheduleDeadlineAlarm(Context context, long taskId, long deadlineMillis, String title) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, TaskReminderReceiver.class);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId);
        intent.putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, "Sắp đến hạn: " + title);

        long now = System.currentTimeMillis();

        // Alarm trước 24h
        long triggerTime24h = deadlineMillis - 24 * 60 * 60 * 1000;
        if (triggerTime24h > now) {
            PendingIntent pendingIntent24h = PendingIntent.getBroadcast(
                    context,
                    (int) taskId * 10 + 1,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            setAlarmSafe(alarmManager, triggerTime24h, pendingIntent24h);
        }

        // Alarm trước 2h
        long triggerTime2h = deadlineMillis - 2 * 60 * 60 * 1000;
        if (triggerTime2h > now) {
            PendingIntent pendingIntent2h = PendingIntent.getBroadcast(
                    context,
                    (int) taskId * 10 + 2,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            setAlarmSafe(alarmManager, triggerTime2h, pendingIntent2h);
        }
    }

    /**
     * Đặt alarm an toàn trên mọi phiên bản Android.
     * Android 12+ (API 31): kiểm tra canScheduleExactAlarms() trước khi gọi setExactAndAllowWhileIdle,
     * fallback về setWindow(±1 phút) nếu quyền chưa được cấp.
     */
    private void setAlarmSafe(AlarmManager alarmManager, long triggerTime, PendingIntent pendingIntent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                // Fallback: chính xác trong khoảng ±1 phút, không cần quyền đặc biệt
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerTime, 60_000L, pendingIntent);
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    // Static helper to start periodic sync
    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncRequest =
                new PeriodicWorkRequest.Builder(SchoolSyncWorker.class, 6, TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .addTag("school_sync")
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "SchoolSyncWork",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
        );
    }

    // Static helper for manual sync — REPLACE đảm bảo không bị duplicate khi nhấn đồng bộ nhiều lần
    public static void triggerManualSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest manualRequest = new OneTimeWorkRequest.Builder(SchoolSyncWorker.class)
                .setConstraints(constraints)
                .addTag("school_sync_manual")
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "SchoolSyncManual",
                ExistingWorkPolicy.REPLACE,
                manualRequest
        );
    }
}
