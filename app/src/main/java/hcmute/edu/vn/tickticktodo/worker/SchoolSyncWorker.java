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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

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

        if (iCalUrl == null || iCalUrl.trim().isEmpty()) {
            Log.e(TAG, "No iCal URL found in SharedPreferences!");
            androidx.work.Data errorData = new androidx.work.Data.Builder()
                    .putString("ERROR_MSG", "Lỗi: Không tìm thấy đường dẫn lịch.")
                    .build();
            return Result.failure(errorData);
        }

        // Tự động chuyển đổi webcal:// thành https:// nếu có
        if (iCalUrl.startsWith("webcal://")) {
            iCalUrl = iCalUrl.replaceFirst("webcal://", "https://");
        } else if (iCalUrl.startsWith("http://")) {
            iCalUrl = iCalUrl.replaceFirst("http://", "https://");
        }

        Log.d(TAG, "Syncing from URL: " + iCalUrl);

        try {
            // 2. Download iCal file — tải toàn bộ về chuỗi String
            String icsContent = downloadUrlAsString(iCalUrl);
            if (icsContent == null || icsContent.trim().isEmpty()) {
                Log.e(TAG, "downloadUrlAsString returned null/empty.");
                androidx.work.Data errorData = new androidx.work.Data.Builder()
                        .putString("ERROR_MSG", "Lỗi: Không thể tải dữ liệu từ trường. (Server trả về rỗng)")
                        .build();
                return Result.failure(errorData);
            }

            // Ghi log 200 ký tự đầu tiên để debug
            Log.d(TAG, "Fetched ICS Content (first 200 chars): \n" +
                    icsContent.substring(0, Math.min(icsContent.length(), 200)));

            // 3. Parse iCal using biweekly from String instead of InputStream
            ICalendar ical = Biweekly.parse(icsContent).first();
            if (ical == null) {
                Log.e(TAG, "Failed to parse iCal content — check if URL returns valid .ics data");
                androidx.work.Data errorData = new androidx.work.Data.Builder()
                        .putString("ERROR_MSG", "Lỗi: File tải về không đúng định dạng lịch (.ics).")
                        .build();
                return Result.failure(errorData);
            }

            List<VEvent> events = ical.getEvents();
            Log.d(TAG, "Parsed " + events.size() + " events from iCal");

            TaskDatabase db = TaskDatabase.getInstance(context);
            TaskDao dao = db.taskDao();
            int newTasksCount = 0;

            // 4. Update Database
            for (VEvent event : events) {
                String summary = event.getSummary() != null ? event.getSummary().getValue() : null;
                if (summary == null || summary.trim().isEmpty()) {
                    Log.w(TAG, "Skipping event with null/empty summary");
                    continue;
                }

                Date start = null;
                Date end = null;

                // Lấy Start Date
                if (event.getDateStart() != null && event.getDateStart().getValue() != null) {
                    start = event.getDateStart().getValue();
                }

                // Lấy End Date
                if (event.getDateEnd() != null && event.getDateEnd().getValue() != null) {
                    end = event.getDateEnd().getValue();
                } else if (start != null && event.getDuration() != null && event.getDuration().getValue() != null) {
                    end = new Date(start.getTime() + event.getDuration().getValue().toMillis());
                }

                // Fallback Moodle: Bài tập có thể chỉ có Deadline (End) mà không có Start
                if (start == null && end != null) {
                    start = end;
                }
                
                // Mới: Nếu không có mốc thời gian, không được skip, lấy current time đắp vào
                if (start == null && end == null) {
                    Log.w(TAG, "Event '" + summary + "': missing both DTSTART and DTEND. Fallback to current time.");
                    start = new Date();
                    end = start;
                }

                // Moodle UteX LMS thường dùng description cho nội dung bài tập
                String description = (event.getDescription() != null && event.getDescription().getValue() != null)
                        ? event.getDescription().getValue()
                        : "";

                // Kiểm tra trùng lặp theo title + due_date
                Task existingTask = dao.findTaskByTitleAndDate(summary, end.getTime());
                if (existingTask == null) {
                    Task newTask = new Task(summary, description, end.getTime(), false, 2);
                    newTask.setSource("Moodle"); // Đánh dấu nguồn task
                    long newId = dao.insert(newTask);
                    scheduleDeadlineAlarm(context, newId, end.getTime(), summary);
                    newTasksCount++;
                    Log.d(TAG, "Inserted new task: " + summary);
                } else {
                    Log.d(TAG, "Task already exists, skipping: " + summary);
                }
            }

            // 5. Notify user
            boolean isManualSync = getTags().contains("school_sync_manual");
            Log.d(TAG, "Sync complete. New tasks inserted: " + newTasksCount);
            
            if (newTasksCount > 0) {
                NotificationHelper.showTaskNotification(context,
                        "Moodle Hutech",
                        "Có " + newTasksCount + " deadline/bài tập mới vừa được thêm!",
                        9999);
            } else if (isManualSync) {
                // Chỉ thông báo "không có bài mới" khi người dùng chủ động tải lại (vuốt Moodle)
                // Chạy ngầm định kỳ (Background) thì im lặng nếu không có bài mới để tránh làm phiền
                NotificationHelper.showTaskNotification(context,
                        "Moodle Hutech",
                        "Lịch học đã được cập nhật. Không có bài tập mới.",
                        9999);
            }

            return Result.success();

        } catch (IllegalArgumentException e) {
            boolean isManualSync = getTags().contains("school_sync_manual");
            if ("HTML_CONTENT_TYPE".equals(e.getMessage())) {
                androidx.work.Data errorData = new androidx.work.Data.Builder()
                        .putString("ERROR_MSG", "Lỗi: Link iCal của Moodle đã hết hạn (Server yêu cầu đăng nhập HTML). Vui lòng Copy link mới!")
                        .build();
                
                // Nếu chạy ngầm mà URL hết hạn, lập tức bắn popup notification cảnh báo user
                if (!isManualSync) {
                    NotificationHelper.showTaskNotification(context,
                        "Cảnh báo Moodle",
                        "Link đồng bộ Moodle đã hết hạn. Vui lòng mở ứng dụng và cập nhật lại đường dẫn mới!",
                        9998);
                }
                
                return Result.failure(errorData);
            }
            Log.e(TAG, "Dữ liệu không hợp lệ", e);
            androidx.work.Data errorData = new androidx.work.Data.Builder()
                    .putString("ERROR_MSG", "Lỗi dữ liệu: " + e.getMessage())
                    .build();
            return Result.failure(errorData);
        } catch (IOException e) {
            Log.e(TAG, "Network error during sync", e);
            androidx.work.Data errorData = new androidx.work.Data.Builder()
                    .putString("ERROR_MSG", "Lỗi mạng (" + e.getClass().getSimpleName() + "). Hãy kiểm tra lại kết nối.")
                    .build();
            return Result.failure(errorData); // Đổi từ retry() sang failure() để UI nhận phản hồi ngay
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during sync", e);
            androidx.work.Data errorData = new androidx.work.Data.Builder()
                    .putString("ERROR_MSG", "Lỗi xử lý hệ thống: " + e.getMessage())
                    .build();
            return Result.failure(errorData);
        }
    }

    /**
     * Download iCal từ URL và trả về toàn bộ nội dung dưới dạng String.
     * Tránh truyền trực tiếp InputStream vào Biweekly để tránh treo luồng.
     *
     * @return String nội dung của file .ics, null nếu server trả lỗi / redirect.
     */
    private String downloadUrlAsString(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // ── Bypass SSLHandshakeException cho Moodle (Chấp nhận mọi chứng chỉ HTTPS) ──
        if (conn instanceof HttpsURLConnection) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                            public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                        }
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                ((HttpsURLConnection) conn).setSSLSocketFactory(sc.getSocketFactory());
                ((HttpsURLConnection) conn).setHostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                Log.e(TAG, "Lỗi cài đặt Bypass SSL", e);
            }
        }

        conn.setReadTimeout(15000);
        conn.setConnectTimeout(15000);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        // Thêm User-Agent giả lập trình duyệt để Moodle không chặn
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setInstanceFollowRedirects(true);
        conn.connect();

        // Kiểm tra Content-Type
        // Nếu trả về html thì ném exception để ngừng ngay
        String contentType = conn.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("text/html")) {
            conn.disconnect();
            throw new IllegalArgumentException("HTML_CONTENT_TYPE");
        }

        int responseCode = conn.getResponseCode();
        Log.d(TAG, "HTTP response code: " + responseCode + " for URL: " + urlString);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            conn.disconnect();
            return sb.toString();
        } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == 307 || responseCode == 308) {
            // Redirect thủ công
            String redirectUrl = conn.getHeaderField("Location");
            Log.d(TAG, "Following redirect to: " + redirectUrl);
            conn.disconnect();
            if (redirectUrl != null) {
                return downloadUrlAsString(redirectUrl); // Đệ quy follow redirect 1 lần
            }
            return null;
        } else {
            Log.e(TAG, "Server returned HTTP " + responseCode + ". Sync failed.");
            conn.disconnect();
            return null;
        }
    }

    // Helper to schedule notification at multiple intervals before deadline
    private void scheduleDeadlineAlarm(Context context, long taskId, long deadlineMillis, String title) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        long now = System.currentTimeMillis();

        // Danh sách các khoảng thời gian: 24h, 6h, 1h, 15m, 10m
        long[] intervalsMillis = {
                24 * 60 * 60 * 1000L,
                6 * 60 * 60 * 1000L,
                60 * 60 * 1000L,
                15 * 60 * 1000L,
                10 * 60 * 1000L
        };

        String[] timeLabels = {
                "24 giờ",
                "6 giờ",
                "1 giờ",
                "15 phút",
                "10 phút"
        };

        for (int i = 0; i < intervalsMillis.length; i++) {
            long triggerTime = deadlineMillis - intervalsMillis[i];
            if (triggerTime > now) {
                Intent intent = new Intent(context, TaskReminderReceiver.class);
                intent.putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId);
                intent.putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, title);
                intent.putExtra(TaskReminderReceiver.EXTRA_TIME_LEFT, timeLabels[i]);

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context,
                        (int) taskId * 100 + i, // Unique request code per interval
                        intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                );
                setAlarmSafe(alarmManager, triggerTime, pendingIntent);
            }
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
        // Hủy lịch cũ sử dụng AlarmManager nếu có, vì ta sẽ chuyển sang WorkManager tối ưu hơn
        hcmute.edu.vn.tickticktodo.receiver.SchoolSyncReceiver.cancelExact(context);

        // Thiết lập điều kiện: Chỉ đồng bộ khi có Wi-Fi (UNMETERED) VÀ khi đang sạc (RequiresCharging)
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .build();

        // Thời gian lập lịch lặp lại liên tục: 15 phút/lần (Thấp nhất mà Android hỗ trợ cho WorkManager)
        PeriodicWorkRequest syncRequest =
                new PeriodicWorkRequest.Builder(SchoolSyncWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .addTag("school_sync_bg")
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "SchoolSyncWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
        );
    }

    // Static helper for manual sync — REPLACE đảm bảo không bị duplicate khi nhấn đồng bộ nhiều lần
    public static java.util.UUID triggerManualSync(Context context) {
        // Bỏ constraint NetworkType.CONNECTED cho manual request:
        // Lý do: Nếu máy bị chập chờn báo Wifi Connected nhưng no internet, Worker sẽ bị kẹt mãi ở trạng thái ENQUEUED.
        // Chạy ngay lập tức, nếu rớt mạng thì Exception sẽ làm failed worker và báo Toast cho user luôn.
        OneTimeWorkRequest manualRequest = new OneTimeWorkRequest.Builder(SchoolSyncWorker.class)
                .addTag("school_sync_manual")
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "SchoolSyncManual",
                ExistingWorkPolicy.REPLACE,
                manualRequest
        );
        return manualRequest.getId();
    }
}
