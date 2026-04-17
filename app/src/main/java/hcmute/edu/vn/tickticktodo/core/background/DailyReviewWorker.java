package hcmute.edu.vn.tickticktodo.core.background;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import hcmute.edu.vn.tickticktodo.data.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.core.background.FloatingAssistantService;

public class DailyReviewWorker extends Worker {

    private static final String TAG = "DailyReviewWorker";
    private static final String WORK_NAME = "DailyReviewWork";
    private static final int REVIEW_HOUR = 21;

    public DailyReviewWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            TaskDao taskDao = TaskDatabase.getInstance(getApplicationContext()).taskDao();

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_MONTH, 1);
            long endOfDay = cal.getTimeInMillis();

            List<Task> completedTasks = taskDao.getCompletedTasksForDaySync(startOfDay, endOfDay);
            List<Task> incompleteTasks = taskDao.getIncompleteTasksForDaySync(startOfDay, endOfDay);

            String prompt = buildPrompt(completedTasks, incompleteTasks);
            String reviewText;
            try {
                reviewText = GeminiManager.getInstance().generateResponseBlocking(prompt, 45000L);
            } catch (Exception geminiError) {
                Log.e(TAG, "Gemini review failed", geminiError);
                reviewText = buildFallbackReview(completedTasks, incompleteTasks);
            }

            long[] unfinishedTaskIds = toTaskIdArray(incompleteTasks);
            Intent intent = new Intent(getApplicationContext(), FloatingAssistantService.class);
            intent.setAction(FloatingAssistantService.ACTION_SHOW_DAILY_REVIEW);
            intent.putExtra(FloatingAssistantService.EXTRA_DAILY_REVIEW_TEXT, reviewText);
            intent.putExtra(FloatingAssistantService.EXTRA_UNFINISHED_TASK_IDS, unfinishedTaskIds);
            ContextCompat.startForegroundService(getApplicationContext(), intent);

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Daily review failed", e);
            return Result.failure();
        }
    }

    public static void schedule(Context context) {
        long initialDelay = computeInitialDelayToHour(REVIEW_HOUR);

        Constraints constraints = new Constraints.Builder().build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(DailyReviewWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag("daily_review")
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    private static long computeInitialDelayToHour(int targetHour) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, targetHour);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        if (target.before(now)) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        return target.getTimeInMillis() - now.getTimeInMillis();
    }

    private String buildPrompt(List<Task> completedTasks, List<Task> incompleteTasks) {
        return "Nguoi dung da hoan thanh cac task: " + toTaskListText(completedTasks)
                + ". Cac task chua hoan thanh: " + toTaskListText(incompleteTasks)
                + ". Hay viet mot doan tom tat ngan (3-4 cau) bang giong dieu cua mot tro ly than thien: "
                + "Khen ngoi nhung gi ho da lam, dong vien nhe nhang ve nhung viec chua xong, "
                + "va de xuat chuyen viec chua xong sang ngay mai.";
    }

    private String toTaskListText(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "khong co";
        }

        List<String> titles = new ArrayList<>();
        int limit = Math.min(tasks.size(), 12);
        for (int i = 0; i < limit; i++) {
            Task task = tasks.get(i);
            String title = task.getTitle() == null ? "(khong ten)" : task.getTitle().trim();
            titles.add("- " + title);
        }
        return "\n" + String.join("\n", titles);
    }

    private String buildFallbackReview(List<Task> completedTasks, List<Task> incompleteTasks) {
        int completedCount = completedTasks == null ? 0 : completedTasks.size();
        int incompleteCount = incompleteTasks == null ? 0 : incompleteTasks.size();

        if (completedCount == 0 && incompleteCount == 0) {
            return "Hom nay ban da danh mot ngay kha nhe nhang. Ngay mai hay dat 1-2 muc tieu nho de bat dau day nang luong nhe.";
        }

        return String.format(Locale.getDefault(),
                "Hom nay ban da hoan thanh %d cong viec, rat dang khen. Van con %d cong viec dang do, ban co the chuyen sang ngay mai de tiep tuc. Thu uu tien 1 viec quan trong nhat vao buoi sang de tao da tien do.",
                completedCount,
                incompleteCount
        );
    }

    private long[] toTaskIdArray(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return new long[0];
        }

        long[] ids = new long[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            ids[i] = tasks.get(i).getId();
        }
        return ids;
    }
}
