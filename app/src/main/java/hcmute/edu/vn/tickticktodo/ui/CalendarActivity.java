package hcmute.edu.vn.tickticktodo.ui;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import hcmute.edu.vn.tickticktodo.BaseActivity;
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
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.adapter.TaskAdapter;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;
/**
 * Man hinh Lich (Calendar View).
 *
 * Bo cuc:
 *   - Nua tren: CalendarView mac dinh cua Android de chon ngay.
 *   - Nua duoi: RecyclerView hien thi tat ca Task co dueDate trong ngay duoc chon.
 *
 * Luong du lieu:
 *   User chon ngay -> tinh [startOfDay, endOfDay) -> goi ViewModel.getTasksByDate()
 *   -> observe LiveData -> submit list vao TaskAdapter -> RecyclerView cap nhat tu dong.
 *
 * Moi lan ngay thay doi, observer cu duoc remove va observer moi duoc dang ky
 * de tranh memory leak va dam bao du lieu luon dung ngay.
 */
public class CalendarActivity extends BaseActivity {
    // Factory
    public static Intent newIntent(Context context) {
        return new Intent(context, CalendarActivity.class);
    }
    // Fields
    private TaskViewModel taskViewModel;
    private TaskAdapter taskAdapter;
    // Views
    private CalendarView calendarView;
    private RecyclerView rvCalendarTasks;
    private LinearLayout layoutEmpty;
    private TextView tvSelectedDateLabel;
    private TextView tvTaskCount;
    // LiveData quan ly
    private LiveData<List<Task>> currentTasksLiveData;
    private final Observer<List<Task>> tasksObserver = this::renderTaskList;
    // Format hien thi ten ngay tren header
    private final SimpleDateFormat headerDateFormat =
            new SimpleDateFormat("EEEE, d MMMM", new Locale("vi", "VN"));
    // Lifecycle
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
        // Hien thi task cua ngay hom nay khi moi mo
        loadTasksForDate(System.currentTimeMillis());
    }
    // Setup
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
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    private void setupViewModel() {
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
    }
    private void setupTaskAdapter() {
        taskAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> startActivity(TaskDetailActivity.newIntent(this, task.getId()))
        );
    }
    private void setupRecyclerView() {
        rvCalendarTasks.setLayoutManager(new LinearLayoutManager(this));
        rvCalendarTasks.setAdapter(taskAdapter);
        rvCalendarTasks.setHasFixedSize(false);
    }
    private void setupCalendarView() {
        calendarView.setOnDateChangeListener(
                (view, year, month, dayOfMonth) -> {
                    // CalendarView tra ve month bat dau tu 0
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
    // Data loading
    private void loadTasksForDate(long dateMillis) {
        tvSelectedDateLabel.setText(headerDateFormat.format(new Date(dateMillis)));
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dateMillis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        long endOfDay = cal.getTimeInMillis();
        if (currentTasksLiveData != null) {
            currentTasksLiveData.removeObserver(tasksObserver);
        }
        currentTasksLiveData = taskViewModel.getTasksByDate(startOfDay, endOfDay);
        currentTasksLiveData.observe(this, tasksObserver);
    }
    // Render
    private void renderTaskList(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            rvCalendarTasks.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            tvTaskCount.setText(getString(R.string.calendar_task_count, 0));
        } else {
            rvCalendarTasks.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            int count = tasks.size();
            tvTaskCount.setText(getString(R.string.calendar_task_count, count));
            taskAdapter.submitList(tasks);
        }
    }
    // Lifecycle cleanup
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentTasksLiveData != null) {
            currentTasksLiveData.removeObserver(tasksObserver);
        }
    }
}