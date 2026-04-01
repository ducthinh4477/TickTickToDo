package hcmute.edu.vn.tickticktodo.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.Task;

public class TodoWidgetFactory implements RemoteViewsService.RemoteViewsFactory {

    private static final int MAX_VISIBLE_TASKS = 12;

    private final Context context;
    private final int appWidgetId;
    private final List<Task> tasks = new ArrayList<>();
    private TaskDao taskDao;

    public TodoWidgetFactory(Context context, Intent intent) {
        this.context = context;
        this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    @Override
    public void onCreate() {
        taskDao = TaskDatabase.getInstance(context).taskDao();
    }

    @Override
    public void onDataSetChanged() {
        if (taskDao == null) {
            return;
        }

        long startOfDay = getStartOfDayMillis();
        long endOfDay = getEndOfDayMillis();

        List<Task> latest = taskDao.getIncompleteTasksForDaySync(startOfDay, endOfDay);
        tasks.clear();
        if (latest == null || latest.isEmpty()) {
            return;
        }

        int limit = Math.min(MAX_VISIBLE_TASKS, latest.size());
        for (int i = 0; i < limit; i++) {
            tasks.add(latest.get(i));
        }
    }

    @Override
    public void onDestroy() {
        tasks.clear();
    }

    @Override
    public int getCount() {
        return tasks.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= tasks.size()) {
            return null;
        }

        Task task = tasks.get(position);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_todo_item);

        String title = task.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = context.getString(R.string.widget_untitled_task);
        }

        views.setTextViewText(R.id.widget_item_title, withPriorityPrefix(task.getPriority(), title));
        views.setTextViewText(R.id.widget_item_due, buildDueText(task.getDueDate()));
        views.setImageViewResource(R.id.widget_item_check, R.drawable.ic_checkbox_square_unchecked);

        Intent checkIntent = new Intent();
        checkIntent.setAction(TodoWidgetProvider.ACTION_TOGGLE_TASK);
        checkIntent.putExtra(TodoWidgetProvider.EXTRA_TASK_ID, task.getId());
        checkIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickFillInIntent(R.id.widget_item_check, checkIntent);

        Intent openIntent = new Intent();
        openIntent.setAction(TodoWidgetProvider.ACTION_OPEN_TASK);
        openIntent.putExtra(TodoWidgetProvider.EXTRA_TASK_ID, task.getId());
        openIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickFillInIntent(R.id.widget_item_root, openIntent);

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= tasks.size()) {
            return position;
        }
        return tasks.get(position).getId();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private long getStartOfDayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }

    private String buildDueText(Long dueDate) {
        if (dueDate == null) {
            return context.getString(R.string.widget_due_no_time);
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dueDate);
        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE)
        );
    }

    private String withPriorityPrefix(int priority, String title) {
        switch (priority) {
            case 3:
                return "[High] " + title;
            case 2:
                return "[Med] " + title;
            case 1:
                return "[Low] " + title;
            case 0:
            default:
                return title;
        }
    }
}