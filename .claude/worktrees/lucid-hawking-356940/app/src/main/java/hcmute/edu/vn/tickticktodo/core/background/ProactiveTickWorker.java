package hcmute.edu.vn.doinbot.core.background;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import hcmute.edu.vn.doinbot.agent.proactive.ProactiveEngine;

public class ProactiveTickWorker extends Worker {

    public ProactiveTickWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ProactiveEngine.getInstance(getApplicationContext()).evaluateNow("WORKER_PROACTIVE_TICK");
            return Result.success();
        } catch (Exception e) {
            return Result.failure();
        }
    }
}
