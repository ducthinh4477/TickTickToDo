package hcmute.edu.vn.tickticktodo.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.adapter.TaskAdapter;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

/**
 * BottomSheet hiển thị danh sách công việc của một ngày cụ thể.
 * Mở ra khi người dùng bấm vào ô ngày trong Calendar Month Grid.
 *
 * Cách dùng:
 * <pre>
 *   DayDetailBottomSheet.newInstance(dayMillis)
 *       .show(getSupportFragmentManager(), DayDetailBottomSheet.TAG);
 * </pre>
 */
public class DayDetailBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "DayDetailSheet";
    private static final String ARG_DAY_MILLIS = "arg_day_millis";

    // ─── Factory ─────────────────────────────────────────────────────────────────
    public static DayDetailBottomSheet newInstance(long dayStartMillis) {
        DayDetailBottomSheet sheet = new DayDetailBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_DAY_MILLIS, dayStartMillis);
        sheet.setArguments(args);
        return sheet;
    }

    // ─── Fields ──────────────────────────────────────────────────────────────────
    private TaskViewModel taskViewModel;
    private TaskAdapter   taskAdapter;

    private TextView tvDetailDate;
    private TextView tvDetailTaskCount;
    private RecyclerView rvDayTasks;
    private View layoutDayEmpty;
    private ImageButton btnAddTaskForDay;

    private long dayMillis;

    // ─── Ở đây format tiêu đề theo kiểu: "Thứ Ba, 16 tháng 3 năm 2025" ───────
    private final SimpleDateFormat titleFormat =
            new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("vi", "VN"));

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_TickTickToDo_BottomSheetDialog);
        dayMillis = requireArguments().getLong(ARG_DAY_MILLIS,
                System.currentTimeMillis());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_day_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);

        bindViews(view);
        setupTaskList();
        setupHeader();
        observeTasks();
        setupAddButton();
    }

    // ─── Init ────────────────────────────────────────────────────────────────────

    private void bindViews(View view) {
        tvDetailDate      = view.findViewById(R.id.tv_detail_date);
        tvDetailTaskCount = view.findViewById(R.id.tv_detail_task_count);
        rvDayTasks        = view.findViewById(R.id.rv_day_tasks);
        layoutDayEmpty    = view.findViewById(R.id.layout_day_empty);
        btnAddTaskForDay  = view.findViewById(R.id.btn_add_task_for_day);
    }

    private void setupHeader() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dayMillis);
        tvDetailDate.setText(titleFormat.format(cal.getTime()));
    }

    private void setupTaskList() {
        taskAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> startActivity(TaskDetailActivity.newIntent(requireContext(), task.getId()))
        );
        rvDayTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvDayTasks.setAdapter(taskAdapter);
    }

    private void observeTasks() {
        // Tính khoảng [startOfDay, endOfDay)
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dayMillis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        long endOfDay   = startOfDay + 24L * 60 * 60 * 1000;

        taskViewModel.getTasksByDate(startOfDay, endOfDay)
                .observe(getViewLifecycleOwner(), this::renderTasks);
    }

    private void renderTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            rvDayTasks.setVisibility(View.GONE);
            layoutDayEmpty.setVisibility(View.VISIBLE);
            tvDetailTaskCount.setText(getString(R.string.calendar_task_count, 0));
        } else {
            rvDayTasks.setVisibility(View.VISIBLE);
            layoutDayEmpty.setVisibility(View.GONE);
            taskAdapter.submitList(tasks);
            tvDetailTaskCount.setText(getString(R.string.calendar_task_count, tasks.size()));
        }
    }

    private void setupAddButton() {
        btnAddTaskForDay.setOnClickListener(v -> {
            // Mở BottomSheet thêm sự kiện, pre-fill ngày đang xem
            AddCalendarEventBottomSheet sheet =
                    AddCalendarEventBottomSheet.newInstance(dayMillis);
            sheet.setOnEventSavedListener(() -> {
                // Khi lưu xong: đóng sheet này (danh sách tự reload qua LiveData)
            });
            sheet.show(getChildFragmentManager(), AddCalendarEventBottomSheet.TAG);
        });
    }
}
