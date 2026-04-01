package hcmute.edu.vn.tickticktodo.widget;

import android.app.Application;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import hcmute.edu.vn.tickticktodo.MainActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.repository.TaskRepository;
import hcmute.edu.vn.tickticktodo.ui.TaskDetailActivity;

public class TodoWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_TASK = "hcmute.edu.vn.tickticktodo.action.WIDGET_TOGGLE_TASK";
    public static final String ACTION_OPEN_TASK = "hcmute.edu.vn.tickticktodo.action.WIDGET_OPEN_TASK";
    public static final String ACTION_REFRESH = "hcmute.edu.vn.tickticktodo.action.WIDGET_REFRESH";
    public static final String EXTRA_TASK_ID = "extra_task_id";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_TOGGLE_TASK.equals(action)) {
            handleToggleTask(context, intent);
            return;
        }

        if (ACTION_OPEN_TASK.equals(action)) {
            handleOpenTask(context, intent);
            return;
        }

        if (ACTION_REFRESH.equals(action)) {
            refreshAllWidgets(context);
            return;
        }

        super.onReceive(context, intent);
    }

    private void handleToggleTask(Context context, Intent intent) {
        long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L);
        if (taskId <= 0) {
            return;
        }

        Application app = (Application) context.getApplicationContext();
        TaskRepository repository = new TaskRepository(app);
        repository.markTaskAsCompletedWithDate(taskId, true, System.currentTimeMillis());

        refreshAllWidgets(context);

        // Repository updates on its own background executor; refresh again after a short delay.
        new Handler(Looper.getMainLooper()).postDelayed(() -> refreshAllWidgets(context), 450);
    }

    private void handleOpenTask(Context context, Intent intent) {
        long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L);

        Intent openIntent;
        if (taskId > 0) {
            openIntent = TaskDetailActivity.newIntent(context, taskId);
        } else {
            openIntent = new Intent(context, MainActivity.class);
        }

        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(openIntent);
    }

    private void updateSingleWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_todo_list);

        Intent serviceIntent = new Intent(context, TodoWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_list_view, serviceIntent);
        views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view);

        Intent itemTemplateIntent = new Intent(context, TodoWidgetProvider.class);
        PendingIntent itemTemplatePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                itemTemplateIntent,
                getMutablePendingIntentFlags()
        );
        views.setPendingIntentTemplate(R.id.widget_list_view, itemTemplatePendingIntent);

        Intent openMainIntent = new Intent(context, MainActivity.class);
        openMainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openMainPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 10000,
                openMainIntent,
                getImmutablePendingIntentFlags()
        );
        views.setOnClickPendingIntent(R.id.widget_header_title, openMainPendingIntent);
        views.setOnClickPendingIntent(R.id.widget_empty_view, openMainPendingIntent);

        Intent refreshIntent = new Intent(context, TodoWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 20000,
                refreshIntent,
                getImmutablePendingIntentFlags()
        );
        views.setOnClickPendingIntent(R.id.widget_header_refresh, refreshPendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view);
    }

    private void refreshAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, TodoWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(provider);

        if (appWidgetIds == null || appWidgetIds.length == 0) {
            return;
        }

        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view);
        for (int appWidgetId : appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private int getMutablePendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return flags;
    }

    private int getImmutablePendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}