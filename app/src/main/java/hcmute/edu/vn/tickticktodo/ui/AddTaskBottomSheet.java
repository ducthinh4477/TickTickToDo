package hcmute.edu.vn.tickticktodo.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

/**
 * BottomSheet để thêm công việc mới — phong cách TickTick.
 *
 * Cách sử dụng:
 *   AddTaskBottomSheet.newInstance().show(getSupportFragmentManager(), "AddTask");
 *
 * Có thể truyền title mặc định từ Quick Add bar:
 *   AddTaskBottomSheet.newInstance("Buy groceries").show(...);
 */
public class AddTaskBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_INITIAL_TITLE = "initial_title";

    // Views
    private EditText etTitle;
    private EditText etDescription;
    private Chip chipDueDate;
    private ImageButton btnPriority;
    private ImageButton btnMoreOptions;
    private MaterialButton btnSave;
    private View layoutExtraOptions;
    private View btnAddImage;
    private View btnAddAudio;
    private View btnAddFile;

    // State
    private TaskViewModel taskViewModel;
    private final Calendar selectedDate = Calendar.getInstance(); // mặc định = today
    private int selectedPriority = 0; // 0 = None
    private boolean hasTimeSelected = false;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    // Priority cycle: 0 → 1 → 2 → 3 → 0
    private static final int[] PRIORITY_CYCLE = {0, 1, 2, 3};

    // ─── Factory methods ─────────────────────────────────────────────────────────

    public static AddTaskBottomSheet newInstance() {
        return new AddTaskBottomSheet();
    }

    /**
     * Tạo instance với title được điền sẵn (từ Quick Add bar).
     */
    public static AddTaskBottomSheet newInstance(String initialTitle) {
        AddTaskBottomSheet fragment = new AddTaskBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_TITLE, initialTitle);
        fragment.setArguments(args);
        return fragment;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Lấy ViewModel của Activity (share giữa Activity và Fragment)
        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupInitialTitle();
        setupTitleWatcher();
        setupDueDateChip();
        setupPriorityButton();
        setupMoreOptions();
        setupSaveButton();
        expandAndShowKeyboard();
    }

    // ─── View binding ────────────────────────────────────────────────────────────

    private void initViews(View view) {
        etTitle = view.findViewById(R.id.et_task_title);
        etDescription = view.findViewById(R.id.et_task_description);
        chipDueDate = view.findViewById(R.id.chip_due_date);
        btnPriority = view.findViewById(R.id.btn_priority);
        btnMoreOptions = view.findViewById(R.id.btn_more_options);
        btnSave = view.findViewById(R.id.btn_save_task);
        layoutExtraOptions = view.findViewById(R.id.layout_extra_options);
        btnAddImage = view.findViewById(R.id.btn_add_image);
        btnAddAudio = view.findViewById(R.id.btn_add_audio);
        btnAddFile = view.findViewById(R.id.btn_add_file);
    }

    /**
     * Nếu được mở từ Quick Add bar → pre-fill title.
     */
    private void setupInitialTitle() {
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_INITIAL_TITLE)) {
            String title = args.getString(ARG_INITIAL_TITLE, "");
            etTitle.setText(title);
            etTitle.setSelection(title.length()); // cursor cuối
        }

        // Mặc định due date chip hiển thị "Today"
        updateDueDateChip();
    }

    // ─── Title watcher → enable/disable Save ─────────────────────────────────────

    private void setupTitleWatcher() {
        etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSave.setEnabled(!s.toString().trim().isEmpty());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    // ─── Due Date picker ─────────────────────────────────────────────────────────

    private void setupDueDateChip() {
        chipDueDate.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        DatePickerDialog picker = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    // Tự động mở TimePicker ngay sau khi chọn ngày
                    showTimePicker();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        picker.show();
    }

    private void showTimePicker() {
        int hour = hasTimeSelected ? selectedDate.get(Calendar.HOUR_OF_DAY) : 9;
        int minute = hasTimeSelected ? selectedDate.get(Calendar.MINUTE) : 0;

        TimePickerDialog timePicker = new TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minuteOfDay) -> {
                    selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedDate.set(Calendar.MINUTE, minuteOfDay);
                    selectedDate.set(Calendar.SECOND, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);
                    hasTimeSelected = true;
                    updateDueDateChip();
                },
                hour, minute, true // is24HourView
        );
        timePicker.show();
    }

    /**
     * Cập nhật text trên chip: "Today", "Tomorrow", hoặc "Wed, Mar 5".
     */
    private void updateDueDateChip() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);

        Calendar selected = (Calendar) selectedDate.clone();
        selected.set(Calendar.HOUR_OF_DAY, 0);
        selected.set(Calendar.MINUTE, 0);
        selected.set(Calendar.SECOND, 0);
        selected.set(Calendar.MILLISECOND, 0);

        String dateText;
        if (selected.getTimeInMillis() == today.getTimeInMillis()) {
            dateText = getString(R.string.label_today);
        } else if (selected.getTimeInMillis() == tomorrow.getTimeInMillis()) {
            dateText = getString(R.string.label_tomorrow);
        } else {
            dateText = dateFormat.format(selected.getTime());
        }

        if (hasTimeSelected) {
            dateText += " " + timeFormat.format(selectedDate.getTime());
        }
        chipDueDate.setText(dateText);
    }

    // ─── Priority button (cycle through 0 → 1 → 2 → 3 → 0) ─────────────────────

    private void setupPriorityButton() {
        updatePriorityIcon();

        btnPriority.setOnClickListener(v -> {
            // Cycle to next priority
            selectedPriority = (selectedPriority + 1) % PRIORITY_CYCLE.length;
            updatePriorityIcon();
        });
    }

    private void updatePriorityIcon() {
        int colorRes;
        switch (selectedPriority) {
            case 1:  colorRes = R.color.priority_low;    break;
            case 2:  colorRes = R.color.priority_medium;  break;
            case 3:  colorRes = R.color.priority_high;    break;
            default: colorRes = R.color.priority_none;    break;
        }
        btnPriority.setColorFilter(ContextCompat.getColor(requireContext(), colorRes));
    }

    // ─── More options (Image, Audio, File) ───────────────────────────────────────

    private void setupMoreOptions() {
        btnMoreOptions.setOnClickListener(v -> {
            if (layoutExtraOptions.getVisibility() == View.GONE) {
                layoutExtraOptions.setVisibility(View.VISIBLE);
            } else {
                layoutExtraOptions.setVisibility(View.GONE);
            }
        });

        btnAddImage.setOnClickListener(v -> {
            // TODO: Tích hợp logic upload hình ảnh
        });

        btnAddAudio.setOnClickListener(v -> {
            // TODO: Tích hợp logic thu âm / upload âm thanh
        });

        btnAddFile.setOnClickListener(v -> {
            // TODO: Tích hợp logic đính kèm tệp
        });
    }

    // ─── Save ────────────────────────────────────────────────────────────────────

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> saveTask());
    }

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) return;

        String description = etDescription.getText().toString().trim();

        // Due date: nếu có chọn giờ thì giữ nguyên, không thì 00:00
        Calendar dueCal = (Calendar) selectedDate.clone();
        if (!hasTimeSelected) {
            dueCal.set(Calendar.HOUR_OF_DAY, 0);
            dueCal.set(Calendar.MINUTE, 0);
        }
        dueCal.set(Calendar.SECOND, 0);
        dueCal.set(Calendar.MILLISECOND, 0);

        Task newTask = new Task(title, description, dueCal.getTimeInMillis(), false, selectedPriority);
        taskViewModel.insert(newTask);

        dismiss();
    }

    // ─── UX: tự mở rộng bottom sheet và show keyboard ───────────────────────────

    private void expandAndShowKeyboard() {
        // Đảm bảo bottom sheet mở hết (expanded) thay vì half-expanded
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();

            dialog.setOnShowListener(d -> {
                BottomSheetDialog bsd = (BottomSheetDialog) d;
                View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setSkipCollapsed(true);
                }
            });

            // Mở keyboard và focus vào title
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE |
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }
        etTitle.requestFocus();
    }
}
