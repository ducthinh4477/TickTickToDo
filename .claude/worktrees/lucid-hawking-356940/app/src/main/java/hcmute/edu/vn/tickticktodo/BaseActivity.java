package hcmute.edu.vn.doinbot;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import hcmute.edu.vn.doinbot.helper.LanguageManager;
import hcmute.edu.vn.doinbot.helper.ThemeManager;

/**
 * Activity gốc mà mọi Activity trong ứng dụng phải kế thừa.
 *
 * Nhiệm vụ:
 *   1. attachBaseContext() — inject Context với Locale đúng (ngôn ngữ người dùng đã chọn).
 *   2. onCreate()          — áp dụng chế độ Sáng/Tối từ ThemeManager TRƯỚC super.onCreate()
 *      để đảm bảo layout được inflate đúng bộ resource (values / values-night).
 *
 * Cách kế thừa:
 *   public class MainActivity extends BaseActivity { ... }
 *
 * Không cần thay đổi gì khác trong các Activity con.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // Wrap context với Locale đã lưu trong LanguageManager → SharedPreferences
        super.attachBaseContext(LanguageManager.applyLanguage(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Áp dụng chế độ Sáng/Tối TRƯỚC khi super.onCreate() inflate layout
        // Đọc tùy chọn từ SharedPreferences rồi gọi AppCompatDelegate.setDefaultNightMode()
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
    }
}

