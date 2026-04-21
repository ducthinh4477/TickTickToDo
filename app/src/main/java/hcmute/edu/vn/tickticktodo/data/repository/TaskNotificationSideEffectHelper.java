package hcmute.edu.vn.tickticktodo.data.repository;

import android.app.Application;

import hcmute.edu.vn.tickticktodo.model.Task;

final class TaskNotificationSideEffectHelper {

    private final Application application;

    TaskNotificationSideEffectHelper(Application application) {
        this.application = application;
    }

    void checkAndNotifyIfToday(Task task) {
        if (task.getDueDate() == null) return;

        java.util.Calendar todayStart = java.util.Calendar.getInstance();
        todayStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
        todayStart.set(java.util.Calendar.MINUTE, 0);
        todayStart.set(java.util.Calendar.SECOND, 0);
        todayStart.set(java.util.Calendar.MILLISECOND, 0);

        java.util.Calendar todayEnd = (java.util.Calendar) todayStart.clone();
        todayEnd.add(java.util.Calendar.DAY_OF_YEAR, 1);

        long due = task.getDueDate();
        if (due >= todayStart.getTimeInMillis() && due < todayEnd.getTimeInMillis()) {
            hcmute.edu.vn.tickticktodo.helper.NotificationHelper.showTaskNotification(
                    application,
                    "Bạn có công việc mới cho hôm nay",
                    task.getTitle(),
                    (int) task.getId()
            );
        }
    }
}