package hcmute.edu.vn.tickticktodo.ui;

import android.app.ProgressDialog;
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
import androidx.lifecycle.Observer;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.UUID;

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
    private ProgressDialog    progressDialog;

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

        showLoading(true);
        UUID workId = SchoolSyncWorker.triggerManualSync(this);

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(workId)
            .observe(this, workInfo -> {
                if (workInfo != null) {
                    if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        showLoading(false);
                        Toast.makeText(this, "Đồng bộ lịch học / bài tập thành công!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                        showLoading(false);
                        String error = workInfo.getOutputData().getString("ERROR_MSG");
                        if (error == null) error = "Đồng bộ thất bại, vui lòng kiểm tra lại link hoặc mạng.";
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    }
                }
            });
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            if (progressDialog == null) {
                progressDialog = new ProgressDialog(this);
                progressDialog.setMessage("Đang tải dữ liệu từ trường...");
                progressDialog.setCancelable(false);
            }
            progressDialog.show();
        } else {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }
    }
}
