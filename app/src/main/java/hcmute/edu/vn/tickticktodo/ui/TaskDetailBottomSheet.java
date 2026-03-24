package hcmute.edu.vn.tickticktodo.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

/**
 * BottomSheet hiển thị chi tiết và chỉnh sửa Task.
 */
public class TaskDetailBottomSheet extends BottomSheetDialogFragment {

    public static final String ARG_TASK_ID = "arg_task_id";

    private TaskViewModel taskViewModel;

    // Views
    private android.widget.ImageButton btnExpandCollapse;
    private CheckBox cbCompleted;
    private EditText etTitle;
    private EditText etDescription;
    private Chip chipDueDate;
    private LinearLayout btnPriorityNone;
    private LinearLayout btnPriorityLow;
    private LinearLayout btnPriorityMedium;
    private LinearLayout btnPriorityHigh;

    private BottomSheetBehavior<View> bottomSheetBehavior;

    // State
    private Task currentTask;       // task đang chỉnh sửa
    private Long selectedDueDate;   // timestamp ngày được chọn (null = không có)
    private int selectedPriority;   // 0..3
    private boolean hasTimeSelected; // user đã chọn giờ chưa
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    // ─── Static factory helper ────────────────────────────────────────────────

    public static TaskDetailBottomSheet newInstance(long taskId) {
        TaskDetailBottomSheet fragment = new TaskDetailBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_TASK_ID, taskId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Lấy ViewModel của Activity (share giữa Activity và Fragment)
        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                
                bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                            btnExpandCollapse.setImageResource(R.drawable.ic_back);
                        } else if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                            btnExpandCollapse.setImageResource(R.drawable.ic_expand);
                            saveTask(); // Auto-save khi thu nhỏ
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    }
                });
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_task_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupToolbar();
        setupPriorityButtons();
        setupDueDateChip();
        loadTaskFromArgs();
    }

    // ─── Khởi tạo view references ─────────────────────────────────────────────

    private void initViews(View view) {
        btnExpandCollapse = view.findViewById(R.id.btn_expand_collapse);
        cbCompleted       = view.findViewById(R.id.cb_completed);
        etTitle           = view.findViewById(R.id.et_title);
        etDescription     = view.findViewById(R.id.et_description);
        chipDueDate       = view.findViewById(R.id.chip_due_date);
        btnPriorityNone   = view.findViewById(R.id.btn_priority_none);
        btnPriorityLow    = view.findViewById(R.id.btn_priority_low);
        btnPriorityMedium = view.findViewById(R.id.btn_priority_medium);
        btnPriorityHigh   = view.findViewById(R.id.btn_priority_high);

        // Auto expand khi focus hoặc click
        View.OnFocusChangeListener focusChangeListener = (v, hasFocus) -> {
            if (hasFocus) expandBottomSheet();
        };
        etTitle.setOnFocusChangeListener(focusChangeListener);
        etDescription.setOnFocusChangeListener(focusChangeListener);
        
        View.OnClickListener clickListener = v -> expandBottomSheet();
        etTitle.setOnClickListener(clickListener);
        etDescription.setOnClickListener(clickListener);
    }

    private void expandBottomSheet() {
        if (bottomSheetBehavior != null && bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    // ─── Header ──────────────────────────────────────────────────────────────

    private void setupToolbar() {
        btnExpandCollapse.setOnClickListener(v -> {
            if (bottomSheetBehavior != null) {
                if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                } else {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            }
        });
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        saveTask(); // Auto-save khi dialog bị đóng hoàn toàn
        super.onDismiss(dialog);
    }

    // ─── Load task từ Arguments ───────────────────────────────────────────────

    private void loadTaskFromArgs() {
        Bundle args = getArguments();
        long taskId = (args != null) ? args.getLong(ARG_TASK_ID, -1L) : -1L;
        if (taskId == -1L) {
            Toast.makeText(requireContext(), "Không tìm thấy task", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        taskViewModel.getTaskById(taskId).observe(getViewLifecycleOwner(), task -> {
            if (task == null) {
                Toast.makeText(requireContext(), "Task không tồn tại", Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }
            currentTask = task;
            populateUI(task);
        });
    }

    // ─── Điền dữ liệu lên UI ─────────────────────────────────────────────────

    private void populateUI(Task task) {
        etTitle.setText(task.getTitle());
        etDescription.setText(task.getDescription());
        cbCompleted.setChecked(task.isCompleted());

        selectedDueDate = task.getDueDate();
        if (selectedDueDate != null) {
            Calendar check = Calendar.getInstance();
            check.setTimeInMillis(selectedDueDate);
            hasTimeSelected = check.get(Calendar.HOUR_OF_DAY) != 0
                    || check.get(Calendar.MINUTE) != 0;
        } else {
            hasTimeSelected = false;
        }
        updateDueDateChip(selectedDueDate);

        selectedPriority = task.getPriority();
        highlightPriorityButton(selectedPriority);
    }

    // ─── Due Date Chip ────────────────────────────────────────────────────────

    private void setupDueDateChip() {
        chipDueDate.setOnClickListener(v -> {
            expandBottomSheet();
            showDatePicker();
        });
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        if (selectedDueDate != null) {
            cal.setTimeInMillis(selectedDueDate);
        }

        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth, 0, 0, 0);
                    selected.set(Calendar.MILLISECOND, 0);
                    selectedDueDate = selected.getTimeInMillis();
                    showTimePicker();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        if (selectedDueDate != null) {
            cal.setTimeInMillis(selectedDueDate);
        }
        int hour = hasTimeSelected ? cal.get(Calendar.HOUR_OF_DAY) : 9;
        int minute = hasTimeSelected ? cal.get(Calendar.MINUTE) : 0;

        TimePickerDialog timePicker = new TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minuteOfDay) -> {
                    Calendar updated = Calendar.getInstance();
                    if (selectedDueDate != null) {
                        updated.setTimeInMillis(selectedDueDate);
                    }
                    updated.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    updated.set(Calendar.MINUTE, minuteOfDay);
                    updated.set(Calendar.SECOND, 0);
                    updated.set(Calendar.MILLISECOND, 0);
                    selectedDueDate = updated.getTimeInMillis();
                    hasTimeSelected = true;
                    updateDueDateChip(selectedDueDate);
                },
                hour, minute, true
        );
        timePicker.show();
    }

    private void updateDueDateChip(Long timestamp) {
        if (timestamp == null) {
            chipDueDate.setText(getString(R.string.label_no_date));
            return;
        }

        Calendar today = Calendar.getInstance();
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(timestamp);

        String chipText;
        if (isSameDay(selected, today)) {
            chipText = getString(R.string.label_today);
        } else if (isSameDay(selected, tomorrow)) {
            chipText = getString(R.string.label_tomorrow);
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            chipText = fmt.format(selected.getTime());
        }

        if (hasTimeSelected) {
            chipText += " " + timeFormat.format(selected.getTime());
        }
        chipDueDate.setText(chipText);
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // ─── Priority buttons ─────────────────────────────────────────────────────

    private void setupPriorityButtons() {
        btnPriorityNone.setOnClickListener(v -> selectPriority(0));
        btnPriorityLow.setOnClickListener(v -> selectPriority(1));
        btnPriorityMedium.setOnClickListener(v -> selectPriority(2));
        btnPriorityHigh.setOnClickListener(v -> selectPriority(3));
    }

    private void selectPriority(int priority) {
        expandBottomSheet();
        selectedPriority = priority;
        highlightPriorityButton(priority);
    }

    private void highlightPriorityButton(int priority) {
        btnPriorityNone.setAlpha(priority == 0 ? 1f : 0.35f);
        btnPriorityLow.setAlpha(priority == 1 ? 1f : 0.35f);
        btnPriorityMedium.setAlpha(priority == 2 ? 1f : 0.35f);
        btnPriorityHigh.setAlpha(priority == 3 ? 1f : 0.35f);
    }

    // ─── Lưu task ─────────────────────────────────────────────────────────────

    private void saveTask() {
        if (currentTask == null) return;

        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            // Không save nếu title trống
            return;
        }

        currentTask.setTitle(title);
        currentTask.setDescription(etDescription.getText().toString().trim());
        currentTask.setDueDate(selectedDueDate);
        currentTask.setPriority(selectedPriority);
        currentTask.setCompleted(cbCompleted.isChecked());

        taskViewModel.update(currentTask);
    }
}