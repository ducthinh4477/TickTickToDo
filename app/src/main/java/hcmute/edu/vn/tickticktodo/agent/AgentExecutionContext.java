package hcmute.edu.vn.tickticktodo.agent;

import android.app.Application;

import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.data.repository.TaskRepository;

public class AgentExecutionContext {

    private final Application application;
    private final TaskDatabase database;
    private final TaskRepository taskRepository;
    private final long nowMillis;

    public AgentExecutionContext(Application application,
                                 TaskDatabase database,
                                 TaskRepository taskRepository,
                                 long nowMillis) {
        this.application = application;
        this.database = database;
        this.taskRepository = taskRepository;
        this.nowMillis = nowMillis;
    }

    public static AgentExecutionContext create(Application application) {
        TaskDatabase database = TaskDatabase.getInstance(application);
        TaskRepository repository = new TaskRepository(application);
        return new AgentExecutionContext(application, database, repository, System.currentTimeMillis());
    }

    public Application getApplication() {
        return application;
    }

    public TaskDatabase getDatabase() {
        return database;
    }

    public TaskRepository getTaskRepository() {
        return taskRepository;
    }

    public long getNowMillis() {
        return nowMillis;
    }
}
