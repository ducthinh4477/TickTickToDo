package hcmute.edu.vn.tickticktodo.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import hcmute.edu.vn.tickticktodo.core.background.FloatingAssistantService;

public class MainPermissionHandler {

    private final MainActivity activity;

    public MainPermissionHandler(MainActivity activity) {
        this.activity = activity;
    }

    public void syncFloatingAssistantState(String prefsName, String keyFloatingAssistantEnabled) {
        SharedPreferences prefs = activity.getSharedPreferences(prefsName, MainActivity.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(keyFloatingAssistantEnabled, false);
        boolean hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(activity);

        Intent serviceIntent = new Intent(activity, FloatingAssistantService.class);
        if (enabled && hasOverlayPermission) {
            startFloatingServiceSafely(prefs, keyFloatingAssistantEnabled, FloatingAssistantService.ACTION_HIDE_BUBBLE);
        } else {
            activity.stopService(serviceIntent);
            if (enabled && !hasOverlayPermission) {
                prefs.edit().putBoolean(keyFloatingAssistantEnabled, false).apply();
            }
        }
    }

    private void startFloatingServiceSafely(SharedPreferences prefs,
                                            String keyFloatingAssistantEnabled,
                                            String action) {
        Intent serviceIntent = new Intent(activity, FloatingAssistantService.class);
        serviceIntent.setAction(action);
        try {
            ContextCompat.startForegroundService(activity, serviceIntent);
        } catch (Exception firstException) {
            try {
                activity.startService(serviceIntent);
            } catch (Exception secondException) {
                activity.stopService(serviceIntent);
                prefs.edit().putBoolean(keyFloatingAssistantEnabled, false).apply();
                Toast.makeText(activity,
                        "Không thể khởi động Trợ lý nổi trên thiết bị này.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
