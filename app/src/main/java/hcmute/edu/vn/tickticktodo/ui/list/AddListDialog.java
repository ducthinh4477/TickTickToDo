package hcmute.edu.vn.tickticktodo.ui.list;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.ui.list.IconAdapter;
import hcmute.edu.vn.tickticktodo.model.IconItem;
import hcmute.edu.vn.tickticktodo.model.TodoList;
import hcmute.edu.vn.tickticktodo.ui.task.TaskViewModel;

/**
 * Dialog cho phép người dùng tạo một {@link TodoList} mới bằng cách:
 *  1. Nhập tên danh sách vào EditText.
 *  2. Chọn một icon từ RecyclerView lưới 4 cột ({@link IconAdapter}).
 *  3. Bấm "Tạo mới" → validate → lưu qua {@link TaskViewModel}.
 *
 * Cách mở dialog:
 *   AddListDialog.show(activity, taskViewModel);
 */
public class AddListDialog extends Dialog {

    // ─── Constants ───────────────────────────────────────────────────────────────

    /** Số cột hiển thị icon trong lưới */
    private static final int ICON_GRID_SPAN = 4;

    // ─── Fields ──────────────────────────────────────────────────────────────────

    private final Activity host;
    private final TaskViewModel viewModel;

    private TextInputLayout tilListName;
    private TextInputEditText etListName;
    private IconAdapter iconAdapter;

    /** drawableResId icon đang được chọn — cập nhật qua OnIconSelectedListener */
    private int selectedIconResId;

    // ─── Static factory ──────────────────────────────────────────────────────────

    /**
     * Tạo và hiển thị dialog tạo danh sách mới.
     *
     * @param host      Activity chứa dialog
     * @param viewModel ViewModel dùng để gọi insertList()
     */
    public static void show(@NonNull Activity host, @NonNull TaskViewModel viewModel) {
        new AddListDialog(host, viewModel).show();
    }

    // ─── Constructor ─────────────────────────────────────────────────────────────

    private AddListDialog(@NonNull Activity host, @NonNull TaskViewModel viewModel) {
        super(host);
        this.host = host;
        this.viewModel = viewModel;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        View root = getLayoutInflater().inflate(R.layout.dialog_add_list, null);
        setContentView(root);

        initViews(root);
        setupIconRecyclerView(root);
        setupButtons(root);
    }

    // ─── Setup ──────────────────────────────────────────────────────────────────

    private void initViews(View root) {
        tilListName = root.findViewById(R.id.til_list_name);
        etListName  = root.findViewById(R.id.et_list_name);

        // Xóa error khi user bắt đầu nhập
        etListName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (tilListName.getError() != null) tilListName.setError(null);
            }
        });

        // Bấm Done trên bàn phím → submit
        etListName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryCreateList();
                return true;
            }
            return false;
        });
    }

    private void setupIconRecyclerView(View root) {
        List<IconItem> iconItems = buildIconList();

        // Khởi tạo selectedIconResId bằng icon đầu tiên
        selectedIconResId = iconItems.isEmpty() ? 0 : iconItems.get(0).getDrawableResId();

        iconAdapter = new IconAdapter(iconItems, drawableResId ->
                selectedIconResId = drawableResId);

        RecyclerView rvIcons = root.findViewById(R.id.rv_icon_picker);
        rvIcons.setLayoutManager(new GridLayoutManager(getContext(), ICON_GRID_SPAN));
        rvIcons.setAdapter(iconAdapter);
        // Không cần scroll bên trong Dialog → disable nested scroll
        rvIcons.setNestedScrollingEnabled(false);
        rvIcons.setHasFixedSize(true);
    }

    private void setupButtons(View root) {
        root.findViewById(R.id.btn_add_list_cancel).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.btn_add_list_create).setOnClickListener(v -> tryCreateList());
    }

    // ─── Business logic ──────────────────────────────────────────────────────────

    /**
     * Validate tên → tạo {@link TodoList} → lưu qua ViewModel → đóng dialog.
     */
    private void tryCreateList() {
        String name = "";
        if (etListName.getText() != null) {
            name = etListName.getText().toString().trim();
        }

        if (name.isEmpty()) {
            tilListName.setError(host.getString(R.string.add_list_error_empty_name));
            etListName.requestFocus();
            return;
        }

        // Màu mặc định: accent_primary (#8875FF)
        int defaultColor = host.getResources().getColor(R.color.accent_primary, host.getTheme());

        // Tạo TodoList với icon được chọn
        TodoList newList = new TodoList(name, defaultColor, selectedIconResId);
        viewModel.insertList(newList);

        Toast.makeText(host, R.string.add_list_created, Toast.LENGTH_SHORT).show();
        dismiss();
    }

    // ─── Data ───────────────────────────────────────────────────────────────────

    /**
     * Tập hợp icon có sẵn cho người dùng chọn.
     * Để thêm icon mới: thêm drawable XML + string tên + dòng mới vào đây.
     */
    private List<IconItem> buildIconList() {
        List<IconItem> list = new ArrayList<>();
        list.add(new IconItem(R.drawable.ic_list,      R.string.icon_name_list));
        list.add(new IconItem(R.drawable.ic_work,      R.string.icon_name_work));
        list.add(new IconItem(R.drawable.ic_study,     R.string.icon_name_study));
        list.add(new IconItem(R.drawable.ic_home,      R.string.icon_name_home));
        list.add(new IconItem(R.drawable.ic_shopping,  R.string.icon_name_shopping));
        list.add(new IconItem(R.drawable.ic_health,    R.string.icon_name_health));
        list.add(new IconItem(R.drawable.ic_travel,    R.string.icon_name_travel));
        list.add(new IconItem(R.drawable.ic_calendar,  R.string.icon_name_calendar));
        list.add(new IconItem(R.drawable.ic_timer,     R.string.icon_name_timer));
        return list;
    }
}

