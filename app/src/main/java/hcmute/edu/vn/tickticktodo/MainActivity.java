package hcmute.edu.vn.tickticktodo;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.adapter.TaskAdapter;
import hcmute.edu.vn.tickticktodo.helper.SwipeToDeleteCallback;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.ui.AddTaskBottomSheet;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

public class MainActivity extends AppCompatActivity {

    private TaskViewModel taskViewModel;

    // UI components
    private TextView tvHeaderDate;
    private RecyclerView rvTasks;
    private LinearLayout layoutEmpty;
    private EditText etQuickAdd;
    private ImageButton btnSendTask;
    private FloatingActionButton fabAddTask;

    // Adapters
    private TaskAdapter incompleteAdapter;
    private TaskAdapter completedAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupHeader();
        setupRecyclerView();
        setupQuickAdd();
        setupFab();
        setupViewModel();
    }

    // ─── Khởi tạo view references ────────────────────────────────────────────────

    private void initViews() {
        tvHeaderDate = findViewById(R.id.tv_header_date);
        rvTasks = findViewById(R.id.rv_tasks);
        layoutEmpty = findViewById(R.id.layout_empty);
        etQuickAdd = findViewById(R.id.et_quick_add);
        btnSendTask = findViewById(R.id.btn_send_task);
        fabAddTask = findViewById(R.id.fab_add_task);
    }

    // ─── Header: hiển thị ngày hiện tại giống TickTick ───────────────────────────

    private void setupHeader() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        tvHeaderDate.setText(dateFormat.format(new Date()));
    }

    // ─── RecyclerView setup ──────────────────────────────────────────────────────

    private void setupRecyclerView() {
        // Adapter cho task chưa hoàn thành
        incompleteAdapter = new TaskAdapter(
                // Checkbox toggle → đánh dấu hoàn thành
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                // Item click → hiện Toast (sau này mở màn hình chi tiết)
                task -> Toast.makeText(this,
                        "Task: " + task.getTitle(), Toast.LENGTH_SHORT).show()
        );

        // Adapter cho task đã hoàn thành
        completedAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> Toast.makeText(this,
                        "Task: " + task.getTitle(), Toast.LENGTH_SHORT).show()
        );

        // Sử dụng ConcatAdapter để ghép: [incomplete] + [section header] + [completed]
        // Tạm thời dùng single adapter merged list cho đơn giản
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(incompleteAdapter);

        // Swipe-to-delete: vuốt trái để xóa task
        SwipeToDeleteCallback swipeCallback = new SwipeToDeleteCallback(this, this::handleSwipeDelete);
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvTasks);
    }

    // ─── Swipe to delete + Undo ───────────────────────────────────────────────

    /**
     * Xử lý khi item bị vuốt xóa:
     * 1. Lấy Task tại vị trí đó
     * 2. Xóa khỏi DB qua ViewModel
     * 3. Hiện Snackbar với nút Undo — nếu nhấn Undo thì insert lại
     */
    private void handleSwipeDelete(int position) {
        Task deletedTask = incompleteAdapter.getCurrentList().get(position);

        // Xóa task khỏi database
        taskViewModel.delete(deletedTask);

        // Hiện Snackbar với Undo
        Snackbar.make(rvTasks, "Đã xóa: " + deletedTask.getTitle(), Snackbar.LENGTH_LONG)
                .setAction("Undo", v -> {
                    // Khôi phục task: insert lại với cùng dữ liệu
                    // Reset id = 0 để Room tự sinh id mới (tránh conflict)
                    Task restoredTask = new Task(
                            deletedTask.getTitle(),
                            deletedTask.getDescription(),
                            deletedTask.getDueDate(),
                            deletedTask.isCompleted(),
                            deletedTask.getPriority()
                    );
                    taskViewModel.insert(restoredTask);
                })
                .setActionTextColor(getResources().getColor(R.color.priority_low, getTheme()))
                .show();
    }

    // ─── Quick Add bar ───────────────────────────────────────────────────────────

    private void setupQuickAdd() {
        // Hiện/ẩn nút gửi khi có text
        etQuickAdd.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSendTask.setVisibility(
                        s.toString().trim().isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        // Click nút gửi → thêm task
        btnSendTask.setOnClickListener(v -> submitQuickAdd());

        // Nhấn Done trên keyboard → thêm task
        etQuickAdd.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitQuickAdd();
                return true;
            }
            return false;
        });
    }

    /**
     * Lấy text từ Quick Add, tạo task mới với dueDate = hôm nay, insert vào DB.
     */
    private void submitQuickAdd() {
        String title = etQuickAdd.getText().toString().trim();
        if (title.isEmpty()) return;

        // Due date = đầu ngày hôm nay (00:00:00)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Task newTask = new Task(title, "", cal.getTimeInMillis(), false, 0);
        taskViewModel.insert(newTask);

        // Clear input & ẩn keyboard
        etQuickAdd.setText("");
        hideKeyboard();
    }

    // ─── FAB → mở BottomSheet ────────────────────────────────────────────────────

    private void setupFab() {
        fabAddTask.setOnClickListener(v -> openAddTaskBottomSheet(null));
    }

    /**
     * Mở AddTaskBottomSheet. Nếu initialTitle != null, sẽ pre-fill title.
     */
    private void openAddTaskBottomSheet(String initialTitle) {
        AddTaskBottomSheet bottomSheet;
        if (initialTitle != null && !initialTitle.isEmpty()) {
            bottomSheet = AddTaskBottomSheet.newInstance(initialTitle);
        } else {
            bottomSheet = AddTaskBottomSheet.newInstance();
        }
        bottomSheet.show(getSupportFragmentManager(), "AddTaskBottomSheet");
    }

    // ─── ViewModel + LiveData observe ────────────────────────────────────────────

    private void setupViewModel() {
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        // Observe task chưa hoàn thành hôm nay
        taskViewModel.getTodayIncompleteTasks().observe(this, incompleteTasks -> {
            if (incompleteTasks == null) incompleteTasks = new ArrayList<>();

            // Lấy completed tasks hiện tại (nếu đã có)
            List<Task> completedTasks = taskViewModel.getTodayCompletedTasks().getValue();
            if (completedTasks == null) completedTasks = new ArrayList<>();

            updateUI(incompleteTasks, completedTasks);
        });

        // Observe task đã hoàn thành hôm nay
        taskViewModel.getTodayCompletedTasks().observe(this, completedTasks -> {
            if (completedTasks == null) completedTasks = new ArrayList<>();

            List<Task> incompleteTasks = taskViewModel.getTodayIncompleteTasks().getValue();
            if (incompleteTasks == null) incompleteTasks = new ArrayList<>();

            updateUI(incompleteTasks, completedTasks);
        });
    }

    /**
     * Cập nhật RecyclerView với merged list: incomplete + completed.
     * Hiện/ẩn empty state tùy theo tổng số task.
     */
    private void updateUI(List<Task> incompleteTasks, List<Task> completedTasks) {
        // Merge cả 2 list: incomplete trước, completed sau
        List<Task> mergedList = new ArrayList<>();
        mergedList.addAll(incompleteTasks);
        mergedList.addAll(completedTasks);

        incompleteAdapter.submitList(mergedList);

        // Hiện/ẩn empty state
        boolean isEmpty = mergedList.isEmpty();
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // ─── Utilities ───────────────────────────────────────────────────────────────

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View currentFocus = getCurrentFocus();
        if (imm != null && currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }
}