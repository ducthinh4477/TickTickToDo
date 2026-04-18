package hcmute.edu.vn.tickticktodo.core.background;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.tickticktodo.agent.profile.ProfileAgent;

public class DailyProfileReflectionWorker extends Worker {

    private static final String WORK_NAME = "DailyProfileReflectionWorker";
    private static final int TARGET_HOUR = 23;
    private static final int TARGET_MINUTE = 30;

    public DailyProfileReflectionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ProfileAgent.getInstance(getApplicationContext()).runDailyReflection();
            return Result.success();
        } catch (Exception e) {
            return Result.failure();
        }
    }

    public static void schedule(Context context) {
        long initialDelay = computeInitialDelay();

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DailyProfileReflectionWorker.class,
                24,
                TimeUnit.HOURS
        )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("profile_reflection_daily")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    private static long computeInitialDelay() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, TARGET_HOUR);
        target.set(Calendar.MINUTE, TARGET_MINUTE);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        return target.getTimeInMillis() - now.getTimeInMillis();
    }
}
