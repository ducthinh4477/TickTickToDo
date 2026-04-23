package hcmute.edu.vn.doinbot;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.doinbot.core.background.ContextRefreshWorker;
import hcmute.edu.vn.doinbot.core.background.DatabaseCleanupWorker;
import hcmute.edu.vn.doinbot.core.background.DailyDigestWorker;
import hcmute.edu.vn.doinbot.core.background.DailyProfileReflectionWorker;
import hcmute.edu.vn.doinbot.core.background.DailyReviewWorker;
import hcmute.edu.vn.doinbot.core.background.OverdueCheckWorker;
import hcmute.edu.vn.doinbot.core.background.ProactiveTickWorker;
import hcmute.edu.vn.doinbot.core.background.SyncWorker;
import hcmute.edu.vn.doinbot.core.background.WeeklyProfileReflectionWorker;
import hcmute.edu.vn.doinbot.ui.moodle.SchoolSyncWorker;

final class TickTickWorkerBootstrapCoordinator {

    void schedule(Application application) {
        WorkManager workManager = WorkManager.getInstance(application);

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
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
        );

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

        SchoolSyncWorker.schedulePeriodicSync(application);
        DailyDigestWorker.schedule(application);
        OverdueCheckWorker.schedule(application);
        DailyReviewWorker.schedule(application);
        schedulePhase1Workers(workManager);
        DailyProfileReflectionWorker.schedule(application);
        WeeklyProfileReflectionWorker.schedule(application);
    }

    private void schedulePhase1Workers(WorkManager workManager) {
        PeriodicWorkRequest contextRefreshRequest =
                new PeriodicWorkRequest.Builder(ContextRefreshWorker.class, 1, TimeUnit.HOURS)
                        .addTag("context_refresh")
                        .build();

        workManager.enqueueUniquePeriodicWork(
                "ContextRefreshWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                contextRefreshRequest
        );

        Constraints proactiveConstraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest proactiveTickRequest =
                new PeriodicWorkRequest.Builder(ProactiveTickWorker.class, 6, TimeUnit.HOURS)
                        .setConstraints(proactiveConstraints)
                        .addTag("proactive_tick")
                        .build();

        workManager.enqueueUniquePeriodicWork(
                "ProactiveTickWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                proactiveTickRequest
        );
    }
}