package hcmute.edu.vn.doinbot.agent.integration.providers;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.doinbot.agent.integration.IntegrationProvider;
import hcmute.edu.vn.doinbot.agent.integration.model.ExternalDeadline;
import hcmute.edu.vn.doinbot.data.database.TaskDatabase;
import hcmute.edu.vn.doinbot.model.Task;

public class MoodleIntegrationProvider implements IntegrationProvider {

    private static final String SOURCE_MOODLE = "MOODLE";

    private final Context appContext;

    public MoodleIntegrationProvider(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public String getProviderName() {
        return "MOODLE_ICAL";
    }

    @Override
    public List<ExternalDeadline> getDeadlines(long fromMillis, long toMillis) {
        List<ExternalDeadline> deadlines = new ArrayList<>();

        List<Task> tasks = TaskDatabase.getInstance(appContext).taskDao().getAllTasksSync();
        if (tasks == null) {
            return deadlines;
        }

        for (Task task : tasks) {
            if (task == null || task.isCompleted()) {
                continue;
            }

            String source = task.getSource();
            if (source == null || !SOURCE_MOODLE.equals(source.trim().toUpperCase(Locale.ROOT))) {
                continue;
            }

            Long dueDate = task.getDueDate();
            if (dueDate == null || dueDate < fromMillis || dueDate > toMillis) {
                continue;
            }

            deadlines.add(new ExternalDeadline(
                    "moodle-task-" + task.getId(),
                    task.getTitle(),
                    dueDate,
                    getProviderName(),
                    task.getDescription() == null ? "" : task.getDescription()
            ));
        }

        return deadlines;
    }
}