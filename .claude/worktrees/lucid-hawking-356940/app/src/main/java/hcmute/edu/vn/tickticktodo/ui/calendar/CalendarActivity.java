package hcmute.edu.vn.doinbot.ui.calendar;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hcmute.edu.vn.doinbot.BaseActivity;
import hcmute.edu.vn.doinbot.R;
import hcmute.edu.vn.doinbot.ui.calendar.CalendarAdapter;
import hcmute.edu.vn.doinbot.model.CalendarDay;
import hcmute.edu.vn.doinbot.model.Task;
import hcmute.edu.vn.doinbot.ui.task.TaskViewModel;

/**
 * CalendarActivity — Màn hình Lịch tháng kiểu Google Calendar (Month Grid View).
 *
 * Bố cục:
 *   • Toolbar + nút Back
 *   • Header điều hướng: "<" / Tháng X năm YYYY / ">" / Hôm nay
 *   • Hàng thứ: T2 T3 T4 T5 T6 T7 CN
 *   • RecyclerView GridLayoutManager(span=7) — 42 ô ngày (6 tuần × 7 ngày)
 *   • FAB thêm sự kiện mới
 *
 * Luồng dữ liệu:
 *   buildCalendarGrid() xây 42 CalendarDay →
 *   getTasksByDateRange(startGrid, endGrid) trả về LiveData<List<Task>> →
 *   assignTasksToDays() phân bổ task vào đúng ô ngày →
 *   CalendarAdapter.updateTasks() refresh lưới.
 */
public class CalendarActivity extends BaseActivity {

    // ─── Factory ───────────────────────────────────────────────────────────────────────────────
    public static Intent newIntent(Context context) {
        return new Intent(context, CalendarActivity.class);
    }

    // ─── State ────────────────────────────────────────────────────────────────────────────────
    private int currentMonth;
    private int currentYear;
    private List<CalendarDay> calendarDays = new ArrayList<>();

    // ─── ViewModel & LiveData ───────────────────────────────────────────────────────────────────
    private TaskViewModel taskViewModel;
    private LiveData<List<Task>> currentRangeLiveData;
    private final Observer<List<Task>> rangeObserver =
            tasks -> assignTasksToDays(tasks != null ? tasks : new ArrayList<>());

    // ─── Views ────────────────────────────────────────────────────────────────────────────────
    private TextView tvMonthTitle;
    private RecyclerView rvCalendarGrid;
    private CalendarAdapter calendarAdapter;

    // ─── Formatters ────────────────────────────────────────────────────────────────────────────
    private final SimpleDateFormat monthTitleFormat =
            new SimpleDateFormat("'Tháng' M 'năm' yyyy", new Locale("vi", "VN"));
    private final SimpleDateFormat dayKeyFormat =
            new SimpleDateFormat("yyyyMMdd", Locale.US);

    // ─── Lifecycle ─────────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_calendar);

        Calendar today = Calendar.getInstance();
        currentMonth = today.get(Calendar.MONTH);
        currentYear  = today.get(Calendar.YEAR);

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        setupToolbar();
        setupMonthNavigation();
        setupCalendarGrid();
        setupFab();
        setupBackHandler();
        applyWindowInsets();

        navigateToMonth(currentMonth, currentYear);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentRangeLiveData != null)
            currentRangeLiveData.removeObserver(rangeObserver);
    }

    // ─── Setup ────────────────────────────────────────────────────────────────────────────────

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar_calendar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupMonthNavigation() {
        tvMonthTitle = findViewById(R.id.tv_month_title);

        ImageButton btnPrev  = findViewById(R.id.btn_prev_month);
        ImageButton btnNext  = findViewById(R.id.btn_next_month);
        Button      btnToday = findViewById(R.id.btn_today);

        btnPrev.setOnClickListener(v -> {
            if (currentMonth == Calendar.JANUARY) {
                currentMonth = Calendar.DECEMBER;
                currentYear--;
            } else {
                currentMonth--;
            }
            navigateToMonth(currentMonth, currentYear);
        });

        btnNext.setOnClickListener(v -> {
            if (currentMonth == Calendar.DECEMBER) {
                currentMonth = Calendar.JANUARY;
                currentYear++;
            } else {
                currentMonth++;
            }
            navigateToMonth(currentMonth, currentYear);
        });

        btnToday.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            currentMonth = now.get(Calendar.MONTH);
            currentYear  = now.get(Calendar.YEAR);
            navigateToMonth(currentMonth, currentYear);
        });
    }

    private void setupCalendarGrid() {
        rvCalendarGrid = findViewById(R.id.rv_calendar_grid);

        calendarAdapter = new CalendarAdapter(day -> {
            if (!day.isCurrentMonth()) return;
            int pos = calendarDays.indexOf(day);
            calendarAdapter.setSelectedPosition(pos);
            DayDetailBottomSheet.newInstance(day.getStartOfDayMillis())
                    .show(getSupportFragmentManager(), DayDetailBottomSheet.TAG);
        });

        rvCalendarGrid.setLayoutManager(new GridLayoutManager(this, 7));
        rvCalendarGrid.setAdapter(calendarAdapter);
        rvCalendarGrid.setHasFixedSize(true);
    }

    private void setupFab() {
        FloatingActionButton fab = findViewById(R.id.fab_add_event);
        fab.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.set(currentYear, currentMonth, 1);
            AddCalendarEventBottomSheet sheet =
                    AddCalendarEventBottomSheet.newInstance(cal.getTimeInMillis());
            sheet.setOnEventSavedListener(() -> { /* LiveData tự refresh */ });
            sheet.show(getSupportFragmentManager(), AddCalendarEventBottomSheet.TAG);
        });
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { finish(); }
        });
    }

    private void applyWindowInsets() {
        View rootLayout = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Toolbar toolbar = findViewById(R.id.toolbar_calendar);
            toolbar.setPadding(
                    toolbar.getPaddingLeft(),
                    insets.top,
                    toolbar.getPaddingRight(),
                    toolbar.getPaddingBottom());
            android.view.ViewGroup.LayoutParams lp = toolbar.getLayoutParams();
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true);
            lp.height = getResources().getDimensionPixelSize(tv.resourceId) + insets.top;
            toolbar.setLayoutParams(lp);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    // ─── Core Logic ────────────────────────────────────────────────────────────────────────────

    /**
     * Chuyển sang tháng mới: cập nhật tiêu đề, xây lại lưới, load task.
     */
    private void navigateToMonth(int month, int year) {
        currentMonth = month;
        currentYear  = year;
        updateMonthTitle();
        calendarDays = buildCalendarGrid();
        calendarAdapter.submitList(new ArrayList<>(calendarDays));
        loadRangeTasks();
    }

    private void updateMonthTitle() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, currentYear);
        cal.set(Calendar.MONTH, currentMonth);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        tvMonthTitle.setText(monthTitleFormat.format(cal.getTime()));
    }

    /**
     * Xây danh sách 42 ô CalendarDay cho tháng hiện tại.
     *
     * Tuần bắt đầu từ Thứ Hai (Mon-first).
     * Offset = số ô trống đầu lưới (ngày tháng trước).
     * Tổng luôn = 42 ô = 6 hàng × 7 cột.
     */
    private List<CalendarDay> buildCalendarGrid() {
        List<CalendarDay> result = new ArrayList<>(42);

        Calendar today = Calendar.getInstance();
        int todayDay   = today.get(Calendar.DAY_OF_MONTH);
        int todayMonth = today.get(Calendar.MONTH);
        int todayYear  = today.get(Calendar.YEAR);

        Calendar firstOfMonth = Calendar.getInstance();
        firstOfMonth.set(currentYear, currentMonth, 1, 0, 0, 0);
        firstOfMonth.set(Calendar.MILLISECOND, 0);

        // Mon-first offset: SUNDAY=1→6, MONDAY=2→0, TUE=3→1 … SAT=7→5
        int rawDow = firstOfMonth.get(Calendar.DAY_OF_WEEK);
        int offset = (rawDow == Calendar.SUNDAY) ? 6 : rawDow - 2;
        int daysInMonth = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH);

        // ── Ngày cuối tháng trước (mờ) ──
        Calendar prevCal = (Calendar) firstOfMonth.clone();
        prevCal.add(Calendar.MONTH, -1);
        int daysInPrev = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = offset - 1; i >= 0; i--) {
            result.add(new CalendarDay(daysInPrev - i,
                    prevCal.get(Calendar.MONTH),
                    prevCal.get(Calendar.YEAR), false));
        }

        // ── Ngày của tháng hiện tại ──
        for (int d = 1; d <= daysInMonth; d++) {
            boolean isToday = (d == todayDay
                    && currentMonth == todayMonth
                    && currentYear == todayYear);
            result.add(new CalendarDay(d, currentMonth, currentYear, true, isToday));
        }

        // ── Ngày đầu tháng sau (mờ) cho đủ 42 ──
        Calendar nextCal = (Calendar) firstOfMonth.clone();
        nextCal.add(Calendar.MONTH, 1);
        int remaining = 42 - result.size();
        for (int d = 1; d <= remaining; d++) {
            result.add(new CalendarDay(d,
                    nextCal.get(Calendar.MONTH),
                    nextCal.get(Calendar.YEAR), false));
        }

        return result;
    }

    /**
     * Remove observer cũ và đăng ký observer mới cho khoảng 42 ngày của lưới.
     */
    private void loadRangeTasks() {
        if (calendarDays.isEmpty()) return;
        if (currentRangeLiveData != null)
            currentRangeLiveData.removeObserver(rangeObserver);

        long startMillis = calendarDays.get(0).getStartOfDayMillis();
        long endMillis   = calendarDays.get(41).getEndOfDayMillis();

        currentRangeLiveData = taskViewModel.getTasksByDateRange(startMillis, endMillis);
        currentRangeLiveData.observe(this, rangeObserver);
    }

    /**
     * Phân bổ task từ query vào đúng ô ngày, O(T + 42).
     */
    private void assignTasksToDays(List<Task> tasks) {
        // Build map: "yyyyMMdd" → List<Task>
        Map<String, List<Task>> taskMap = new HashMap<>();
        for (Task task : tasks) {
            if (task.getDueDate() == null) continue;
            String key = dayKeyFormat.format(new Date(task.getDueDate()));
            List<Task> bucket = taskMap.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                taskMap.put(key, bucket);
            }
            bucket.add(task);
        }

        // Gán task vào từng CalendarDay
        for (CalendarDay day : calendarDays) {
            String key      = dayKeyFormat.format(new Date(day.getStartOfDayMillis()));
            List<Task> list = taskMap.get(key);
            day.setTasks(list != null ? list : new ArrayList<>());
        }

        calendarAdapter.updateTasks(calendarDays);
    }
}
