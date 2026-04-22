package hcmute.edu.vn.tickticktodo.ui.habit;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.ui.habit.HabitHeatmapAdapter;
import hcmute.edu.vn.tickticktodo.ui.habit.HabitListAdapter;
import hcmute.edu.vn.tickticktodo.model.Habit;
import hcmute.edu.vn.tickticktodo.model.HabitHeatmapCell;
import hcmute.edu.vn.tickticktodo.ui.habit.HabitTrackerViewModel;

public class HabitTrackerFragment extends Fragment {

    private HabitTrackerViewModel viewModel;
    private HabitListAdapter habitListAdapter;
    private HabitHeatmapAdapter heatmapAdapter;
    private TextView tvSelectedHabit;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_habit_tracker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HabitTrackerViewModel.class);
        tvSelectedHabit = view.findViewById(R.id.tv_selected_habit_name);

        setupHabitList(view);
        setupHeatmap(view);
        setupBackButton(view);
        setupAddHabitButton(view);
        observeData();
    }

    private void setupBackButton(View root) {
        View btnBack = root.findViewById(R.id.btn_habit_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> requireActivity().finish());
        }
    }

    private void setupHabitList(View root) {
        RecyclerView rvHabits = root.findViewById(R.id.rv_habits);
        habitListAdapter = new HabitListAdapter(new HabitListAdapter.HabitActionListener() {
            @Override
            public void onHabitSelected(Habit habit) {
                viewModel.selectHabit(habit.getId());
                habitListAdapter.setSelectedHabitId(habit.getId());
                tvSelectedHabit.setText(getString(R.string.habit_selected_prefix, habit.getName()));
            }

            @Override
            public void onHabitCheckIn(Habit habit) {
                viewModel.checkInHabit(habit.getId());
                Toast.makeText(requireContext(), R.string.habit_checkin_success, Toast.LENGTH_SHORT).show();
            }
        });

        rvHabits.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        rvHabits.setAdapter(habitListAdapter);
    }

    private void setupHeatmap(View root) {
        RecyclerView rvHeatmap = root.findViewById(R.id.rv_habit_heatmap);
        heatmapAdapter = new HabitHeatmapAdapter();

        GridLayoutManager gridLayoutManager = new GridLayoutManager(requireContext(), 7, RecyclerView.HORIZONTAL, false);
        rvHeatmap.setLayoutManager(gridLayoutManager);
        rvHeatmap.setAdapter(heatmapAdapter);
    }

    private void setupAddHabitButton(View root) {
        View btnAddHabit = root.findViewById(R.id.btn_add_habit);
        btnAddHabit.setOnClickListener(v -> showAddHabitDialog());
    }

    private void showAddHabitDialog() {
        int dp16 = (int) (16 * requireContext().getResources().getDisplayMetrics().density);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp16, dp16, dp16, dp16);

        // Habit name field
        EditText editText = new EditText(requireContext());
        editText.setHint(R.string.habit_add_hint);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        container.addView(editText);

        // Reminder toggle
        CheckBox cbReminder = new CheckBox(requireContext());
        cbReminder.setText("Đặt giờ nhắc hàng ngày");
        cbReminder.setTextSize(14f);
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbParams.topMargin = dp16;
        cbReminder.setLayoutParams(cbParams);
        container.addView(cbReminder);

        // Reminder time label (initially hidden)
        final int[] pickedHour = {7};
        final int[] pickedMinute = {0};
        TextView tvTime = new TextView(requireContext());
        tvTime.setText("⏰ 07:00");
        tvTime.setTextSize(15f);
        tvTime.setVisibility(View.GONE);
        tvTime.setPadding(0, dp16 / 2, 0, 0);
        tvTime.setOnClickListener(v -> {
            new TimePickerDialog(requireContext(), (picker, h, m) -> {
                pickedHour[0] = h;
                pickedMinute[0] = m;
                tvTime.setText(String.format(Locale.getDefault(), "⏰ %02d:%02d", h, m));
            }, pickedHour[0], pickedMinute[0], true).show();
        });
        container.addView(tvTime);

        cbReminder.setOnCheckedChangeListener((btn, checked) -> {
            tvTime.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.habit_add_title)
                .setView(container)
                .setNegativeButton(R.string.add_list_btn_cancel, null)
                .setPositiveButton(R.string.habit_add_confirm, (dialog, which) -> {
                    String name = editText.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.habit_add_empty_error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int reminderHour = cbReminder.isChecked() ? pickedHour[0] : -1;
                    int reminderMinute = cbReminder.isChecked() ? pickedMinute[0] : 0;
                    viewModel.addHabit(name, "ic_health", reminderHour, reminderMinute);
                })
                .show();
    }

    private void observeData() {
        viewModel.getHabits().observe(getViewLifecycleOwner(), habits -> {
            habitListAdapter.submitList(habits);
            viewModel.evaluateNudgeForHabits(habits);

            if (habits == null || habits.isEmpty()) {
                tvSelectedHabit.setText(R.string.habit_no_data_title);
                heatmapAdapter.submitList(java.util.Collections.emptyList());
                return;
            }

            long selectedHabitId = viewModel.getSelectedHabitId();
            Habit selected = null;
            if (selectedHabitId > 0) {
                for (Habit habit : habits) {
                    if (habit.getId() == selectedHabitId) {
                        selected = habit;
                        break;
                    }
                }
            }

            if (selected == null) {
                selected = habits.get(0);
                viewModel.selectHabit(selected.getId());
            }

            habitListAdapter.setSelectedHabitId(selected.getId());
            tvSelectedHabit.setText(getString(R.string.habit_selected_prefix, selected.getName()));
        });

        viewModel.getSelectedHabitLogs().observe(getViewLifecycleOwner(), logs -> {
            List<HabitHeatmapCell> cells = viewModel.buildHeatmapCells(logs);
            heatmapAdapter.submitList(cells);
        });
    }
}
