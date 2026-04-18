package hcmute.edu.vn.tickticktodo.ui.countdown;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
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
import hcmute.edu.vn.tickticktodo.ui.countdown.TimerService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.widget.NumberPicker;

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

    private static final String PREFS_NAME = "countdown_settings";
    private static final String KEY_AMBIENT_SOUND_MODE = "ambient_sound_mode";

    // ── Service binding ──────────────────────────────────────────────────────────
    private TimerService timerService;
    private boolean      isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            TimerService.TimerBinder tb = (TimerService.TimerBinder) binder;
            timerService = tb.getService();
            isBound = true;
            timerService.setAmbientSoundMode(getSavedAmbientSoundMode());
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

        if (timerService.getTimerMode() == TimerService.TimerMode.STOPWATCH) {
            toggleTimerMode.check(R.id.btn_mode_stopwatch);
            vRingOuter.setVisibility(android.view.View.GONE);
            vRingDashed.setVisibility(android.view.View.VISIBLE);
            tvCountdown.setClickable(false);
        } else {
            toggleTimerMode.check(R.id.btn_mode_countdown);
            vRingOuter.setVisibility(android.view.View.VISIBLE);
            vRingDashed.setVisibility(android.view.View.GONE);
            tvCountdown.setClickable(true);
        }

        updateTimerDisplay(millis);
        updateButtonsState(state);

        // Update session count
        int session = timerService.getSessionCount();
        tvSessionCount.setText(getString(R.string.countdown_session) + " " + session);
    }

    private void updateButtonsState(TimerService.TimerState state) {
        if (state == TimerService.TimerState.RUNNING) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            btnStartPause.setText(getString(R.string.countdown_btn_pause));
            tvTimerStatus.setText(getString(R.string.countdown_status_focus));
            btnStop.setEnabled(true);
            setToggleEnabled(false);
            
            // Handle animation for stopwatch
            if (timerService != null && timerService.getTimerMode() == TimerService.TimerMode.STOPWATCH) {
                if (rotationAnimator == null) {
                    rotationAnimator = android.animation.ObjectAnimator.ofFloat(vRingDashed, "rotation", 0f, 360f);
                    rotationAnimator.setDuration(10000);
                    rotationAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    rotationAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
                }
                if (rotationAnimator.isPaused()) {
                    rotationAnimator.resume();
                } else if (!rotationAnimator.isStarted()) {
                    rotationAnimator.start();
                }
            }
        } else if (state == TimerService.TimerState.PAUSED) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            btnStartPause.setText(getString(R.string.countdown_btn_resume));
            tvTimerStatus.setText(getString(R.string.countdown_status_paused));
            btnStop.setEnabled(true);
            setToggleEnabled(false);
            
            if (rotationAnimator != null) {
                rotationAnimator.pause();
            }
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            btnStartPause.setText(getString(R.string.countdown_btn_start));
            tvTimerStatus.setText(getString(R.string.countdown_status_ready));
            btnStop.setEnabled(false);
            setToggleEnabled(true); // Allow toggle only when idle
            
            if (rotationAnimator != null) {
                rotationAnimator.cancel();
            }
            vRingDashed.setRotation(0f);
        }
    }

    private void setToggleEnabled(boolean enabled) {
        for (int i = 0; i < toggleTimerMode.getChildCount(); i++) {
            toggleTimerMode.getChildAt(i).setEnabled(enabled);
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
    private com.google.android.material.button.MaterialButtonToggleGroup toggleTimerMode;
    private android.view.View vRingOuter;
    private android.view.View vRingDashed;
    private android.animation.ObjectAnimator rotationAnimator;
    private ImageButton btnAmbientSound;

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
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        toggleTimerMode = findViewById(R.id.toggle_timer_mode);
        vRingOuter      = findViewById(R.id.v_ring_outer);
        vRingDashed     = findViewById(R.id.v_ring_dashed);
        btnAmbientSound = findViewById(R.id.btn_ambient_sound);

        ImageButton btnBack = findViewById(R.id.btn_countdown_back);
        btnBack.setOnClickListener(v -> finish());
        if (btnAmbientSound != null) {
            btnAmbientSound.setOnClickListener(v -> showAmbientSoundDialog());
        }

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
        toggleTimerMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (timerService != null && timerService.getTimerState() != TimerService.TimerState.IDLE) {
                Toast.makeText(this, "Vui lòng dừng trước khi đổi chế độ", Toast.LENGTH_SHORT).show();
                // Revert UI change visually
                if (timerService.getTimerMode() == TimerService.TimerMode.COUNTDOWN) {
                    group.check(R.id.btn_mode_countdown);
                } else {
                    group.check(R.id.btn_mode_stopwatch);
                }
                return;
            }
            if (checkedId == R.id.btn_mode_countdown) {
                if (timerService != null) timerService.setTimerMode(TimerService.TimerMode.COUNTDOWN);
                vRingOuter.setVisibility(android.view.View.VISIBLE);
                vRingDashed.setVisibility(android.view.View.GONE);
                tvCountdown.setClickable(true);
            } else if (checkedId == R.id.btn_mode_stopwatch) {
                if (timerService != null) timerService.setTimerMode(TimerService.TimerMode.STOPWATCH);
                vRingOuter.setVisibility(android.view.View.GONE);
                vRingDashed.setVisibility(android.view.View.VISIBLE);
                tvCountdown.setClickable(false); // Do not let user pick time for stopwatch
            }
        });

        tvCountdown.setOnClickListener(v -> {
            if (!isBound || timerService.getTimerState() != TimerService.TimerState.IDLE) {
                if (timerService != null && timerService.getTimerState() != TimerService.TimerState.IDLE) {
                    Toast.makeText(this, "Chỉ có thể đổi thời gian khi đang dừng", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            showTimePickerDialog();
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

        Toast.makeText(this, getString(R.string.countdown_toast_done), Toast.LENGTH_LONG).show();
    }

    private void showTimePickerDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_time_picker, null);
        dialog.setContentView(view);
        
        NumberPicker npMinutes = view.findViewById(R.id.np_minutes);
        npMinutes.setMinValue(1);
        npMinutes.setMaxValue(180);
        if (timerService != null) {
            npMinutes.setValue(timerService.getCurrentModeMins());
        } else {
            npMinutes.setValue(25);
        }

        Button btnSave = view.findViewById(R.id.btn_save_time);
        btnSave.setOnClickListener(v -> {
            int selectedMinutes = npMinutes.getValue();
            if (timerService != null) {
                timerService.setMode(selectedMinutes);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showAmbientSoundDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_ambient_sound, null);
        dialog.setContentView(view);

        RadioGroup radioGroup = view.findViewById(R.id.rg_ambient_sound);
        int currentMode = isBound && timerService != null
                ? timerService.getAmbientSoundMode()
                : getSavedAmbientSoundMode();
        radioGroup.check(toRadioButtonId(currentMode));

        Button btnApply = view.findViewById(R.id.btn_apply_sound);
        btnApply.setOnClickListener(v -> {
            int selectedMode = resolveSelectedSoundMode(radioGroup.getCheckedRadioButtonId());
            saveAmbientSoundMode(selectedMode);

            if (isBound && timerService != null) {
                timerService.setAmbientSoundMode(selectedMode);
                if (selectedMode != TimerService.AMBIENT_SOUND_NONE
                        && !timerService.hasAmbientSoundResource(selectedMode)) {
                    Toast.makeText(this, R.string.countdown_sound_missing_files, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.countdown_sound_applied, Toast.LENGTH_SHORT).show();
                }
            }

            dialog.dismiss();
        });

        dialog.show();
    }

    private int resolveSelectedSoundMode(int checkedId) {
        if (checkedId == R.id.rb_sound_rain) {
            return TimerService.AMBIENT_SOUND_RAIN;
        }
        if (checkedId == R.id.rb_sound_cafe) {
            return TimerService.AMBIENT_SOUND_CAFE;
        }
        if (checkedId == R.id.rb_sound_lofi) {
            return TimerService.AMBIENT_SOUND_LOFI;
        }
        return TimerService.AMBIENT_SOUND_NONE;
    }

    private int toRadioButtonId(int mode) {
        switch (mode) {
            case TimerService.AMBIENT_SOUND_RAIN:
                return R.id.rb_sound_rain;
            case TimerService.AMBIENT_SOUND_CAFE:
                return R.id.rb_sound_cafe;
            case TimerService.AMBIENT_SOUND_LOFI:
                return R.id.rb_sound_lofi;
            case TimerService.AMBIENT_SOUND_NONE:
            default:
                return R.id.rb_sound_none;
        }
    }

    private int getSavedAmbientSoundMode() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_AMBIENT_SOUND_MODE, TimerService.AMBIENT_SOUND_NONE);
    }

    private void saveAmbientSoundMode(int mode) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_AMBIENT_SOUND_MODE, mode).apply();
    }
}
