package hcmute.edu.vn.doinbot.core.background;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import hcmute.edu.vn.doinbot.agent.context.ContextAgent;

public class ContextRefreshWorker extends Worker {

    public ContextRefreshWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ContextAgent.getInstance(getApplicationContext()).refreshSnapshot("WORKER_CONTEXT_REFRESH");
            return Result.success();
        } catch (Exception e) {
            return Result.failure();
        }
    }
}
