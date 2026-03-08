package hcmute.edu.vn.tickticktodo.ui;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;

public class CountdownActivity extends BaseActivity {

    // Timer modes (minutes)
    private static final int MODE_POMODORO    = 25;
    private static final int MODE_SHORT_BREAK = 5;
    private static final int MODE_LONG_BREAK  = 15;

    // State
    private enum TimerState { IDLE, RUNNING, PAUSED }

    private int currentModeMins = MODE_POMODORO;
    private long totalMillis;
    private long millisRemaining;
    private TimerState timerState = TimerState.IDLE;
    private int sessionCount = 1;

    private CountDownTimer countDownTimer;
    private MediaPlayer mediaPlayer;

    // Views
    private TextView tvCountdown;
    private TextView tvTimerStatus;
    private TextView tvSessionCount;
    private Button btnStartPause;
    private Button btnStop;
    private TextView tabPomodoro;
    private TextView tabShortBreak;
    private TextView tabLongBreak;

    public static Intent newIntent(Context context) {
        return new Intent(context, CountdownActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_countdown);

        initViews();
        applyWindowInsets();
        setupListeners();
        applyMode(MODE_POMODORO);
    }

    /** Áp dụng paddingTop = status bar height cho header, tránh bị tai thỏ/notch che */
    private void applyWindowInsets() {
        RelativeLayout header = findViewById(R.id.countdown_header);
        ViewCompat.setOnApplyWindowInsetsListener(header, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            float density = getResources().getDisplayMetrics().density;
            // paddingTop = status bar + 8dp khoảng thở
            view.setPadding(
                    view.getPaddingLeft(),
                    insets.top + (int) (8 * density),
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            );
            // height = 80dp base + status bar height
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.height = (int) (80 * density) + insets.top;
            view.setLayoutParams(lp);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initViews() {
        tvCountdown    = findViewById(R.id.tv_countdown);
        tvTimerStatus  = findViewById(R.id.tv_timer_status);
        tvSessionCount = findViewById(R.id.tv_session_count);
        btnStartPause  = findViewById(R.id.btn_start_pause);
        btnStop        = findViewById(R.id.btn_stop);
        tabPomodoro    = findViewById(R.id.tab_pomodoro);
        tabShortBreak  = findViewById(R.id.tab_short_break);
        tabLongBreak   = findViewById(R.id.tab_long_break);

        ImageButton btnBack = findViewById(R.id.btn_countdown_back);
        btnBack.setOnClickListener(v -> finish());

        // Handle back press: pause timer instead of cancelling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (timerState == TimerState.RUNNING) {
                    pauseTimer();
                }
                finish();
            }
        });
    }

    private void setupListeners() {
        tabPomodoro.setOnClickListener(v -> {
            if (timerState == TimerState.IDLE) {
                selectTab(tabPomodoro);
                applyMode(MODE_POMODORO);
            }
        });
        tabShortBreak.setOnClickListener(v -> {
            if (timerState == TimerState.IDLE) {
                selectTab(tabShortBreak);
                applyMode(MODE_SHORT_BREAK);
            }
        });
        tabLongBreak.setOnClickListener(v -> {
            if (timerState == TimerState.IDLE) {
                selectTab(tabLongBreak);
                applyMode(MODE_LONG_BREAK);
            }
        });

        btnStartPause.setOnClickListener(v -> {
            switch (timerState) {
                case IDLE:
                    startTimer();
                    break;
                case RUNNING:
                    pauseTimer();
                    break;
                case PAUSED:
                    resumeTimer();
                    break;
            }
        });

        btnStop.setOnClickListener(v -> stopTimer());
    }

    // ──────────────────────────────────────────────
    // Mode helpers
    // ──────────────────────────────────────────────

    private void applyMode(int minutes) {
        currentModeMins = minutes;
        totalMillis = minutes * 60 * 1000L;
        millisRemaining = totalMillis;
        updateTimerDisplay(millisRemaining);
        tvTimerStatus.setText(R.string.countdown_status_ready);
    }

    private void selectTab(TextView selected) {
        int white    = ContextCompat.getColor(this, android.R.color.white);
        int dimWhite = 0xAAFFFFFF;

        tabPomodoro.setBackground(null);
        tabShortBreak.setBackground(null);
        tabLongBreak.setBackground(null);
        tabPomodoro.setTextColor(dimWhite);
        tabShortBreak.setTextColor(dimWhite);
        tabLongBreak.setTextColor(dimWhite);

        selected.setBackgroundResource(R.drawable.bg_tab_selected);
        selected.setTextColor(white);
    }

    // ──────────────────────────────────────────────
    // Timer control
    // ──────────────────────────────────────────────

    private void startTimer() {
        timerState = TimerState.RUNNING;
        btnStartPause.setText(R.string.countdown_btn_pause);
        btnStop.setEnabled(true);
        tvTimerStatus.setText(R.string.countdown_status_focus);
        runCountdown(millisRemaining);
    }

    private void pauseTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerState = TimerState.PAUSED;
        btnStartPause.setText(R.string.countdown_btn_resume);
        tvTimerStatus.setText(R.string.countdown_status_paused);
    }

    private void resumeTimer() {
        timerState = TimerState.RUNNING;
        btnStartPause.setText(R.string.countdown_btn_pause);
        tvTimerStatus.setText(R.string.countdown_status_focus);
        runCountdown(millisRemaining);
    }

    private void stopTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerState = TimerState.IDLE;
        millisRemaining = totalMillis;
        btnStartPause.setText(R.string.countdown_btn_start);
        btnStop.setEnabled(false);
        tvTimerStatus.setText(R.string.countdown_status_ready);
        updateTimerDisplay(millisRemaining);
    }

    private void runCountdown(long millis) {
        countDownTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                millisRemaining = millisUntilFinished;
                updateTimerDisplay(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                millisRemaining = 0;
                updateTimerDisplay(0);
                onTimerFinished();
            }
        }.start();
    }

    private void onTimerFinished() {
        timerState = TimerState.IDLE;
        btnStartPause.setText(R.string.countdown_btn_start);
        btnStop.setEnabled(false);
        tvTimerStatus.setText(R.string.countdown_status_done);

        // Increment session counter (only for Pomodoro)
        if (currentModeMins == MODE_POMODORO) {
            sessionCount++;
            tvSessionCount.setText(String.valueOf(sessionCount));
        }

        // Reset for next use
        millisRemaining = totalMillis;

        playAlarmSound();

        Toast.makeText(this,
                currentModeMins == MODE_POMODORO
                        ? getString(R.string.countdown_toast_done)
                        : getString(R.string.countdown_toast_break_done),
                Toast.LENGTH_LONG).show();
    }

    // ──────────────────────────────────────────────
    // UI helpers
    // ──────────────────────────────────────────────

    private void updateTimerDisplay(long millis) {
        long minutes = millis / 1000 / 60;
        long seconds = (millis / 1000) % 60;
        tvCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    // ──────────────────────────────────────────────
    // Alarm sound
    // ──────────────────────────────────────────────

    private void playAlarmSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Auto-stop after 3 seconds so it doesn't keep ringing
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            }, 3000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ──────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}

