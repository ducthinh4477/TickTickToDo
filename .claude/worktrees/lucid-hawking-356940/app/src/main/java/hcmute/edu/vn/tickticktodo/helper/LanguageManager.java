package hcmute.edu.vn.doinbot.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Quản lý ngôn ngữ của ứng dụng thông qua SharedPreferences.
 *
 * Ngôn ngữ được hỗ trợ:
 *   "vi" – Tiếng Việt (mặc định)
 *   "en" – English
 *
 * Cách dùng:
 *   // Lấy ngôn ngữ hiện tại
 *   String lang = LanguageManager.getLanguage(context);
 *
 *   // Lưu ngôn ngữ mới
 *   LanguageManager.setLanguage(context, "en");
 *
 *   // Áp dụng Locale vào Context (gọi trong attachBaseContext)
 *   context = LanguageManager.applyLanguage(context);
 */
public class LanguageManager {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_LANGUAGE = "language";

    /** Ngôn ngữ mặc định khi lần đầu cài app */
    private static final String DEFAULT_LANGUAGE = "vi";

    // ─── SharedPreferences helpers ───────────────────────────────────────────────

    /**
     * Trả về mã ngôn ngữ đang lưu ("vi" hoặc "en").
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    /**
     * Lưu mã ngôn ngữ mới vào SharedPreferences.
     * Sau khi gọi hàm này, gọi thêm activity.recreate() để áp dụng ngay lập tức.
     *
     * @param languageCode "vi" hoặc "en"
     */
    public static void setLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    // ─── Locale application ──────────────────────────────────────────────────────

    /**
     * Tạo ra một Context mới đã được áp dụng Locale đúng với ngôn ngữ đã lưu.
     * Được gọi trong BaseActivity.attachBaseContext().
     *
     * @param context Context gốc (từ super.attachBaseContext)
     * @return Context mới với Locale đã được set
     */
    public static Context applyLanguage(Context context) {
        String languageCode = getLanguage(context);
        return applyLocale(context, new Locale(languageCode));
    }

    /**
     * Wrapper: áp dụng một Locale cụ thể vào context, tương thích API 17+.
     */
    private static Context applyLocale(Context context, Locale locale) {
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.setLocale(locale);
        }

        return context.createConfigurationContext(config);
    }

    // ─── Display name helpers ────────────────────────────────────────────────────

    /**
     * Trả về tên hiển thị của ngôn ngữ theo mã code.
     * Ví dụ: "vi" → "Tiếng Việt", "en" → "English"
     */
    public static String getDisplayName(String languageCode) {
        switch (languageCode) {
            case "en": return "English";
            case "vi": return "Tiếng Việt";
            default:   return languageCode;
        }
    }
}

