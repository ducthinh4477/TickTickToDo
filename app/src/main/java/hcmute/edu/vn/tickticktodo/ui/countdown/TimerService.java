package hcmute.edu.vn.tickticktodo.ui.countdown;

import android.app.Notification; // Added
import android.app.NotificationManager; // Added
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import hcmute.edu.vn.tickticktodo.helper.NotificationHelper; // Added

/**
 * TimerService — chứa toàn bộ logic đếm ngược Pomodoro.
 *
 * Kiến trúc giao tiếp:
 *   Activity  ──bind──►  Service  (gọi start/pause/stop qua Binder)
 *   Service   ──broadcast──►  Activity  (gửi tick & event mỗi giây)
 *
 * Broadcasts được gửi:
 *   ACTION_TICK   — mỗi giây, kèm EXTRA_MILLIS_REMAINING
 *   ACTION_FINISH — khi đếm ngược kết thúc, kèm EXTRA_MODE_MINS
 *
 * Vòng đời:
 *   bindService() → onBind() → client dùng Binder
 *   Khi tất cả client unbind → onUnbind() → Service tự stopSelf()
 */
public class TimerService extends Service {

    private static final String TAG = "TimerService";

    // ── Broadcast action constants ───────────────────────────────────────────────
    public static final String ACTION_TICK   = "hcmute.ticktick.TIMER_TICK";
    public static final String ACTION_FINISH = "hcmute.ticktick.TIMER_FINISH";

    // Service Actions
    public static final String ACTION_START  = "hcmute.ticktick.TIMER_START"; // Added
    public static final String ACTION_PAUSE  = "hcmute.ticktick.TIMER_PAUSE";
    public static final String ACTION_RESUME = "hcmute.ticktick.TIMER_RESUME";
    public static final String ACTION_STOP   = "hcmute.ticktick.TIMER_STOP";

    // Notification ID
    private static final int NOTIFICATION_ID = 2026;

    // ── Broadcast extras ─────────────────────────────────────────────────────────
    public static final String EXTRA_MILLIS_REMAINING = "extra_millis_remaining";
    public static final String EXTRA_MODE_MINS        = "extra_mode_mins";

    // ── Timer state (mirror của Activity) ────────────────────────────────────────
    public enum TimerState { IDLE, RUNNING, PAUSED }
    public enum TimerMode { COUNTDOWN, STOPWATCH }

    public static final int AMBIENT_SOUND_NONE = 0;
    public static final int AMBIENT_SOUND_RAIN = 1;
    public static final int AMBIENT_SOUND_CAFE = 2;
    public static final int AMBIENT_SOUND_LOFI = 3;

    // ── Internal state ───────────────────────────────────────────────────────────
    private TimerState timerState   = TimerState.IDLE;
    private static volatile TimerState processTimerState = TimerState.IDLE;
    private TimerMode  timerMode    = TimerMode.COUNTDOWN;
    private int   currentModeMins   = 25;          // mặc định Pomodoro
    private long  totalMillis       = 25 * 60 * 1000L;
    private long  millisRemaining   = totalMillis;
    private long  stopwatchMillis   = 0L;
    private int   sessionCount      = 1;

    private CountDownTimer countDownTimer;
    private Handler stopwatchHandler;
    private Runnable stopwatchRunnable;
    private MediaPlayer    mediaPlayer;
    private MediaPlayer ambientMediaPlayer;
    private int ambientSoundMode = AMBIENT_SOUND_NONE;

    private LocalBroadcastManager lbm;

    // Is bound?
    private boolean isBound = false;

    // ── Binder ──────────────────────────────────────────────────────────────────

    /**
     * Binder trả về tham chiếu trực tiếp tới TimerService.
     * Activity dùng binder.getService() để gọi các phương thức điều khiển.
     */
    public class TimerBinder extends Binder {
        public TimerService getService() {
            return TimerService.this;
        }
    }

    private final TimerBinder binder = new TimerBinder();

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        lbm = LocalBroadcastManager.getInstance(this);
        processTimerState = timerState;
        // Ensure channel exists
        NotificationHelper.createNotificationChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_START:
                    startTimer();
                    break;
                case ACTION_PAUSE:
                    pauseTimer();
                    break;
                case ACTION_RESUME:
                    resumeTimer();
                    break;
                case ACTION_STOP:
                    stopTimer();
                    break;
            }
        }
        // START_STICKY: Hệ thống sẽ cố gắng tạo lại service nếu bị kill khi đang chạy
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        isBound = true;
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        isBound = false;
        // Logic quan trọng:
        // Chỉ Stop Service nếu Timer đang IDLE (đã dừng hoặc chưa chạy).
        // Nếu đang RUNNING hoặc PAUSED, Service KHÔNG được chết dù Activity đã unbind.
        if (timerState == TimerState.IDLE) {
            stopSelf();
        }
        return true; // return true để sau này rebind được (onRebind)
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelCountdown();
        releaseMediaPlayer();
        releaseAmbientSoundPlayer();
        stopForeground(true); // Ensure foreground is stopped
        timerState = TimerState.IDLE;
        processTimerState = TimerState.IDLE;
        Log.d(TAG, "TimerService destroyed, state reset to IDLE");
    }

    // ── Public API (gọi qua Binder) ──────────────────────────────────────────────

    /** Lấy trạng thái hiện tại để Activity sync UI khi vừa bind */
    public TimerState getTimerState()    { return timerState; }
    public static boolean isTimerRunning() { return processTimerState == TimerState.RUNNING; }
    public TimerMode  getTimerMode()     { return timerMode; }
    public long  getMillisRemaining()    { return timerMode == TimerMode.COUNTDOWN ? millisRemaining : stopwatchMillis; }
    public long  getTotalMillis()        { return totalMillis; }
    public int   getCurrentModeMins()    { return currentModeMins; }
    public int   getSessionCount()       { return sessionCount; }
    public int   getAmbientSoundMode()   { return ambientSoundMode; }

    public void setAmbientSoundMode(int mode) {
        ambientSoundMode = mode;
        if (timerState == TimerState.RUNNING) {
            startAmbientSound();
        } else {
            stopAmbientSound();
        }
    }

    public boolean hasAmbientSoundResource(int mode) {
        if (mode == AMBIENT_SOUND_NONE) {
            return true;
        }
        return resolveAmbientSoundResId(mode) != 0;
    }

    public void setTimerMode(TimerMode mode) {
        if (timerState != TimerState.IDLE) return;
        this.timerMode = mode;
        if (mode == TimerMode.COUNTDOWN) {
            millisRemaining = totalMillis;
            broadcastTick(millisRemaining);
        } else {
            stopwatchMillis = 0L;
            broadcastTick(stopwatchMillis);
        }
    }

    /** Đặt thời gian (Pomodoro / Short Break / Long Break) — chỉ khi IDLE */
    public void setMode(int minutes) {
        if (timerState != TimerState.IDLE) return;
        currentModeMins = minutes;
        totalMillis     = minutes * 60 * 1000L;
        millisRemaining = totalMillis;
        // Gửi ngay một tick để Activity cập nhật hiển thị
        broadcastTick(millisRemaining);
        updateNotification(); // Start notification immediately? No, wait for start.
    }

    public void startTimer() {
        if (timerState == TimerState.IDLE || timerState == TimerState.PAUSED) {
            timerState = TimerState.RUNNING;
            processTimerState = timerState;
            if (timerMode == TimerMode.COUNTDOWN) {
                runCountdown(millisRemaining);
            } else {
                runStopwatch();
            }
            startForegroundServiceNotification(); // Start foreground
            startAmbientSound();
        }
    }

    public void pauseTimer() {
        if (timerState == TimerState.RUNNING) {
            cancelCountdown();
            timerState = TimerState.PAUSED;
            processTimerState = timerState;
            updateNotification(); // Update to show "Paused"
            stopAmbientSound();
            Log.d(TAG, "Timer paused");
        }
    }

    public void resumeTimer() {
        if (timerState == TimerState.PAUSED) {
            timerState = TimerState.RUNNING;
            processTimerState = timerState;
            if (timerMode == TimerMode.COUNTDOWN) {
                runCountdown(millisRemaining);
            } else {
                runStopwatch();
            }
            updateNotification(); // Update to show "Running"
            startAmbientSound();
        }
    }

    public void stopTimer() {
        cancelCountdown();
        timerState      = TimerState.IDLE;
        processTimerState = timerState;
        millisRemaining = totalMillis; // Reset về ban đầu của chế độ hiện tại
        stopAmbientSound();

        broadcastTick(millisRemaining); // Update UI
        stopForeground(true); // Xóa notification

        // Nếu không còn ai bind và đã Stop, thì kill service luôn
        if (!isBound) stopSelf();
    }

    // ── Internal timer logic ─────────────────────────────────────────────────────

    private void runCountdown(long millis) {
        countDownTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                millisRemaining = millisUntilFinished;
                broadcastTick(millisUntilFinished);
                updateNotification(); // Update notification every second
            }

            @Override
            public void onFinish() {
                millisRemaining = 0;
                broadcastTick(0);
                onTimerFinished();
            }
        }.start();
    }

    private void runStopwatch() {
        if (stopwatchHandler == null) {
            stopwatchHandler = new Handler(Looper.getMainLooper());
        }
        stopwatchRunnable = new Runnable() {
            @Override
            public void run() {
                if (timerState == TimerState.RUNNING) {
                    stopwatchMillis += 1000;
                    broadcastTick(stopwatchMillis);
                    updateNotification();
                    stopwatchHandler.postDelayed(this, 1000);
                }
            }
        };
        stopwatchHandler.postDelayed(stopwatchRunnable, 1000);
    }

    private void onTimerFinished() {
        timerState = TimerState.IDLE;
        processTimerState = timerState;

        sessionCount++;

        millisRemaining = totalMillis;
        stopAmbientSound();
        playAlarmSound();
        broadcastFinish();
        stopForeground(true); // Stop foreground

        // Stop self if unbound
        if (!isBound) stopSelf();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        if (stopwatchHandler != null && stopwatchRunnable != null) {
            stopwatchHandler.removeCallbacks(stopwatchRunnable);
        }
    }

    // ── Broadcasts ───────────────────────────────────────────────────────────────

    private void broadcastTick(long millisLeft) {
        Intent intent = new Intent(ACTION_TICK);
        intent.putExtra(EXTRA_MILLIS_REMAINING, millisLeft);
        lbm.sendBroadcast(intent);
    }

    private void broadcastFinish() {
        Intent intent = new Intent(ACTION_FINISH);
        intent.putExtra(EXTRA_MODE_MINS, currentModeMins);
        lbm.sendBroadcast(intent);
    }

    // ── Notification Helper ──────────────────────────────────────────────────────

    private void startForegroundServiceNotification() {
        Notification notification = NotificationHelper.buildTimerNotification(
            this, getMillisRemaining(), timerState == TimerState.RUNNING
        );

        // For Android 14, need to specify type in logic if possible,
        // but startForeground(id, notif) usually implies declared types in manifest
        // However, scoped storage access etc might need type.
        // Since we claimed "specialUse", we just use standard startForeground.

        // If we strictly follow Android 14 API for strict types (e.g. DATA_SYNC):
        // if (Build.VERSION.SDK_INT >= 34) {
        //    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        // } else ...

        // But for SpecialUse, we can often just call the basic one if manifest is correct,
        // or passing type is safer.

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
             // Use minimal type if required. For now, basic call.
             // Ideally: pass the type.
             try {
                // If compiling against SDK 34, we can use ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                // But since I don't want to break imports if compileSdk is lower (though it is 35),
                // I'll stick to basic startForeground.
                // Actually, if I use specialUse, I *MUST* pass it in API 34+.
                 if (android.os.Build.VERSION.SDK_INT >= 34) {
                     // 2026 is random type ID? No.
                     // ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 32
                     startForeground(NOTIFICATION_ID, notification,
                         android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                 } else {
                     startForeground(NOTIFICATION_ID, notification);
                 }
             } catch (Exception e) {
                 startForeground(NOTIFICATION_ID, notification);
             }
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        // Only update if we are in Foreground (or at least start it if not)
        // If the service is running, update the notification
        if (timerState != TimerState.IDLE) {
            Notification notification = NotificationHelper.buildTimerNotification(
                this, getMillisRemaining(), timerState == TimerState.RUNNING
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    // ── Alarm sound ──────────────────────────────────────────────────────────────

    private void playAlarmSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            releaseMediaPlayer();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Tự dừng sau 3 giây
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            }, 3000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startAmbientSound() {
        stopAmbientSound();
        if (ambientSoundMode == AMBIENT_SOUND_NONE) {
            return;
        }

        int resId = resolveAmbientSoundResId(ambientSoundMode);
        if (resId == 0) {
            return;
        }

        try {
            ambientMediaPlayer = MediaPlayer.create(this, resId);
            if (ambientMediaPlayer == null) {
                return;
            }
            ambientMediaPlayer.setLooping(true);
            ambientMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            ambientMediaPlayer.start();
        } catch (Exception ignored) {
            releaseAmbientSoundPlayer();
        }
    }

    private void stopAmbientSound() {
        if (ambientMediaPlayer != null) {
            try {
                if (ambientMediaPlayer.isPlaying()) {
                    ambientMediaPlayer.stop();
                }
            } catch (Exception ignored) {
            }
            releaseAmbientSoundPlayer();
        }
    }

    private int resolveAmbientSoundResId(int mode) {
        String rawName;
        switch (mode) {
            case AMBIENT_SOUND_RAIN:
                rawName = "ambient_rain";
                break;
            case AMBIENT_SOUND_CAFE:
                rawName = "ambient_cafe";
                break;
            case AMBIENT_SOUND_LOFI:
                rawName = "ambient_lofi";
                break;
            case AMBIENT_SOUND_NONE:
            default:
                return 0;
        }
        return getResources().getIdentifier(rawName, "raw", getPackageName());
    }

    private void releaseAmbientSoundPlayer() {
        if (ambientMediaPlayer != null) {
            try {
                ambientMediaPlayer.release();
            } catch (Exception ignored) {
            }
            ambientMediaPlayer = null;
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}

