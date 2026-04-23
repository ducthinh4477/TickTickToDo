package hcmute.edu.vn.doinbot.helper;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * PermissionHelper — Quản lý tập trung toàn bộ quyền ứng dụng cần:
 *
 *  1. POST_NOTIFICATIONS (Android 13+)  — runtime permission, hỏi trực tiếp
 *  2. SCHEDULE_EXACT_ALARM (Android 12+) — special permission, dẫn vào Settings
 *  3. ACCESS_NOTIFICATION_POLICY (DND)   — special permission, dẫn vào Settings
 *
 * Cách dùng trong MainActivity:
 *   PermissionHelper helper = new PermissionHelper(this);
 *   helper.checkAndRequestAll(); // Gọi trong onStart() hoặc onCreate()
 */
public class PermissionHelper {

    public static final int REQUEST_NOTIFICATION = 1001;

    private final AppCompatActivity activity;

    public PermissionHelper(AppCompatActivity activity) {
        this.activity = activity;
    }

    // ─── API chính ───────────────────────────────────────────────────────────

    /**
     * Kiểm tra và yêu cầu tất cả quyền cần thiết theo thứ tự ưu tiên.
     * Gọi trong MainActivity.onCreate() hoặc onResume().
     */
    public void checkAndRequestAll() {
        // 1. Quyền thông báo — quan trọng nhất, hỏi trước
        if (!hasNotificationPermission()) {
            requestNotificationPermission();
            return; // Hỏi lần lượt, không hỏi đồng thời nhiều quyền
        }

        // 2. Quyền đặt exact alarm — cần cho nhắc nhở chính xác
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            showExactAlarmRationale();
            return;
        }
    }

    /**
     * Gọi riêng để kiểm tra + hướng dẫn bật chính sách DND nếu cần.
     * (Không nằm trong checkAndRequestAll() vì ít quan trọng hơn, tránh làm phiền user ngay lần đầu)
     */
    public void checkDndPolicy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isDndPolicyGranted()) {
            showDndRationale();
        }
    }

    // ─── Kiểm tra trạng thái quyền ───────────────────────────────────────────

    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        // Dưới Android 13: không cần runtime permission, mặc định được cấp
        return true;
    }

    public boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true; // API < 31: không cần quyền đặc biệt
    }

    public boolean isDndPolicyGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.isNotificationPolicyAccessGranted();
        }
        return true;
    }

    // ─── Xin quyền thông báo (POST_NOTIFICATIONS) ────────────────────────────

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;

        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
            // User đã từ chối lần trước → giải thích lý do trước khi hỏi lại
            new AlertDialog.Builder(activity)
                    .setTitle("Cần quyền thông báo")
                    .setMessage(
                        "TickTick cần quyền gửi thông báo để:\n\n" +
                        "• Nhắc nhở bạn về deadline công việc\n" +
                        "• Thông báo tổng hợp công việc mỗi sáng\n" +
                        "• Cảnh báo công việc sắp hết hạn từ lịch trường\n\n" +
                        "Nếu không cấp quyền này, bạn sẽ không nhận được bất kỳ nhắc nhở nào."
                    )
                    .setPositiveButton("Cho phép", (d, w) ->
                            ActivityCompat.requestPermissions(
                                    activity,
                                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                    REQUEST_NOTIFICATION
                            )
                    )
                    .setNegativeButton("Để sau", null)
                    .show();
        } else {
            // Lần đầu hỏi hoặc user chọn "Không hỏi lại" → vào Settings
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION
            );
        }
    }

    /**
     * Gọi từ Activity.onRequestPermissionsResult() để xử lý kết quả xin quyền thông báo.
     * Trả về true nếu quyền được cấp.
     */
    public boolean handleNotificationPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_NOTIFICATION) return false;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            // User từ chối → hướng vào Settings nếu đã chọn "Không hỏi lại"
            if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                showGoToAppSettingsDialog(
                        "Bật thông báo trong Cài đặt",
                        "Bạn đã tắt thông báo. Mở Cài đặt ứng dụng để bật lại quyền Thông báo."
                );
            }
        }
        return granted;
    }

    // ─── Quyền đặt alarm chính xác (SCHEDULE_EXACT_ALARM) ───────────────────

    private void showExactAlarmRationale() {
        new AlertDialog.Builder(activity)
                .setTitle("Cần quyền báo thức & lời nhắc")
                .setMessage(
                    "Để nhắc nhở bạn đúng giờ hạn chót công việc, TickTick cần quyền " +
                    "\"Báo thức & lời nhắc\" (Alarms & Reminders).\n\n" +
                    "Nhấn \"Cài đặt\" → bật quyền \"Cho phép đặt báo thức và lời nhắc\"."
                )
                .setPositiveButton("Mở Cài đặt", (d, w) -> openExactAlarmSettings())
                .setNegativeButton("Để sau", null)
                .show();
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                // Fallback: mở màn hình cài đặt tổng quan của app
                openAppDetailSettings();
            }
        }
    }

    // ─── Quyền DND (ACCESS_NOTIFICATION_POLICY) ──────────────────────────────

    private void showDndRationale() {
        new AlertDialog.Builder(activity)
                .setTitle("Cho phép thông báo qua chế độ Không làm phiền")
                .setMessage(
                    "Để đảm bảo nhắc nhở deadline quan trọng luôn hiển thị ngay cả khi " +
                    "bạn bật chế độ Không làm phiền (DND), hãy cấp quyền truy cập.\n\n" +
                    "Nhấn \"Cài đặt\" → bật \"TickTick\" trong danh sách ứng dụng được phép."
                )
                .setPositiveButton("Mở Cài đặt", (d, w) -> openDndSettings())
                .setNegativeButton("Để sau", null)
                .show();
    }

    private void openDndSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            } catch (Exception e) {
                openAppDetailSettings();
            }
        }
    }

    // ─── Mở trang cài đặt chi tiết của ứng dụng ─────────────────────────────

    private void showGoToAppSettingsDialog(String title, String message) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Mở Cài đặt", (d, w) -> openAppDetailSettings())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void openAppDetailSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(intent);
    }
}
