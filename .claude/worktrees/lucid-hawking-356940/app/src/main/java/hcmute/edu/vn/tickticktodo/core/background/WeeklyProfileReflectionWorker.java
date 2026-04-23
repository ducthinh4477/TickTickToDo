package hcmute.edu.vn.doinbot.core.background;

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

import hcmute.edu.vn.doinbot.agent.profile.ProfileAgent;

public class WeeklyProfileReflectionWorker extends Worker {

    private static final String WORK_NAME = "WeeklyProfileReflectionWorker";

    public WeeklyProfileReflectionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ProfileAgent.getInstance(getApplicationContext()).runWeeklyReflection();
            return Result.success();
        } catch (Exception e) {
            return Result.failure();
        }
    }

    public static void schedule(Context context) {
        long initialDelay = computeInitialDelayToSunday2200();

        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WeeklyProfileReflectionWorker.class,
                7,
                TimeUnit.DAYS
        )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("profile_reflection_weekly")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    private static long computeInitialDelayToSunday2200() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        target.set(Calendar.HOUR_OF_DAY, 22);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 7);
        }

        return target.getTimeInMillis() - now.getTimeInMillis();
    }
}
