package hcmute.edu.vn.tickticktodo.service;

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

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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

    // ── Broadcast action constants ───────────────────────────────────────────────
    public static final String ACTION_TICK   = "hcmute.ticktick.TIMER_TICK";
    public static final String ACTION_FINISH = "hcmute.ticktick.TIMER_FINISH";

    // ── Broadcast extras ─────────────────────────────────────────────────────────
    public static final String EXTRA_MILLIS_REMAINING = "extra_millis_remaining";
    public static final String EXTRA_MODE_MINS        = "extra_mode_mins";

    // ── Timer state (mirror của Activity) ────────────────────────────────────────
    public enum TimerState { IDLE, RUNNING, PAUSED }

    // ── Internal state ───────────────────────────────────────────────────────────
    private TimerState timerState   = TimerState.IDLE;
    private int   currentModeMins   = 25;          // mặc định Pomodoro
    private long  totalMillis       = 25 * 60 * 1000L;
    private long  millisRemaining   = totalMillis;
    private int   sessionCount      = 1;

    private CountDownTimer countDownTimer;
    private MediaPlayer    mediaPlayer;

    private LocalBroadcastManager lbm;

    // ── Binder ───────────────────────────────────────────────────────────────────

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
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Khi Activity unbind (ví dụ: bị destroy), dừng Service nếu không chạy
        if (timerState == TimerState.IDLE) {
            stopSelf();
        }
        return false; // không cho rebind tự động
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelCountdown();
        releaseMediaPlayer();
    }

    // ── Public API (gọi qua Binder) ──────────────────────────────────────────────

    /** Lấy trạng thái hiện tại để Activity sync UI khi vừa bind */
    public TimerState getTimerState()    { return timerState; }
    public long  getMillisRemaining()    { return millisRemaining; }
    public long  getTotalMillis()        { return totalMillis; }
    public int   getCurrentModeMins()    { return currentModeMins; }
    public int   getSessionCount()       { return sessionCount; }

    /** Đặt chế độ (Pomodoro / Short Break / Long Break) — chỉ khi IDLE */
    public void setMode(int minutes) {
        if (timerState != TimerState.IDLE) return;
        currentModeMins = minutes;
        totalMillis     = minutes * 60 * 1000L;
        millisRemaining = totalMillis;
        // Gửi ngay một tick để Activity cập nhật hiển thị
        broadcastTick(millisRemaining);
    }

    public void startTimer() {
        if (timerState == TimerState.IDLE || timerState == TimerState.PAUSED) {
            timerState = TimerState.RUNNING;
            runCountdown(millisRemaining);
        }
    }

    public void pauseTimer() {
        if (timerState == TimerState.RUNNING) {
            cancelCountdown();
            timerState = TimerState.PAUSED;
        }
    }

    public void resumeTimer() {
        if (timerState == TimerState.PAUSED) {
            timerState = TimerState.RUNNING;
            runCountdown(millisRemaining);
        }
    }

    public void stopTimer() {
        cancelCountdown();
        timerState      = TimerState.IDLE;
        millisRemaining = totalMillis;
        broadcastTick(millisRemaining); // reset UI về thời gian ban đầu
    }

    // ── Internal timer logic ─────────────────────────────────────────────────────

    private void runCountdown(long millis) {
        countDownTimer = new CountDownTimer(millis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                millisRemaining = millisUntilFinished;
                broadcastTick(millisUntilFinished);
            }

            @Override
            public void onFinish() {
                millisRemaining = 0;
                broadcastTick(0);
                onTimerFinished();
            }
        }.start();
    }

    private void onTimerFinished() {
        timerState = TimerState.IDLE;

        if (currentModeMins == 25) {   // Pomodoro
            sessionCount++;
        }

        millisRemaining = totalMillis;
        playAlarmSound();
        broadcastFinish();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
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

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}

