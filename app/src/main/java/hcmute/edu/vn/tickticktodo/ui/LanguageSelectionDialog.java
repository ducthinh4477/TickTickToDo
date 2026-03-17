package hcmute.edu.vn.tickticktodo.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
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

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.helper.LanguageManager;

/**
 * Dialog chọn ngôn ngữ hiển thị trong ứng dụng.
 *
 * Hiển thị danh sách RadioButton cho từng ngôn ngữ được hỗ trợ.
 * Khi user xác nhận, lưu ngôn ngữ qua LanguageManager rồi gọi
 * activity.recreate() để áp dụng ngay lập tức — không cần restart app.
 *
 * Cách mở dialog từ bất kỳ Activity nào:
 *   LanguageSelectionDialog.show(this);
 */
public class LanguageSelectionDialog extends Dialog {

    private final Activity host;

    // Các ngôn ngữ được hỗ trợ — thêm vào đây để mở rộng
    private static final String[] LANGUAGE_CODES = {"vi", "en"};

    public LanguageSelectionDialog(@NonNull Activity host) {
        super(host);
        this.host = host;
    }

    // ─── Static factory ──────────────────────────────────────────────────────────

    /** Tạo và hiện dialog ngay lập tức. */
    public static void show(Activity host) {
        LanguageSelectionDialog dialog = new LanguageSelectionDialog(host);
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
                .inflate(R.layout.dialog_language_selection, null);
        setContentView(root);

        setupViews(root);
    }

    // ─── Setup ──────────────────────────────────────────────────────────────────

    private void setupViews(View root) {
        RadioGroup radioGroup = root.findViewById(R.id.rg_languages);
        TextView   btnCancel  = root.findViewById(R.id.btn_lang_cancel);
        TextView   btnConfirm = root.findViewById(R.id.btn_lang_confirm);

        // Ngôn ngữ hiện tại (lấy từ SharedPreferences qua Application context
        // để không bị ảnh hưởng bởi Locale override của Activity)
        String currentLang = LanguageManager.getLanguage(host.getApplicationContext());

        // Tạo RadioButton động cho từng ngôn ngữ
        for (String code : LANGUAGE_CODES) {
            RadioButton rb = buildRadioButton(getContext(), code);
            if (code.equals(currentLang)) rb.setChecked(true);
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
            String chosenLang = (String) selected.getTag();

            // Chỉ áp dụng nếu ngôn ngữ thực sự thay đổi
            if (!chosenLang.equals(currentLang)) {
                // Lưu vào SharedPreferences (dùng applicationContext để chắc chắn)
                LanguageManager.setLanguage(host.getApplicationContext(), chosenLang);
                dismiss();
                // recreate() sẽ trigger lại attachBaseContext() → load đúng strings
                host.recreate();
            } else {
                dismiss();
            }
        });
    }

    // ─── Helper ─────────────────────────────────────────────────────────────────

    /**
     * Tạo RadioButton cho một ngôn ngữ với tag = languageCode.
     */
    private RadioButton buildRadioButton(Context context, String languageCode) {
        RadioButton rb = new RadioButton(context);
        rb.setId(View.generateViewId());
        rb.setText(LanguageManager.getDisplayName(languageCode));
        rb.setTag(languageCode);
        rb.setTextSize(16f);
        rb.setPadding(dp(8), dp(12), dp(8), dp(12));
        return rb;
    }

    private int dp(int value) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}

