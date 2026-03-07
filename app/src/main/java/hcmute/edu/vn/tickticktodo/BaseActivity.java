package hcmute.edu.vn.tickticktodo;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import hcmute.edu.vn.tickticktodo.helper.LanguageManager;

/**
 * Activity gốc mà mọi Activity trong ứng dụng phải kế thừa.
 *
 * Nhiệm vụ duy nhất: ghi đè attachBaseContext() để inject Context
 * đã được áp dụng đúng Locale (ngôn ngữ người dùng đã chọn).
 *
 * Tại sao dùng attachBaseContext() thay vì onCreate()?
 *   - attachBaseContext() được gọi TRƯỚC khi bất kỳ resource nào được inflate.
 *   - Đây là thời điểm duy nhất để Android load đúng bộ strings (values-vi / values-en).
 *   - Nếu set trong onCreate() sẽ quá muộn, layout đã được inflate với Locale cũ.
 *
 * Cách kế thừa:
 *   // TRƯỚC (extend AppCompatActivity):
 *   public class MainActivity extends AppCompatActivity { ... }
 *
 *   // SAU (extend BaseActivity):
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
}

