package hcmute.edu.vn.tickticktodo.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.helper.ThemeManager;
import hcmute.edu.vn.tickticktodo.core.background.FloatingAssistantService;

/**
 * Dialog chọn chế độ giao diện Sáng / Tối / Theo hệ thống.
 *
 * Hiển thị danh sách RadioButton cho từng chế độ giao diện được hỗ trợ.
 * Khi user xác nhận, lưu tùy chọn qua ThemeManager rồi gọi
 * ThemeManager.applyTheme() + activity.recreate() để áp dụng ngay lập tức.
 *
 * Cách mở dialog từ bất kỳ Activity nào:
 *   ThemeSelectionDialog.show(this);
 */
public class ThemeSelectionDialog extends Dialog {

    private final Activity host;

    // Các chế độ được hỗ trợ — mode code + resource string
    private static final int[] THEME_MODES = {
            ThemeManager.MODE_LIGHT,
            ThemeManager.MODE_DARK,
            ThemeManager.MODE_SYSTEM
    };

    private static final int[] THEME_LABEL_RES = {
            R.string.theme_light,
            R.string.theme_dark,
            R.string.theme_system
    };

    public ThemeSelectionDialog(@NonNull Activity host) {
        super(host);
        this.host = host;
    }

    // ─── Static factory ──────────────────────────────────────────────────────────

    /** Tạo và hiện dialog ngay lập tức. */
    public static void show(Activity host) {
        ThemeSelectionDialog dialog = new ThemeSelectionDialog(host);
        dialog.show();
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Xóa title mặc định, set transparent background cho dialog
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        View root = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_theme_selection, null);
        setContentView(root);

        setupViews(root);
    }

    // ─── Setup ──────────────────────────────────────────────────────────────────

    private void setupViews(View root) {
        RadioGroup radioGroup = root.findViewById(R.id.rg_themes);
        TextView   btnCancel  = root.findViewById(R.id.btn_theme_cancel);
        TextView   btnConfirm = root.findViewById(R.id.btn_theme_confirm);

        // Chế độ hiện tại (dùng applicationContext để tránh bị ảnh hưởng override)
        int currentMode = ThemeManager.getThemeMode(host.getApplicationContext());

        // Tạo RadioButton động cho từng chế độ giao diện
        for (int i = 0; i < THEME_MODES.length; i++) {
            RadioButton rb = buildRadioButton(getContext(), THEME_MODES[i], THEME_LABEL_RES[i]);
            if (THEME_MODES[i] == currentMode) rb.setChecked(true);
            radioGroup.addView(rb);
        }

        // Nút Hủy
        btnCancel.setOnClickListener(v -> dismiss());

        // Nút Áp dụng
        btnConfirm.setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            if (checkedId == View.NO_ID) {
                dismiss();
                return;
            }

            RadioButton selected = root.findViewById(checkedId);
            int chosenMode = (int) selected.getTag();

            // Chỉ áp dụng nếu chế độ thực sự thay đổi
            if (chosenMode != currentMode) {
                // Lưu vào SharedPreferences
                ThemeManager.setThemeMode(host.getApplicationContext(), chosenMode);
                // Áp dụng ngay lập tức qua AppCompatDelegate
                ThemeManager.applyTheme(host.getApplicationContext());
                requestFloatingAssistantThemeRefresh(host);
                dismiss();
                // recreate() để Activity load lại đúng bộ resource (colors, drawables)
                host.recreate();
            } else {
                dismiss();
            }
        });
    }

    // ─── Helper ─────────────────────────────────────────────────────────────────

    /**
     * Tạo RadioButton cho một chế độ giao diện với tag = mode code.
     */
    private RadioButton buildRadioButton(Context context, int mode, int labelRes) {
        RadioButton rb = new RadioButton(context);
        rb.setId(View.generateViewId());
        rb.setText(context.getString(labelRes));
        rb.setTag(mode);
        rb.setTextSize(16f);
        rb.setTextColor(context.getResources().getColor(R.color.text_primary, context.getTheme()));
        rb.setPadding(dp(8), dp(12), dp(8), dp(12));
        return rb;
    }

    private int dp(int value) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private void requestFloatingAssistantThemeRefresh(Activity host) {
        SharedPreferences prefs = host.getSharedPreferences("TickTickPrefs", Context.MODE_PRIVATE);
        boolean floatingEnabled = prefs.getBoolean("floating_assistant_enabled", false);
        if (!floatingEnabled) {
            return;
        }

        Intent intent = new Intent(host, FloatingAssistantService.class);
        intent.setAction(FloatingAssistantService.ACTION_REFRESH_THEME);
        try {
            ContextCompat.startForegroundService(host, intent);
        } catch (Exception first) {
            try {
                host.startService(intent);
            } catch (Exception ignored) {
            }
        }
    }
}

