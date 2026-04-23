package hcmute.edu.vn.doinbot;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import hcmute.edu.vn.doinbot.core.background.SystemStateReceiver;

final class TickTickReceiverLifecycleHelper {

    private SystemStateReceiver systemStateReceiver;
    private boolean systemStateReceiverRegistered;

    void registerSystemStateReceiver(Application application, String logTag) {
        if (systemStateReceiverRegistered) {
            return;
        }

        if (systemStateReceiver == null) {
            systemStateReceiver = new SystemStateReceiver();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(systemStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            application.registerReceiver(systemStateReceiver, filter);
        }

        systemStateReceiverRegistered = true;
        Log.d(logTag, "SystemStateReceiver registered");
    }

    void unregisterSystemStateReceiver(Application application, String logTag) {
        if (!systemStateReceiverRegistered || systemStateReceiver == null) {
            return;
        }

        try {
            application.unregisterReceiver(systemStateReceiver);
            Log.d(logTag, "SystemStateReceiver unregistered");
        } catch (IllegalArgumentException exception) {
            Log.w(logTag, "SystemStateReceiver was already unregistered", exception);
        } finally {
            systemStateReceiverRegistered = false;
        }
    }
}