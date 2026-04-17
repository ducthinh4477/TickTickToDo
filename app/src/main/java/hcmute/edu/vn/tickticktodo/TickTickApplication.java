package hcmute.edu.vn.tickticktodo;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.tickticktodo.core.background.DatabaseCleanupWorker;
import hcmute.edu.vn.tickticktodo.core.background.DailyReviewWorker;
import hcmute.edu.vn.tickticktodo.core.background.SyncWorker;
import hcmute.edu.vn.tickticktodo.core.background.DailyDigestWorker;
import hcmute.edu.vn.tickticktodo.core.background.OverdueCheckWorker;
import hcmute.edu.vn.tickticktodo.helper.NotificationHelper;
import hcmute.edu.vn.tickticktodo.helper.UsageStreakManager;
import hcmute.edu.vn.tickticktodo.core.background.SystemStateReceiver;
import hcmute.edu.vn.tickticktodo.core.background.FloatingAssistantService;

public class TickTickApplication extends Application {

        private static final String PREFS_NAME = "TickTickPrefs";
        private static final String KEY_FLOATING_ASSISTANT_ENABLED = "floating_assistant_enabled";
        private static final String TAG = "TickTickApplication";

        private int startedActivities = 0;
        private SystemStateReceiver systemStateReceiver;
        private boolean systemStateReceiverRegistered;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Notification Channels
        NotificationHelper.createNotificationChannels(this);

        // Schedule Workers
        scheduleWorkers();

        registerSystemStateReceiver();

                registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
                        @Override
                        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                        }

                        @Override
                        public void onActivityStarted(Activity activity) {
                                UsageStreakManager.markUsageAndGetCurrentStreak(activity.getApplicationContext());
                                if (startedActivities == 0) {
                                        syncFloatingAssistantOverlay(true);
                                }
                                startedActivities++;
                        }

                        @Override
                        public void onActivityResumed(Activity activity) {
                        }

                        @Override
                        public void onActivityPaused(Activity activity) {
                        }

                        @Override
                        public void onActivityStopped(Activity activity) {
                                boolean changingConfigurations = activity.isChangingConfigurations();
                                startedActivities = Math.max(0, startedActivities - 1);
                                if (startedActivities == 0 && !changingConfigurations) {
                                        syncFloatingAssistantOverlay(false);
                                }
                        }

                        @Override
                        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                        }

                        @Override
                        public void onActivityDestroyed(Activity activity) {
                        }
                });
    }

        @Override
        public void onTerminate() {
                unregisterSystemStateReceiver();
                super.onTerminate();
        }

        private void registerSystemStateReceiver() {
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
                        registerReceiver(systemStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                        registerReceiver(systemStateReceiver, filter);
                }

                systemStateReceiverRegistered = true;
                Log.d(TAG, "SystemStateReceiver registered");
        }

        private void unregisterSystemStateReceiver() {
                if (!systemStateReceiverRegistered || systemStateReceiver == null) {
                        return;
                }

                try {
                        unregisterReceiver(systemStateReceiver);
                        Log.d(TAG, "SystemStateReceiver unregistered");
                } catch (IllegalArgumentException exception) {
                        Log.w(TAG, "SystemStateReceiver was already unregistered", exception);
                } finally {
                        systemStateReceiverRegistered = false;
                }
        }

        private void syncFloatingAssistantOverlay(boolean appInForeground) {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean enabled = prefs.getBoolean(KEY_FLOATING_ASSISTANT_ENABLED, false);

                Intent serviceIntent = new Intent(this, FloatingAssistantService.class);
                if (!enabled) {
                        stopService(serviceIntent);
                        return;
                }

                boolean hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                                || Settings.canDrawOverlays(this);
                if (!hasOverlayPermission) {
                        prefs.edit().putBoolean(KEY_FLOATING_ASSISTANT_ENABLED, false).apply();
                        stopService(serviceIntent);
                        return;
                }

                serviceIntent.setAction(appInForeground
                                ? FloatingAssistantService.ACTION_HIDE_BUBBLE
                                : FloatingAssistantService.ACTION_SHOW_BUBBLE);
                Log.d(TAG, "syncFloatingAssistantOverlay enabled=" + enabled
                                + ", appInForeground=" + appInForeground
                                + ", startedActivities=" + startedActivities
                                + ", action=" + serviceIntent.getAction());

                try {
                        ContextCompat.startForegroundService(this, serviceIntent);
                } catch (Exception firstException) {
                        try {
                                startService(serviceIntent);
                        } catch (Exception secondException) {
                                stopService(serviceIntent);
                                prefs.edit().putBoolean(KEY_FLOATING_ASSISTANT_ENABLED, false).apply();
                        }
                }
        }

    private void scheduleWorkers() {
        WorkManager workManager = WorkManager.getInstance(this);

        // 1. Database Cleanup Worker (Weekly)
        // Constraints: Requires Battery Not Low and Device Charging (for intensive cleanup)
        Constraints cleanupConstraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
                .build();

        PeriodicWorkRequest cleanupRequest =
                new PeriodicWorkRequest.Builder(DatabaseCleanupWorker.class, 7, TimeUnit.DAYS)
                        .setConstraints(cleanupConstraints)
                        .addTag("database_cleanup")
                        .build();

        workManager.enqueueUniquePeriodicWork(
                "DatabaseCleanupWorker",
                ExistingPeriodicWorkPolicy.KEEP, // If exists, keep existing periodic schedule
                cleanupRequest
        );

        // 2. Sync Worker (Periodic, e.g., every 12 hours)
        // Constraints: Network Connected + Charging (optional but good for background sync)
        Constraints syncConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(true)
                .build();

        PeriodicWorkRequest syncRequest =
                new PeriodicWorkRequest.Builder(SyncWorker.class, 12, TimeUnit.HOURS)
                        .setConstraints(syncConstraints)
                        .addTag("data_sync")
                        .build();

        workManager.enqueueUniquePeriodicWork(
                "SyncWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
        );

        // 3. School Sync Worker
        // Checks every 6 hours for new school tasks if enabled
        hcmute.edu.vn.tickticktodo.ui.moodle.SchoolSyncWorker.schedulePeriodicSync(this);

        // 4. Daily Digest Worker — gửi tổng hợp công việc lúc 8:00 AM mỗi ngày
        DailyDigestWorker.schedule(this);

        // 5. Overdue Check Worker — kiểm tra task quá hạn mỗi 12 giờ
        OverdueCheckWorker.schedule(this);

                // 6. Daily Review Worker — tổng kết cuối ngày lúc 21:00
                DailyReviewWorker.schedule(this);
    }
}
