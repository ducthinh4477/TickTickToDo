package hcmute.edu.vn.tickticktodo.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Locale;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.service.TimerService;

/**
 * CountdownActivity — Giao diện Pomodoro Timer.
 *
 * Kiến trúc giao tiếp với TimerService:
 *   - bindService()  : lấy Binder để gọi start/pause/stop/setMode
 *   - LocalBroadcast : nhận ACTION_TICK (mỗi giây) và ACTION_FINISH (khi hết giờ)
 *
 * Activity KHÔNG còn chứa CountDownTimer — toàn bộ logic nằm trong TimerService.
 */
public class CountdownActivity extends BaseActivity {

    // Timer modes (minutes)
    private static final int MODE_POMODORO    = 25;
    private static final int MODE_SHORT_BREAK = 5;
    private static final int MODE_LONG_BREAK  = 15;

    // ── Service binding ──────────────────────────────────────────────────────────
    private TimerService timerService;
    private boolean      isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            TimerService.TimerBinder tb = (TimerService.TimerBinder) binder;
            timerService = tb.getService();
            isBound = true;
            // Sync lại UI với trạng thái hiện tại của Service
            syncUiWithService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            timerService = null;
        }
    };

    private void syncUiWithService() {
        if (timerService == null) return;

        long millis = timerService.getMillisRemaining();
        int mode = timerService.getCurrentModeMins();
        TimerService.TimerState state = timerService.getTimerState();

        updateTimerDisplay(millis);
        updateButtonsState(state);

        // Update tabs selection based on mode
        if (mode == MODE_POMODORO) selectTab(tabPomodoro);
        else if (mode == MODE_SHORT_BREAK) selectTab(tabShortBreak);
        else if (mode == MODE_LONG_BREAK) selectTab(tabLongBreak);

        // Update session count
        int session = timerService.getSessionCount();
        tvSessionCount.setText(getString(R.string.countdown_session) + " " + session);
    }

    private void updateButtonsState(TimerService.TimerState state) {
        if (state == TimerService.TimerState.RUNNING) {
            btnStartPause.setText(getString(R.string.countdown_btn_pause));
            tvTimerStatus.setText(getString(R.string.countdown_status_focus));
        } else if (state == TimerService.TimerState.PAUSED) {
            btnStartPause.setText(getString(R.string.countdown_btn_resume));
            tvTimerStatus.setText(getString(R.string.countdown_status_paused));
        } else {
            btnStartPause.setText(getString(R.string.countdown_btn_start));
            tvTimerStatus.setText(getString(R.string.countdown_status_ready));
        }
    }

    // ── LocalBroadcast receiver ──────────────────────────────────────────────────
    private final BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TimerService.ACTION_TICK.equals(intent.getAction())) {
                long millis = intent.getLongExtra(TimerService.EXTRA_MILLIS_REMAINING, 0);
                updateTimerDisplay(millis);

            } else if (TimerService.ACTION_FINISH.equals(intent.getAction())) {
                int modeMins = intent.getIntExtra(TimerService.EXTRA_MODE_MINS, 25);
                onTimerFinished(modeMins);
            }
        }
    };

    // ── Views ────────────────────────────────────────────────────────────────────
    private TextView tvCountdown;
    private TextView tvTimerStatus;
    private TextView tvSessionCount;
    private Button   btnStartPause;
    private Button   btnStop;
    private TextView tabPomodoro;
    private TextView tabShortBreak;
    private TextView tabLongBreak;

    // ── Factory ──────────────────────────────────────────────────────────────────
    public static Intent newIntent(Context context) {
        return new Intent(context, CountdownActivity.class);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_countdown);

        initViews();
        applyWindowInsets();
        setupListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind tới TimerService (tạo mới Service nếu chưa có)
        Intent serviceIntent = new Intent(this, TimerService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // Đăng ký nhận broadcast từ Service
        IntentFilter filter = new IntentFilter();
        filter.addAction(TimerService.ACTION_TICK);
        filter.addAction(TimerService.ACTION_FINISH);
        LocalBroadcastManager.getInstance(this).registerReceiver(timerReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Huỷ đăng ký broadcast
        LocalBroadcastManager.getInstance(this).unregisterReceiver(timerReceiver);

        // Unbind khỏi Service
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    // ── View binding ─────────────────────────────────────────────────────────────

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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Tạm dừng nếu đang chạy khi nhấn Back
                if (isBound && timerService != null
                        && timerService.getTimerState() == TimerService.TimerState.RUNNING) {
                    timerService.pauseTimer();
                }
                finish();
            }
        });
    }

    /** Áp dụng paddingTop = status bar height cho header, tránh tai thỏ/notch che */
    private void applyWindowInsets() {
        RelativeLayout header = findViewById(R.id.countdown_header);
        ViewCompat.setOnApplyWindowInsetsListener(header, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            float density = getResources().getDisplayMetrics().density;
            view.setPadding(
                    view.getPaddingLeft(),
                    insets.top + (int) (8 * density),
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            );
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.height = (int) (80 * density) + insets.top;
            view.setLayoutParams(lp);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    // ── Listeners ────────────────────────────────────────────────────────────────

    private void setupListeners() {
        tabPomodoro.setOnClickListener(v -> {
            if (!isBound || timerService.getTimerState() != TimerService.TimerState.IDLE) return;
            selectTab(tabPomodoro);
            timerService.setMode(MODE_POMODORO);
        });
        tabShortBreak.setOnClickListener(v -> {
            if (!isBound || timerService.getTimerState() != TimerService.TimerState.IDLE) return;
            selectTab(tabShortBreak);
            timerService.setMode(MODE_SHORT_BREAK);
        });
        tabLongBreak.setOnClickListener(v -> {
            if (!isBound || timerService.getTimerState() != TimerService.TimerState.IDLE) return;
            selectTab(tabLongBreak);
            timerService.setMode(MODE_LONG_BREAK);
        });

        btnStartPause.setOnClickListener(v -> {
            if (!isBound) return;
            TimerService.TimerState state = timerService.getTimerState();

            if (state == TimerService.TimerState.RUNNING) {
                timerService.pauseTimer();
            } else if (state == TimerService.TimerState.PAUSED) {
                timerService.resumeTimer();
            } else {
                // START
                // Quan trọng: Gọi startService để đảm bảo Service sống độc lập
                Intent intent = new Intent(this, TimerService.class);
                intent.setAction(TimerService.ACTION_START);
                ContextCompat.startForegroundService(this, intent);

                // Binder gọi logic (thực ra onStartCommand cũng gọi, nhưng gọi trực tiếp qua binder nhanh hơn cập nhật UI)
                timerService.startTimer();
            }
            // UI sẽ tự cập nhật qua broadcast hoặc logic sync
            updateButtonsState(timerService.getTimerState());
        });

        btnStop.setOnClickListener(v -> {
            if (!isBound) return;
            timerService.stopTimer();
            // UI update handled by broadcast tick (reset to full time)
            updateButtonsState(TimerService.TimerState.IDLE);
        });
    }

    // ── Callbacks từ BroadcastReceiver ───────────────────────────────────────────

    private void updateTimerDisplay(long millis) {
        long minutes = millis / 1000 / 60;
        long seconds = (millis / 1000) % 60;
        tvCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void onTimerFinished(int modeMins) {
        btnStartPause.setText(R.string.countdown_btn_start);
        btnStop.setEnabled(false);
        tvTimerStatus.setText(R.string.countdown_status_done);

        if (isBound && timerService != null) {
            tvSessionCount.setText(String.valueOf(timerService.getSessionCount()));
        }

        Toast.makeText(this,
                modeMins == MODE_POMODORO
                        ? getString(R.string.countdown_toast_done)
                        : getString(R.string.countdown_toast_break_done),
                Toast.LENGTH_LONG).show();
    }

    // ── Tab UI helper ─────────────────────────────────────────────────────────────

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
}
