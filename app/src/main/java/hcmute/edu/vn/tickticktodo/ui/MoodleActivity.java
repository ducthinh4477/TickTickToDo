package hcmute.edu.vn.tickticktodo.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.adapter.TaskAdapter;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

public class MoodleActivity extends BaseActivity {

    private TaskViewModel taskViewModel;
    private TaskAdapter adapter;
    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private TextView tvNewTaskWarning;
    private Button btnConnectMoodle;
    private ImageView btnSettings;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_moodle);

        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerViewMoodle);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        tvNewTaskWarning = findViewById(R.id.tvNewTaskWarning);
        btnConnectMoodle = findViewById(R.id.btnConnectMoodle);
        btnSettings = findViewById(R.id.btnSettings);

        toolbar.setNavigationOnClickListener(v -> finish());

        // Sử dụng lại TaskAdapter để giữ tính nhất quán
        adapter = new TaskAdapter(
            (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
            task -> startActivity(TaskDetailActivity.newIntent(this, task.getId()))
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        // 1. Observe hiển thị danh sách bài tập sắp đến hạn
        taskViewModel.getUpcomingMoodleTasks(System.currentTimeMillis()).observe(this, tasks -> {
            if (tasks != null && !tasks.isEmpty()) {
                adapter.submitList(tasks);
                emptyStateLayout.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                adapter.submitList(new ArrayList<>());
                emptyStateLayout.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
            checkMoodleConfig(); // Check nếu chưa config link Moodle
        });

        // 2. Observe đếm số lượng bài mới để hiển thị "Cảnh báo bài mới"
        taskViewModel.getUnreadMoodleTasksCount().observe(this, count -> {
            if (count > 0) {
                tvNewTaskWarning.setVisibility(View.VISIBLE);
                tvNewTaskWarning.setText("Bạn có " + count + " bài tập Moodle chưa hoàn thành!");
            } else {
                tvNewTaskWarning.setVisibility(View.GONE);
            }
        });

        // Nút nhấn vào Setting / Login trường
        btnConnectMoodle.setOnClickListener(v -> launchSchoolLogin());
        btnSettings.setOnClickListener(v -> launchSchoolLogin());
    }

    private void checkMoodleConfig() {
        SharedPreferences settings = getSharedPreferences(SchoolLoginActivity.PREFS_NAME, 0);
        String iCalUrl = settings.getString(SchoolLoginActivity.KEY_ICAL_URL, null);
        if (iCalUrl == null || iCalUrl.isEmpty()) {
            btnConnectMoodle.setText("Đăng nhập Moodle");
            btnConnectMoodle.setVisibility(View.VISIBLE);
        } else {
            btnConnectMoodle.setText("Đồng bộ lại Moodle");
        }
    }

    private void launchSchoolLogin() {
        startActivity(new Intent(this, SchoolLoginActivity.class));
    }
}