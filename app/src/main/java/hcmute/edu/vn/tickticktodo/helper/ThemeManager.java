package hcmute.edu.vn.tickticktodo.helper;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Quản lý chế độ giao diện (Sáng / Tối / Theo hệ thống) thông qua SharedPreferences.
 *
 * Chế độ được hỗ trợ:
 *   MODE_LIGHT  = 0 – Luôn sáng
 *   MODE_DARK   = 1 – Luôn tối
 *   MODE_SYSTEM = 2 – Theo cài đặt hệ thống (mặc định)
 *
 * Cách dùng:
 *   // Lấy chế độ hiện tại
 *   int mode = ThemeManager.getThemeMode(context);
 *
 *   // Lưu chế độ mới
 *   ThemeManager.setThemeMode(context, ThemeManager.MODE_DARK);
 *
 *   // Áp dụng chế độ (gọi trong BaseActivity.onCreate trước super)
 *   ThemeManager.applyTheme(context);
 */
public class ThemeManager {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    /** Chế độ Sáng */
    public static final int MODE_LIGHT  = 0;
    /** Chế độ Tối */
    public static final int MODE_DARK   = 1;
    /** Theo hệ thống (mặc định) */
    public static final int MODE_SYSTEM = 2;

    // ─── SharedPreferences helpers ───────────────────────────────────────────────

    /**
     * Trả về chế độ giao diện đang lưu (0, 1, hoặc 2).
     * Mặc định là MODE_SYSTEM (theo hệ thống).
     */
    public static int getThemeMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM);
    }

    /**
     * Lưu chế độ giao diện mới vào SharedPreferences.
     * Sau khi gọi hàm này, gọi thêm applyTheme() + activity.recreate() để áp dụng ngay.
     *
     * @param mode MODE_LIGHT, MODE_DARK, hoặc MODE_SYSTEM
     */
    public static void setThemeMode(Context context, int mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    // ─── Áp dụng theme ──────────────────────────────────────────────────────────

    /**
     * Đọc tùy chọn từ SharedPreferences và gọi AppCompatDelegate.setDefaultNightMode()
     * để áp dụng chế độ sáng/tối tương ứng.
     *
     * Nên gọi trong:
     *   - BaseActivity.onCreate() TRƯỚC super.onCreate()
     *   - Application.onCreate() (nếu có) để set ngay khi app khởi động
     */
    public static void applyTheme(Context context) {
        int mode = getThemeMode(context);
        int nightMode;

        switch (mode) {
            case MODE_LIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case MODE_DARK:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case MODE_SYSTEM:
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }

        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    // ─── Display name helpers ────────────────────────────────────────────────────

    /**
     * Trả về tên hiển thị của chế độ giao diện theo mã mode.
     * Dùng cho UI hiển thị trạng thái hiện tại.
     *
     * @param mode MODE_LIGHT, MODE_DARK, hoặc MODE_SYSTEM
     * @return Tên hiển thị (tiếng Anh, vì chuỗi đa ngôn ngữ nằm trong strings.xml)
     */
    public static String getDisplayName(int mode) {
        switch (mode) {
            case MODE_LIGHT:  return "Light";
            case MODE_DARK:   return "Dark";
            case MODE_SYSTEM: return "System";
            default:          return "System";
        }
    }
}

