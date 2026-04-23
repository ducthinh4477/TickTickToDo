package hcmute.edu.vn.doinbot.ui.countdown;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.doinbot.BaseActivity;
import hcmute.edu.vn.doinbot.R;

/**
 * CountdownActivity — Pomodoro & Stopwatch timer UI.
 *
 * Button logic:
 *   Countdown IDLE     → single "Bắt đầu" button
 *   Countdown RUNNING  → "Đặt lại"  +  "Tạm dừng"
 *   Countdown PAUSED   → "Đặt lại"  +  "Tiếp tục"
 *
 *   Stopwatch IDLE     → single "Bắt đầu" button
 *   Stopwatch RUNNING  → "Cờ"  |  "Dừng"  |  "Đặt lại" (dimmed)
 *   Stopwatch STOPPED  → "Cờ" (dimmed)  |  "Bắt đầu"  |  "Đặt lại" (active)
 */
public class CountdownActivity extends BaseActivity {

    private static final String PREFS_NAME           = "countdown_settings";
    private static final String KEY_AMBIENT_SOUND    = "ambient_sound_mode";

    // ── Service binding ──────────────────────────────────────────────────────
    private TimerService timerService;
    private boolean      isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            timerService = ((TimerService.TimerBinder) binder).getService();
            isBound = true;
            timerService.setAmbientSoundMode(getSavedAmbientSoundMode());
            syncUiWithService();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            timerService = null;
        }
    };

    // ── Broadcast receiver ───────────────────────────────────────────────────
    private final BroadcastReceiver timerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TimerService.ACTION_TICK.equals(intent.getAction())) {
                long millis = intent.getLongExtra(TimerService.EXTRA_MILLIS_REMAINING, 0);
                updateTimerDisplay(millis);
            } else if (TimerService.ACTION_FINISH.equals(intent.getAction())) {
                onTimerFinished();
            }
        }
    };

    // ── Views — common ───────────────────────────────────────────────────────
    private TextView                     tvCountdown;
    private TextView                     tvTimerStatus;
    private CircularProgressIndicator    progressCountdown;
    private StopwatchBarsView            swBarsView;
    private MaterialButtonToggleGroup    toggleTimerMode;
    private ImageButton                  btnAmbientSound;
    private TextView                     tvAmbientIndicator;

    // ── Views — countdown buttons ────────────────────────────────────────────
    private FrameLayout     containerCountdownButtons;
    private MaterialButton  btnCountdownStart;
    private LinearLayout    llCountdownDual;
    private MaterialButton  btnCountdownReset;
    private MaterialButton  btnCountdownPauseResume;

    // ── Views — stopwatch buttons ────────────────────────────────────────────
    private FrameLayout     containerSwButtons;
    private MaterialButton  btnSwStart;
    private LinearLayout    llSwActive;
    private MaterialButton  btnSwFlag;
    private MaterialButton  btnSwStopResume;
    private MaterialButton  btnSwReset;

    // ── Views — lap list ─────────────────────────────────────────────────────
    private RecyclerView rvLaps;
    private LapAdapter   lapAdapter;
    private final List<Long> lapTimes = new ArrayList<>();

    // ── Factory ──────────────────────────────────────────────────────────────
    public static Intent newIntent(Context context) {
        return new Intent(context, CountdownActivity.class);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_countdown);

        initViews();
        applyWindowInsets();
        setupListeners();
        setupLapList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent svc = new Intent(this, TimerService.class);
        bindService(svc, serviceConnection, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TimerService.ACTION_TICK);
        filter.addAction(TimerService.ACTION_FINISH);
        LocalBroadcastManager.getInstance(this).registerReceiver(timerReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(timerReceiver);
        if (isBound) { unbindService(serviceConnection); isBound = false; }
    }

    // ── View binding ─────────────────────────────────────────────────────────
    private void initViews() {
        tvCountdown            = findViewById(R.id.tv_countdown);
        tvTimerStatus          = findViewById(R.id.tv_timer_status);
        progressCountdown      = findViewById(R.id.progress_countdown);
        swBarsView             = findViewById(R.id.view_stopwatch_bars);
        toggleTimerMode        = findViewById(R.id.toggle_timer_mode);
        btnAmbientSound        = findViewById(R.id.btn_ambient_sound);
        tvAmbientIndicator     = findViewById(R.id.tv_ambient_indicator);

        containerCountdownButtons = findViewById(R.id.container_countdown_buttons);
        btnCountdownStart         = findViewById(R.id.btn_countdown_start);
        llCountdownDual           = findViewById(R.id.ll_countdown_dual);
        btnCountdownReset         = findViewById(R.id.btn_countdown_reset);
        btnCountdownPauseResume   = findViewById(R.id.btn_countdown_pause_resume);

        containerSwButtons  = findViewById(R.id.container_sw_buttons);
        btnSwStart          = findViewById(R.id.btn_sw_start);
        llSwActive          = findViewById(R.id.ll_sw_active);
        btnSwFlag           = findViewById(R.id.btn_sw_flag);
        btnSwStopResume     = findViewById(R.id.btn_sw_stop_resume);
        btnSwReset          = findViewById(R.id.btn_sw_reset);

        rvLaps = findViewById(R.id.rv_laps);

        // Back button
        ImageButton btnBack = findViewById(R.id.btn_countdown_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Ambient sound
        if (btnAmbientSound != null)
            btnAmbientSound.setOnClickListener(v -> showAmbientSoundDialog());

        // Back-press: if running, pause first
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (isBound && timerService != null
                        && timerService.getTimerState() == TimerService.TimerState.RUNNING) {
                    timerService.pauseTimer();
                }
                finish();
            }
        });
    }

    /** Pad header for status-bar notch. */
    private void applyWindowInsets() {
        RelativeLayout header = findViewById(R.id.countdown_header);
        if (header == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(header, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            float density = getResources().getDisplayMetrics().density;
            view.setPadding(
                    view.getPaddingLeft(),
                    insets.top + (int) (8 * density),
                    view.getPaddingRight(),
                    view.getPaddingBottom());
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.height = (int) (80 * density) + insets.top;
            view.setLayoutParams(lp);

            // Bottom padding for the button containers (avoid nav-bar overlap)
            int bottomPad = insets.bottom + (int) (16 * density);
            containerCountdownButtons.setPadding(0, 0, 0, bottomPad);
            containerSwButtons.setPadding(0, 0, 0, bottomPad);

            return WindowInsetsCompat.CONSUMED;
        });
    }

    // ── Lap list setup ───────────────────────────────────────────────────────
    private void setupLapList() {
        lapAdapter = new LapAdapter(lapTimes);
        rvLaps.setLayoutManager(new LinearLayoutManager(this));
        rvLaps.setAdapter(lapAdapter);
    }

    // ── Listeners ────────────────────────────────────────────────────────────
    private void setupListeners() {

        // ── Mode toggle ──────────────────────────────────────────────────────
        toggleTimerMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (timerService != null
                    && timerService.getTimerState() != TimerService.TimerState.IDLE) {
                Toast.makeText(this, "Vui lòng dừng trước khi đổi chế độ",
                        Toast.LENGTH_SHORT).show();
                // Revert
                group.check(timerService.getTimerMode() == TimerService.TimerMode.COUNTDOWN
                        ? R.id.btn_mode_countdown : R.id.btn_mode_stopwatch);
                return;
            }
            if (checkedId == R.id.btn_mode_countdown) {
                if (timerService != null) timerService.setTimerMode(TimerService.TimerMode.COUNTDOWN);
                switchToCountdownMode();
            } else {
                if (timerService != null) timerService.setTimerMode(TimerService.TimerMode.STOPWATCH);
                switchToStopwatchMode();
            }
        });

        // ── Time picker (tap timer digits in countdown idle) ─────────────────
        tvCountdown.setOnClickListener(v -> {
            if (!isBound || timerService == null) return;
            if (timerService.getTimerMode() != TimerService.TimerMode.COUNTDOWN) return;
            if (timerService.getTimerState() != TimerService.TimerState.IDLE) {
                Toast.makeText(this, "Chỉ có thể đổi thời gian khi đang dừng",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            showTimePickerDialog();
        });

        // ── Countdown buttons ────────────────────────────────────────────────
        btnCountdownStart.setOnClickListener(v -> {
            if (!isBound) return;
            startForegroundTimer();
            timerService.startTimer();
            showCountdownRunning();
        });

        btnCountdownReset.setOnClickListener(v -> {
            if (!isBound) return;
            timerService.stopTimer();
            showCountdownIdle();
        });

        btnCountdownPauseResume.setOnClickListener(v -> {
            if (!isBound) return;
            TimerService.TimerState state = timerService.getTimerState();
            if (state == TimerService.TimerState.RUNNING) {
                timerService.pauseTimer();
                showCountdownPaused();
            } else if (state == TimerService.TimerState.PAUSED) {
                timerService.resumeTimer();
                showCountdownRunning();
            }
        });

        // ── Stopwatch buttons ────────────────────────────────────────────────
        btnSwStart.setOnClickListener(v -> {
            if (!isBound) return;
            startForegroundTimer();
            timerService.startTimer();
            showStopwatchRunning();
        });

        btnSwStopResume.setOnClickListener(v -> {
            if (!isBound) return;
            TimerService.TimerState state = timerService.getTimerState();
            if (state == TimerService.TimerState.RUNNING) {
                timerService.pauseTimer();
                showStopwatchStopped();
            } else if (state == TimerService.TimerState.PAUSED) {
                timerService.resumeTimer();
                showStopwatchRunning();
            }
        });

        btnSwFlag.setOnClickListener(v -> {
            if (!isBound || timerService == null) return;
            long current = timerService.getMillisRemaining(); // elapsed millis in stopwatch
            lapTimes.add(0, current); // newest first
            lapAdapter.notifyItemInserted(0);
            rvLaps.scrollToPosition(0);
        });

        btnSwReset.setOnClickListener(v -> {
            if (!isBound) return;
            timerService.stopTimer();
            lapTimes.clear();
            lapAdapter.notifyDataSetChanged();
            swBarsView.reset();
            showStopwatchIdle();
        });
    }

    // ── UI State helpers — countdown ─────────────────────────────────────────

    private void switchToCountdownMode() {
        containerCountdownButtons.setVisibility(View.VISIBLE);
        containerSwButtons.setVisibility(View.GONE);
        progressCountdown.setVisibility(View.VISIBLE);
        swBarsView.setVisibility(View.GONE);
        rvLaps.setVisibility(View.GONE);
        tvCountdown.setClickable(true);
        showCountdownIdle();
    }

    private void showCountdownIdle() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        btnCountdownStart.setVisibility(View.VISIBLE);
        llCountdownDual.setVisibility(View.GONE);
        progressCountdown.setProgressCompat(1000, false);
        setToggleEnabled(true);
        updateStatusText(getString(R.string.countdown_status_ready));
    }

    private void showCountdownRunning() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        btnCountdownStart.setVisibility(View.GONE);
        llCountdownDual.setVisibility(View.VISIBLE);
        btnCountdownPauseResume.setText(R.string.countdown_btn_pause);
        btnCountdownPauseResume.setIconResource(R.drawable.ic_pause);
        setToggleEnabled(false);
        updateStatusText(getString(R.string.countdown_status_focus));
    }

    private void showCountdownPaused() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        btnCountdownStart.setVisibility(View.GONE);
        llCountdownDual.setVisibility(View.VISIBLE);
        btnCountdownPauseResume.setText(R.string.countdown_btn_resume);
        btnCountdownPauseResume.setIconResource(R.drawable.ic_play);
        updateStatusText(getString(R.string.countdown_status_paused));
    }

    // ── UI State helpers — stopwatch ─────────────────────────────────────────

    private void switchToStopwatchMode() {
        containerCountdownButtons.setVisibility(View.GONE);
        containerSwButtons.setVisibility(View.VISIBLE);
        progressCountdown.setVisibility(View.GONE);
        swBarsView.setVisibility(View.VISIBLE);
        tvCountdown.setClickable(false);
        showStopwatchIdle();
    }

    private void showStopwatchIdle() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        btnSwStart.setVisibility(View.VISIBLE);
        llSwActive.setVisibility(View.GONE);
        rvLaps.setVisibility(View.GONE);
        swBarsView.reset();
        setToggleEnabled(true);
        updateTimerDisplay(0);
        updateStatusText(getString(R.string.countdown_status_ready));
    }

    private void showStopwatchRunning() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        btnSwStart.setVisibility(View.GONE);
        llSwActive.setVisibility(View.VISIBLE);
        rvLaps.setVisibility(View.VISIBLE);
        // Flag: active
        btnSwFlag.setAlpha(1f);
        btnSwFlag.setEnabled(true);
        // Stop: enabled, shows "Dừng"
        btnSwStopResume.setText(R.string.countdown_btn_stop);
        btnSwStopResume.setIconResource(R.drawable.ic_stop);
        // Reset: dimmed (disabled while running)
        btnSwReset.setAlpha(0.35f);
        btnSwReset.setEnabled(false);
        setToggleEnabled(false);
        updateStatusText(getString(R.string.countdown_status_focus));
    }

    private void showStopwatchStopped() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        btnSwStart.setVisibility(View.GONE);
        llSwActive.setVisibility(View.VISIBLE);
        rvLaps.setVisibility(View.VISIBLE);
        // Flag: dimmed
        btnSwFlag.setAlpha(0.3f);
        btnSwFlag.setEnabled(false);
        // Resume: shows "Bắt đầu"
        btnSwStopResume.setText(R.string.countdown_btn_start);
        btnSwStopResume.setIconResource(R.drawable.ic_play);
        // Reset: fully active
        btnSwReset.setAlpha(1f);
        btnSwReset.setEnabled(true);
        updateStatusText(getString(R.string.countdown_status_stopped));
    }

    // ── Display updates ──────────────────────────────────────────────────────

    private void updateTimerDisplay(long millis) {
        long minutes = millis / 60_000;
        long seconds = (millis / 1000) % 60;
        tvCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));

        // Progress arc update (countdown mode)
        if (timerService != null
                && timerService.getTimerMode() == TimerService.TimerMode.COUNTDOWN) {
            long total = timerService.getTotalMillis();
            if (total > 0) {
                int progress = (int) (millis * 1000L / total);
                progressCountdown.setProgressCompat(progress, true);
            }
        }

        // Tick bars update (stopwatch mode)
        if (timerService != null
                && timerService.getTimerMode() == TimerService.TimerMode.STOPWATCH) {
            int second = (int) (millis / 1000) % 60;
            swBarsView.setCurrentSecond(second);
        }

        animateTick();
    }

    private void animateTick() {
        tvCountdown.animate()
                .scaleX(1.04f).scaleY(1.04f).setDuration(70)
                .withEndAction(() -> tvCountdown.animate()
                        .scaleX(1f).scaleY(1f).setDuration(110).start())
                .start();
    }

    private void updateStatusText(String text) {
        tvTimerStatus.animate().alpha(0f).setDuration(100)
                .withEndAction(() -> {
                    tvTimerStatus.setText(text);
                    tvTimerStatus.animate().alpha(1f).setDuration(150).start();
                }).start();
    }

    private void updateAmbientIndicator(boolean isPlaying) {
        if (tvAmbientIndicator == null) return;
        if (isPlaying) {
            tvAmbientIndicator.setVisibility(View.VISIBLE);
            tvAmbientIndicator.animate().alpha(1f).setDuration(300).start();
        } else {
            tvAmbientIndicator.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> tvAmbientIndicator.setVisibility(View.GONE))
                    .start();
        }
    }

    // ── Service sync ─────────────────────────────────────────────────────────

    private void syncUiWithService() {
        if (timerService == null) return;

        TimerService.TimerMode  mode  = timerService.getTimerMode();
        TimerService.TimerState state = timerService.getTimerState();
        long millis = timerService.getMillisRemaining();

        // Set toggle
        toggleTimerMode.check(mode == TimerService.TimerMode.COUNTDOWN
                ? R.id.btn_mode_countdown : R.id.btn_mode_stopwatch);

        // Set mode-specific visibility
        if (mode == TimerService.TimerMode.COUNTDOWN) {
            progressCountdown.setVisibility(View.VISIBLE);
            swBarsView.setVisibility(View.GONE);
            containerCountdownButtons.setVisibility(View.VISIBLE);
            containerSwButtons.setVisibility(View.GONE);
            tvCountdown.setClickable(state == TimerService.TimerState.IDLE);

            if (state == TimerService.TimerState.RUNNING)       showCountdownRunning();
            else if (state == TimerService.TimerState.PAUSED)   showCountdownPaused();
            else                                                  showCountdownIdle();

        } else {
            progressCountdown.setVisibility(View.GONE);
            swBarsView.setVisibility(View.VISIBLE);
            containerCountdownButtons.setVisibility(View.GONE);
            containerSwButtons.setVisibility(View.VISIBLE);
            tvCountdown.setClickable(false);

            if (state == TimerService.TimerState.RUNNING)       showStopwatchRunning();
            else if (state == TimerService.TimerState.PAUSED)   showStopwatchStopped();
            else                                                  showStopwatchIdle();
        }

        updateTimerDisplay(millis);
    }

    private void onTimerFinished() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateStatusText(getString(R.string.countdown_status_done));

        if (timerService != null
                && timerService.getTimerMode() == TimerService.TimerMode.COUNTDOWN) {
            progressCountdown.setProgressCompat(0, true);
            showCountdownIdle();
        }
        Toast.makeText(this, R.string.countdown_toast_done, Toast.LENGTH_LONG).show();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void startForegroundTimer() {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(TimerService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
    }

    private void setToggleEnabled(boolean enabled) {
        for (int i = 0; i < toggleTimerMode.getChildCount(); i++) {
            toggleTimerMode.getChildAt(i).setEnabled(enabled);
        }
    }

    // ── Time-picker dialog ───────────────────────────────────────────────────

    private void showTimePickerDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_time_picker, null);
        dialog.setContentView(view);

        NumberPicker np = view.findViewById(R.id.np_minutes);
        np.setMinValue(1);
        np.setMaxValue(180);
        np.setValue(timerService != null ? timerService.getCurrentModeMins() : 25);

        // Preset chips — tap to apply instantly and close
        ChipGroup chipGroup = view.findViewById(R.id.chip_group_time);
        int[] presets = {5, 10, 15, 20, 25, 30, 45, 60};
        int[] chipIds = {R.id.chip_5, R.id.chip_10, R.id.chip_15, R.id.chip_20,
                         R.id.chip_25, R.id.chip_30, R.id.chip_45, R.id.chip_60};
        // Pre-check the chip matching current value
        int current = timerService != null ? timerService.getCurrentModeMins() : 25;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == current) {
                Chip chip = view.findViewById(chipIds[i]);
                if (chip != null) chip.setChecked(true);
            }
            final int mins = presets[i];
            Chip chip = view.findViewById(chipIds[i]);
            if (chip != null) {
                chip.setOnCheckedChangeListener((c, checked) -> {
                    if (checked) {
                        applyTimerMinutes(mins);
                        dialog.dismiss();
                    }
                });
            }
        }

        // Manual picker confirm
        view.findViewById(R.id.btn_save_time).setOnClickListener(v -> {
            applyTimerMinutes(np.getValue());
            dialog.dismiss();
        });

        dialog.show();
    }

    private void applyTimerMinutes(int minutes) {
        if (timerService != null) timerService.setMode(minutes);
    }

    // ── Ambient sound dialog ─────────────────────────────────────────────────

    private void showAmbientSoundDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_ambient_sound, null);
        dialog.setContentView(view);

        RadioGroup radioGroup = view.findViewById(R.id.rg_ambient_sound);
        int currentMode = isBound && timerService != null
                ? timerService.getAmbientSoundMode()
                : getSavedAmbientSoundMode();
        radioGroup.check(toRadioButtonId(currentMode));

        view.findViewById(R.id.btn_apply_sound).setOnClickListener(v -> {
            int selectedMode = resolveSelectedSoundMode(radioGroup.getCheckedRadioButtonId());
            saveAmbientSoundMode(selectedMode);
            if (isBound && timerService != null) {
                timerService.setAmbientSoundMode(selectedMode);
                if (selectedMode != TimerService.AMBIENT_SOUND_NONE
                        && !timerService.hasAmbientSoundResource(selectedMode)) {
                    Toast.makeText(this, R.string.countdown_sound_missing_files,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.countdown_sound_applied,
                            Toast.LENGTH_SHORT).show();
                }
            }
            updateAmbientIndicator(selectedMode != TimerService.AMBIENT_SOUND_NONE);
            dialog.dismiss();
        });

        dialog.show();
    }

    private int resolveSelectedSoundMode(int checkedId) {
        if (checkedId == R.id.rb_sound_rain) return TimerService.AMBIENT_SOUND_RAIN;
        if (checkedId == R.id.rb_sound_cafe) return TimerService.AMBIENT_SOUND_CAFE;
        if (checkedId == R.id.rb_sound_lofi) return TimerService.AMBIENT_SOUND_LOFI;
        return TimerService.AMBIENT_SOUND_NONE;
    }

    private int toRadioButtonId(int mode) {
        switch (mode) {
            case TimerService.AMBIENT_SOUND_RAIN: return R.id.rb_sound_rain;
            case TimerService.AMBIENT_SOUND_CAFE: return R.id.rb_sound_cafe;
            case TimerService.AMBIENT_SOUND_LOFI: return R.id.rb_sound_lofi;
            default:                              return R.id.rb_sound_none;
        }
    }

    private int getSavedAmbientSoundMode() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_AMBIENT_SOUND, TimerService.AMBIENT_SOUND_NONE);
    }

    private void saveAmbientSoundMode(int mode) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putInt(KEY_AMBIENT_SOUND, mode).apply();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Lap list adapter
    // ════════════════════════════════════════════════════════════════════════

    private static class LapAdapter extends RecyclerView.Adapter<LapAdapter.VH> {

        private final List<Long> data; // newest at index 0

        LapAdapter(List<Long> data) { this.data = data; }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvLapNumber;
            final TextView tvLapTime;
            VH(@NonNull View v) {
                super(v);
                tvLapNumber = v.findViewById(R.id.tv_lap_number);
                tvLapTime   = v.findViewById(R.id.tv_lap_time);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_lap, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            int lapNumber = data.size() - pos; // newest = highest number
            long millis   = data.get(pos);
            long minutes  = millis / 60_000;
            long seconds  = (millis / 1000) % 60;
            h.tvLapNumber.setText(String.format(Locale.getDefault(), "Cờ %d", lapNumber));
            h.tvLapTime.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
        }

        @Override public int getItemCount() { return data.size(); }
    }
}
