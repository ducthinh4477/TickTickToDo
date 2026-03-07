package hcmute.edu.vn.tickticktodo.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

/**
 * Màn hình xem và chỉnh sửa chi tiết một Task.
 */
public class TaskDetailActivity extends BaseActivity {

    /** Key dùng để truyền taskId qua Intent */
    public static final String EXTRA_TASK_ID = "extra_task_id";

    private TaskViewModel taskViewModel;

    // Views
    private Toolbar toolbar;
    private CheckBox cbCompleted;
    private EditText etTitle;
    private EditText etDescription;
    private Chip chipDueDate;
    private LinearLayout btnPriorityNone;
    private LinearLayout btnPriorityLow;
    private LinearLayout btnPriorityMedium;
    private LinearLayout btnPriorityHigh;

    // State
    private Task currentTask;       // task đang chỉnh sửa
    private Long selectedDueDate;   // timestamp ngày được chọn (null = không có)
    private int selectedPriority;   // 0..3
    private boolean hasTimeSelected; // user đã chọn giờ chưa
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    // ─── Static factory helper ────────────────────────────────────────────────

    /**
     * Tạo Intent để mở TaskDetailActivity với taskId cho trước.
     * Gọi từ bên ngoài thay vì tự xây Intent.
     */
    public static Intent newIntent(Context context, long taskId) {
        Intent intent = new Intent(context, TaskDetailActivity.class);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        return intent;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_detail);

        initViews();
        setupToolbar();
        setupPriorityButtons();
        setupDueDateChip();
        loadTaskFromIntent();
    }

    // ─── Khởi tạo view references ─────────────────────────────────────────────

    private void initViews() {
        toolbar        = findViewById(R.id.toolbar);
        cbCompleted    = findViewById(R.id.cb_completed);
        etTitle        = findViewById(R.id.et_title);
        etDescription  = findViewById(R.id.et_description);
        chipDueDate    = findViewById(R.id.chip_due_date);
        btnPriorityNone   = findViewById(R.id.btn_priority_none);
        btnPriorityLow    = findViewById(R.id.btn_priority_low);
        btnPriorityMedium = findViewById(R.id.btn_priority_medium);
        btnPriorityHigh   = findViewById(R.id.btn_priority_high);
    }

    // ─── Toolbar ──────────────────────────────────────────────────────────────

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        // Navigation icon (X) → đóng màn hình mà không lưu
        toolbar.setNavigationOnClickListener(v -> finish());

        // Nút Save (dấu tick ✓)
        findViewById(R.id.btn_save).setOnClickListener(v -> saveTask());
    }

    // ─── Load task từ DB ──────────────────────────────────────────────────────

    private void loadTaskFromIntent() {
        long taskId = getIntent().getLongExtra(EXTRA_TASK_ID, -1L);
        if (taskId == -1L) {
            Toast.makeText(this, "Không tìm thấy task", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        // Observe LiveData: tự động điền UI khi data sẵn sàng
        taskViewModel.getTaskById(taskId).observe(this, task -> {
            if (task == null) {
                Toast.makeText(this, "Task không tồn tại", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            // Lưu lại reference để dùng khi update
            currentTask = task;
            populateUI(task);
        });
    }

    // ─── Điền dữ liệu lên UI ─────────────────────────────────────────────────

    private void populateUI(Task task) {
        etTitle.setText(task.getTitle());
        etDescription.setText(task.getDescription());
        cbCompleted.setChecked(task.isCompleted());

        // Cài due date
        selectedDueDate = task.getDueDate();
        // Kiểm tra xem due date có chứa giờ không (khác 00:00)
        if (selectedDueDate != null) {
            Calendar check = Calendar.getInstance();
            check.setTimeInMillis(selectedDueDate);
            hasTimeSelected = check.get(Calendar.HOUR_OF_DAY) != 0
                    || check.get(Calendar.MINUTE) != 0;
        } else {
            hasTimeSelected = false;
        }
        updateDueDateChip(selectedDueDate);

        // Cài priority
        selectedPriority = task.getPriority();
        highlightPriorityButton(selectedPriority);
    }

    // ─── Due Date Chip ────────────────────────────────────────────────────────

    private void setupDueDateChip() {
        chipDueDate.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        if (selectedDueDate != null) {
            cal.setTimeInMillis(selectedDueDate);
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth, 0, 0, 0);
                    selected.set(Calendar.MILLISECOND, 0);
                    selectedDueDate = selected.getTimeInMillis();
                    // Mở TimePicker ngay sau khi chọn ngày
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
                this,
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

        // Hiện "Today" / "Tomorrow" hoặc ngày định dạng
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
        selectedPriority = priority;
        highlightPriorityButton(priority);
    }

    /**
     * Làm nổi bật nút priority đang được chọn bằng cách thay đổi alpha
     * của các nút không được chọn.
     */
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
            etTitle.setError(getString(R.string.error_title_empty));
            etTitle.requestFocus();
            return;
        }

        // Cập nhật các field của task gốc
        currentTask.setTitle(title);
        currentTask.setDescription(etDescription.getText().toString().trim());
        currentTask.setDueDate(selectedDueDate);
        currentTask.setPriority(selectedPriority);
        currentTask.setCompleted(cbCompleted.isChecked());

        // Ghi vào DB qua ViewModel (background thread)
        taskViewModel.update(currentTask);

        Toast.makeText(this, getString(R.string.detail_saved), Toast.LENGTH_SHORT).show();
        finish();
    }
}
