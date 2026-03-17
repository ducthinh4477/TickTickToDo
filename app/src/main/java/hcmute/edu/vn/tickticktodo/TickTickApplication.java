package hcmute.edu.vn.tickticktodo;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.tickticktodo.worker.DatabaseCleanupWorker;
import hcmute.edu.vn.tickticktodo.worker.SyncWorker;
import hcmute.edu.vn.tickticktodo.helper.NotificationHelper;

public class TickTickApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Notification Channels
        NotificationHelper.createNotificationChannels(this);

        // Schedule Workers
        scheduleWorkers();
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
        hcmute.edu.vn.tickticktodo.worker.SchoolSyncWorker.schedulePeriodicSync(this);
    }
}
