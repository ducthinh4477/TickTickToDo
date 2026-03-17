package hcmute.edu.vn.tickticktodo.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.tickticktodo.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;

public class DatabaseCleanupWorker extends Worker {

    public DatabaseCleanupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        TaskDatabase db = TaskDatabase.getInstance(context);
        TaskDao dao = db.taskDao();

        // Calculate threshold: 30 days ago
        long thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);

        // Delete old tasks
        int deletedCount = dao.deleteOldCompletedTasks(thirtyDaysAgo);

        // Optimization (optional, but requested in prompt: "to optimize database")
        if (deletedCount > 0) {
            db.getOpenHelper().getWritableDatabase().execSQL("VACUUM");
        }

        return Result.success();
    }
}

