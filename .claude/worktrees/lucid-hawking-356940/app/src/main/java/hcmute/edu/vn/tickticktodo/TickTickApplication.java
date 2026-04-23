package hcmute.edu.vn.doinbot;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import hcmute.edu.vn.doinbot.agent.context.ContextAgent;
import hcmute.edu.vn.doinbot.agent.core.AgentEventBus;
import hcmute.edu.vn.doinbot.agent.proactive.ProactiveEngine;
import hcmute.edu.vn.doinbot.helper.AppRuntimeState;
import hcmute.edu.vn.doinbot.helper.GeminiManager;
import hcmute.edu.vn.doinbot.helper.NotificationHelper;
import hcmute.edu.vn.doinbot.helper.SecurePreferencesHelper;
import hcmute.edu.vn.doinbot.helper.UsageStreakManager;
import hcmute.edu.vn.doinbot.core.background.FloatingAssistantService;

public class TickTickApplication extends Application {

        private static final String PREFS_NAME = "TickTickPrefs";
        private static final String KEY_FLOATING_ASSISTANT_ENABLED = "floating_assistant_enabled";
        private static final String TAG = "TickTickApplication";

        private int startedActivities = 0;
        private final TickTickWorkerBootstrapCoordinator workerBootstrapCoordinator =
                new TickTickWorkerBootstrapCoordinator();
        private final TickTickReceiverLifecycleHelper receiverLifecycleHelper =
                new TickTickReceiverLifecycleHelper();

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
                receiverLifecycleHelper.registerSystemStateReceiver(this, TAG);
        }

        private void unregisterSystemStateReceiver() {
                receiverLifecycleHelper.unregisterSystemStateReceiver(this, TAG);
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
