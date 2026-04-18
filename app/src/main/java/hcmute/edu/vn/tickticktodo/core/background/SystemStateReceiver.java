package hcmute.edu.vn.tickticktodo.core.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.WorkManager;

import hcmute.edu.vn.tickticktodo.ui.countdown.TimerService;
import hcmute.edu.vn.tickticktodo.ui.moodle.SchoolSyncReceiver;
import hcmute.edu.vn.tickticktodo.ui.moodle.SchoolSyncWorker;

/**
 * Lắng nghe trạng thái hệ thống để bảo vệ trải nghiệm Pomodoro và pin thiết bị.
 */
public class SystemStateReceiver extends BroadcastReceiver {

    private static final String TAG = "SystemStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            handleScreenOff(context);
        } else if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            handleBatteryLow(context);
        } else if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
            handleBatteryOkay(context);
        }
    }

    private void handleScreenOff(Context context) {
        if (!TimerService.isTimerRunning()) {
            return;
        }

        Log.d(TAG, "ACTION_SCREEN_OFF while timer running. Auto-pausing Pomodoro.");
        Intent pauseIntent = new Intent(context, TimerService.class);
        pauseIntent.setAction(TimerService.ACTION_PAUSE);
        try {
            context.startService(pauseIntent);
        } catch (Exception exception) {
            Log.w(TAG, "Unable to dispatch timer pause action", exception);
        }
    }

    private void handleBatteryLow(Context context) {
        Log.d(TAG, "ACTION_BATTERY_LOW received. Pausing heavy school sync tasks.");
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork("SchoolSyncWork");
        workManager.cancelUniqueWork("SchoolSyncBgAlarm");
        workManager.cancelAllWorkByTag("school_sync_bg");
        SchoolSyncReceiver.cancelExact(context);
    }

    private void handleBatteryOkay(Context context) {
        Log.d(TAG, "ACTION_BATTERY_OKAY received. Rescheduling school periodic sync.");
        SchoolSyncWorker.schedulePeriodicSync(context);
    }
}
