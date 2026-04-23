package hcmute.edu.vn.doinbot.ui.moodle;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import hcmute.edu.vn.doinbot.ui.moodle.SchoolSyncWorker;

public class SchoolSyncReceiver extends BroadcastReceiver {
    private static final String TAG = "SchoolSyncReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm triggered! Waking up WorkManager for SchoolSync.");
        OneTimeWorkRequest bgRequest = new OneTimeWorkRequest.Builder(SchoolSyncWorker.class)
                .addTag("school_sync_bg") // Tag này sẽ báo Worker không được hiện thông báo "Không có bài mới"
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "SchoolSyncBgAlarm",
                ExistingWorkPolicy.REPLACE,
                bgRequest
        );

        // Schedule next alarm in 15 minutes to guarantee it keeps running reliably
        scheduleExact(context);
    }

    public static void scheduleExact(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, SchoolSyncReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Chạy lại sau mỗi 15 phút, dùng AlarmManager Exact để ép hệ điều hành không được ngủ sâu (Doze)
        long triggerTime = System.currentTimeMillis() + 15 * 60 * 1000L;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerTime, 60000L, pendingIntent);
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    public static void cancelExact(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, SchoolSyncReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}