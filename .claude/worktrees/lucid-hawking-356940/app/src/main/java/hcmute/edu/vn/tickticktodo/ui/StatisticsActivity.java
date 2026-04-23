package hcmute.edu.vn.doinbot.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import hcmute.edu.vn.doinbot.BaseActivity;
import hcmute.edu.vn.doinbot.R;
import hcmute.edu.vn.doinbot.helper.UserStatsManager;
import hcmute.edu.vn.doinbot.ui.task.TaskViewModel;

/**
 * Màn hình Thống kê (Statistics) — hiển thị tổng quan về tiến độ hoàn thành task.
 *
 * Bao gồm 3 thẻ:
 *   1. Tổng số task đã hoàn thành (Total Completed)
 *   2. Tỷ lệ hoàn thành hôm nay (Completion Rate Today)
 *   3. Số task hoàn thành trong 7 ngày qua
 *
 * Cách mở:
 *   startActivity(StatisticsActivity.newIntent(context));
 */
public class StatisticsActivity extends BaseActivity {

    private TaskViewModel taskViewModel;

    // Card 1
    private TextView tvTotalCompleted;

    // Card 2
    private TextView tvCompletionRate;
    private ProgressBar progressCompletionRate;
    private TextView tvCompletionDetail;

    // Card 3
    private TextView tvCompleted7Days;

    // Card 4: Gamification
    private TextView tvGamificationLevel;
    private TextView tvGamificationXp;
    private TextView tvGamificationStreak;
    private ProgressBar progressGamificationXp;

    // Lưu giá trị để tính Completion Rate
    private int completedTodayCount = 0;
    private int totalTodayCount = 0;

    // ─── Intent factory ──────────────────────────────────────────────────────────

    public static Intent newIntent(Context context) {
        return new Intent(context, StatisticsActivity.class);
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        initViews();
        setupToolbar();
        setupViewModel();
        updateGamificationUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGamificationUi();
    }

    // ─── View binding ────────────────────────────────────────────────────────────

    private void initViews() {
        tvTotalCompleted     = findViewById(R.id.tv_total_completed);
        tvCompletionRate     = findViewById(R.id.tv_completion_rate);
        progressCompletionRate = findViewById(R.id.progress_completion_rate);
        tvCompletionDetail   = findViewById(R.id.tv_completion_detail);
        tvCompleted7Days     = findViewById(R.id.tv_completed_7_days);

        tvGamificationLevel = findViewById(R.id.tv_gamification_level);
        tvGamificationXp = findViewById(R.id.tv_gamification_xp);
        tvGamificationStreak = findViewById(R.id.tv_gamification_streak);
        progressGamificationXp = findViewById(R.id.progress_gamification_xp);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar_statistics);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ─── ViewModel / LiveData ────────────────────────────────────────────────────

    private void setupViewModel() {
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        // Card 1: Tổng số task đã hoàn thành
        taskViewModel.countTotalCompleted().observe(this, count -> {
            int total = count != null ? count : 0;
            tvTotalCompleted.setText(String.valueOf(total));
        });

        // Card 2: Tỷ lệ hoàn thành hôm nay
        taskViewModel.countCompletedToday().observe(this, count -> {
            completedTodayCount = count != null ? count : 0;
            updateCompletionRate();
        });

        taskViewModel.countTotalTasksToday().observe(this, count -> {
            totalTodayCount = count != null ? count : 0;
            updateCompletionRate();
        });

        // Card 3: Hoàn thành trong 7 ngày qua
        taskViewModel.countCompletedLast7Days().observe(this, count -> {
            int total = count != null ? count : 0;
            tvCompleted7Days.setText(String.valueOf(total));
        });
    }

    /**
     * Tính và hiển thị Completion Rate = (completedToday / totalToday) * 100%.
     */
    private void updateCompletionRate() {
        String tasksLabel = getString(R.string.statistics_tasks_label);
        if (totalTodayCount == 0) {
            tvCompletionRate.setText(getString(R.string.statistics_no_tasks_today));
            tvCompletionRate.setTextSize(16);
            progressCompletionRate.setProgress(0);
            tvCompletionDetail.setText(getString(R.string.statistics_completion_detail, 0, 0, tasksLabel));
        } else {
            int rate = (int) ((completedTodayCount * 100.0f) / totalTodayCount);
            tvCompletionRate.setTextSize(28);
            tvCompletionRate.setText(String.format(getString(R.string.statistics_percent_format), rate));
            progressCompletionRate.setProgress(rate);
            tvCompletionDetail.setText(getString(R.string.statistics_completion_detail,
                    completedTodayCount, totalTodayCount, tasksLabel));
        }
    }

    private void updateGamificationUi() {
        UserStatsManager.Stats stats = UserStatsManager.getInstance(this).getStats();
        tvGamificationLevel.setText("Lv. " + stats.level);
        progressGamificationXp.setProgress(stats.getXpInCurrentLevel());
        tvGamificationXp.setText(getString(R.string.statistics_xp_progress, stats.getXpInCurrentLevel()));
        tvGamificationStreak.setText(getString(R.string.statistics_streak_days, stats.currentStreak));
    }
}


