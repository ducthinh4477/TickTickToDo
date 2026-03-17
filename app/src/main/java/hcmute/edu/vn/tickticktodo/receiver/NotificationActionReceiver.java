package hcmute.edu.vn.tickticktodo.receiver;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import hcmute.edu.vn.tickticktodo.helper.ReminderScheduler;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.repository.TaskRepository;

public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_DONE = "hcmute.edu.vn.tickticktodo.ACTION_DONE";
    public static final String ACTION_SNOOZE = "hcmute.edu.vn.tickticktodo.ACTION_SNOOZE";
    public static final String EXTRA_TASK_ID = "extra_task_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1);

        if (taskId == -1) return;

        // Dismiss notification
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel((int) taskId);
        }

        TaskRepository repository = new TaskRepository((android.app.Application) context.getApplicationContext());

        if (ACTION_DONE.equals(action)) {
            // Mark as done
            repository.markTaskAsCompletedWithDate(taskId, true, System.currentTimeMillis());
            Toast.makeText(context, "Task completed", Toast.LENGTH_SHORT).show();
        } else if (ACTION_SNOOZE.equals(action)) {
            // Snooze for 15 minutes
            // We need to fetch the task first to reschedule properly, but ReminderScheduler needs a Task object with ID and DueDate.
            // Since we don't have the task object here easily without querying DB (which is async),
            // we can cheat a bit for snooze by just creating a dummy task with the right ID and new time.
            // However, ReminderScheduler might use other fields. Let's check ReminderScheduler.scheduleReminder.
            // It uses id and dueDate. So a dummy task is fine.

            long snoozeTime = System.currentTimeMillis() + 15 * 60 * 1000;
            Task task = new Task();
            task.setId(taskId);
            task.setDueDate(snoozeTime);

            ReminderScheduler.scheduleReminder(context, task);
            Toast.makeText(context, "Snoozed for 15 minutes", Toast.LENGTH_SHORT).show();
        }
    }
}

