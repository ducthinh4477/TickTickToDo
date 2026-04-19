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

import hcmute.edu.vn.tickticktodo.agent.context.ContextAgent;
import hcmute.edu.vn.tickticktodo.agent.core.AgentEventBus;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveEngine;
import hcmute.edu.vn.tickticktodo.helper.AppRuntimeState;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.helper.NotificationHelper;
import hcmute.edu.vn.tickticktodo.helper.SecurePreferencesHelper;
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
        private final TickTickWorkerBootstrapCoordinator workerBootstrapCoordinator =
                new TickTickWorkerBootstrapCoordinator();

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Notification Channels
        NotificationHelper.createNotificationChannels(this);

        // Schedule Workers
        scheduleWorkers();

        registerSystemStateReceiver();
        AppRuntimeState.initialize(this);
        SecurePreferencesHelper.getInstance(this);
        GeminiManager.initialize(this);
        initializePhase1ProactiveComponents();

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
                                AppRuntimeState.updateOnActivityStarted(
                                        activity.getApplicationContext(),
                                        activity.getClass().getSimpleName(),
                                        startedActivities
                                );
                        }

                        @Override
                        public void onActivityResumed(Activity activity) {
                                AppRuntimeState.updateOnActivityResumed(
                                        activity.getApplicationContext(),
                                        activity.getClass().getSimpleName()
                                );
                        }

                        @Override
                        public void onActivityPaused(Activity activity) {
                        }

                        @Override
                        public void onActivityStopped(Activity activity) {
                                boolean changingConfigurations = activity.isChangingConfigurations();
                                startedActivities = Math.max(0, startedActivities - 1);
                                AppRuntimeState.updateOnActivityStopped(
                                        activity.getApplicationContext(),
                                        startedActivities
                                );
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
        workerBootstrapCoordinator.schedule(this);
    }

    private void initializePhase1ProactiveComponents() {
        AgentEventBus.getInstance();
        ContextAgent contextAgent = ContextAgent.getInstance(this);
        contextAgent.refreshSnapshot("APP_STARTUP");
        ProactiveEngine proactiveEngine = ProactiveEngine.getInstance(this);
        proactiveEngine.evaluateNow("APP_STARTUP");
    }

}
