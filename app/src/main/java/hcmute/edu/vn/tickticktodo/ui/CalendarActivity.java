package hcmute.edu.vn.tickticktodo.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.adapter.TaskAdapter;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

/**
 * Màn hình Lịch (Calendar View).
 *
 * Bố cục:
 *   - Nửa trên: CalendarView mặc định của Android để chọn ngày.
 *   - Nửa dưới: RecyclerView hiển thị tất cả Task có dueDate trong ngày được chọn.
 *
 * Luồng dữ liệu:
 *   User chọn ngày → tính [startOfDay, endOfDay) → gọi ViewModel.getTasksByDate()
 *   → observe LiveData → submit list vào TaskAdapter → RecyclerView cập nhật tự động.
 *
 * Mỗi lần ngày thay đổi, observer cũ được remove và observer mới được đăng ký
 * để tránh memory leak và đảm bảo dữ liệu luôn đúng ngày.
 */
public class CalendarActivity extends BaseActivity {

    // ─── Factory ────────────────────────────────────────────────────────────────

    public static Intent newIntent(Context context) {
        return new Intent(context, CalendarActivity.class);
    }

    // ─── Fields ─────────────────────────────────────────────────────────────────

    private TaskViewModel taskViewModel;
    private TaskAdapter taskAdapter;

    // Views
    private CalendarView calendarView;
    private RecyclerView rvCalendarTasks;
    private LinearLayout layoutEmpty;
    private TextView tvSelectedDateLabel;
    private TextView tvTaskCount;

    // LiveData quản lý: giữ reference để remove observer khi ngày thay đổi
    private LiveData<List<Task>> currentTasksLiveData;
    private final Observer<List<Task>> tasksObserver = this::renderTaskList;

    // Format hiển thị tên ngày trên header
    private final SimpleDateFormat headerDateFormat =
            new SimpleDateFormat("EEEE, d MMMM", new Locale("vi", "VN"));

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        initViews();
        setupToolbar();
        setupViewModel();
        setupTaskAdapter();
        setupRecyclerView();
        setupCalendarView();
        setupBackHandler();

        // Hiển thị task của ngày hôm nay khi mới mở
        loadTasksForDate(System.currentTimeMillis());
    }

    // ─── Setup ──────────────────────────────────────────────────────────────────

    private void initViews() {
        calendarView       = findViewById(R.id.calendar_view);
        rvCalendarTasks    = findViewById(R.id.rv_calendar_tasks);
        layoutEmpty        = findViewById(R.id.layout_calendar_empty);
        tvSelectedDateLabel = findViewById(R.id.tv_selected_date_label);
        tvTaskCount        = findViewById(R.id.tv_task_count);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar_calendar);
        setSupportActionBar(toolbar);
        // navigationIcon (ic_close) đóng Activity
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupViewModel() {
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
    }

    private void setupTaskAdapter() {
        taskAdapter = new TaskAdapter(
                // Checkbox checked → cập nhật DB qua ViewModel
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                // Item click → mở TaskDetailActivity
                task -> startActivity(TaskDetailActivity.newIntent(this, task.getId()))
        );
    }

    private void setupRecyclerView() {
        rvCalendarTasks.setLayoutManager(new LinearLayoutManager(this));
        rvCalendarTasks.setAdapter(taskAdapter);
        // Tối ưu: chiều cao item cố định
        rvCalendarTasks.setHasFixedSize(false);
    }

    private void setupCalendarView() {
        calendarView.setOnDateChangeListener(
                (view, year, month, dayOfMonth) -> {
                    // CalendarView trả về month bắt đầu từ 0
                    Calendar cal = Calendar.getInstance();
                    cal.set(year, month, dayOfMonth);
                    loadTasksForDate(cal.getTimeInMillis());
                });
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    // ─── Data loading ────────────────────────────────────────────────────────────

    /**
     * Tính khoảng thời gian [startOfDay, endOfDay) cho ngày chứa timestamp,
     * rồi observe LiveData tương ứng từ ViewModel.
     *
     * Mỗi lần gọi, observer cũ bị remove (tránh nhận dữ liệu ngày cũ),
     * sau đó đăng ký observer mới cho LiveData ngày mới.
     *
     * @param dateMillis bất kỳ timestamp nào trong ngày cần xem
     */
    private void loadTasksForDate(long dateMillis) {
        // ── Cập nhật header label ────────────────────────────────────────────
        tvSelectedDateLabel.setText(headerDateFormat.format(new Date(dateMillis)));

        // ── Tính [startOfDay, endOfDay) ──────────────────────────────────────
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dateMillis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        cal.add(Calendar.DAY_OF_MONTH, 1);
        long endOfDay = cal.getTimeInMillis();

        // ── Remove observer cũ (nếu có) để tránh data từ ngày trước ────────
        if (currentTasksLiveData != null) {
            currentTasksLiveData.removeObserver(tasksObserver);
        }

        // ── Đăng ký LiveData mới từ ViewModel ───────────────────────────────
        currentTasksLiveData = taskViewModel.getTasksByDate(startOfDay, endOfDay);
        currentTasksLiveData.observe(this, tasksObserver);
    }

    // ─── Render ──────────────────────────────────────────────────────────────────

    /**
     * Observer callback: nhận danh sách task và cập nhật UI.
     */
    private void renderTaskList(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            // Không có task → hiện empty state
            rvCalendarTasks.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            tvTaskCount.setText(getString(R.string.calendar_task_count, 0));
        } else {
            rvCalendarTasks.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);

            // Cập nhật badge số lượng task
            int count = tasks.size();
            tvTaskCount.setText(getString(R.string.calendar_task_count, count));

            // Đẩy dữ liệu mới vào adapter (DiffUtil tự tính diff)
            taskAdapter.submitList(tasks);
        }
    }

    // ─── Lifecycle cleanup ───────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Đảm bảo không còn observer nào treo lại sau khi Activity bị huỷ
        if (currentTasksLiveData != null) {
            currentTasksLiveData.removeObserver(tasksObserver);
        }
    }
}



