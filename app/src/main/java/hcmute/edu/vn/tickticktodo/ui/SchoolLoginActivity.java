package hcmute.edu.vn.tickticktodo.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.worker.SchoolSyncWorker;

public class SchoolLoginActivity extends BaseActivity {

    private static final String VALID_DOMAIN = "hcmute.edu.vn";

    public static final String PREFS_NAME   = "SchoolPrefs";
    public static final String KEY_ICAL_URL = "ical_url";

    // ─── Views ────────────────────────────────────────────────────────────────

    private MaterialToolbar   toolbar;
    private TextInputLayout   urlInputLayout;
    private TextInputEditText urlEditText;
    private Button            btnSync;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge: layout vẽ phía sau status bar + nav bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_school_login);

        toolbar        = findViewById(R.id.toolbar);
        urlInputLayout = findViewById(R.id.urlInputLayout);
        urlEditText    = findViewById(R.id.urlEditText);
        btnSync        = findViewById(R.id.btnSync);

        // Đảm bảo nút Đồng bộ không bị thanh điều hướng che bởi navigation bar
        android.widget.LinearLayout bottomContainer = findViewById(R.id.bottomContainer);
        ViewCompat.setOnApplyWindowInsetsListener(bottomContainer, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    insets.bottom + (int)(20 * view.getResources().getDisplayMetrics().density)
            );
            return WindowInsetsCompat.CONSUMED;
        });

        // Nút Back trên Toolbar
        toolbar.setNavigationOnClickListener(v -> finish());

        // Nút Đồng bộ
        btnSync.setOnClickListener(v -> confirmAndSync());
    }

    // ─── Xác nhận & Đồng bộ ──────────────────────────────────────────────────

    private void confirmAndSync() {
        // Xóa lỗi cũ
        urlInputLayout.setError(null);

        String url = urlEditText.getText() != null
                ? urlEditText.getText().toString().trim()
                : "";

        // Validation 1: rỗng
        if (TextUtils.isEmpty(url)) {
            urlInputLayout.setError("Vui lòng dán đường dẫn lịch vào ô này.");
            urlEditText.requestFocus();
            return;
        }

        // Validation 2: không thuộc domain trường
        if (!url.contains(VALID_DOMAIN)) {
            urlInputLayout.setError("Đường dẫn không hợp lệ. Hãy chắc chắn bạn copy đúng link từ trang trường.");
            urlEditText.requestFocus();
            return;
        }

        // Hợp lệ → lưu và đồng bộ
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_ICAL_URL, url).apply();

        SchoolSyncWorker.triggerManualSync(this);

        Toast.makeText(this, "Đã lưu đường dẫn lịch thành công!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
