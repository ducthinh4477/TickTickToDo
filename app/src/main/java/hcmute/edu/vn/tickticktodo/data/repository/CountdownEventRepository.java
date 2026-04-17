package hcmute.edu.vn.tickticktodo.data.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import hcmute.edu.vn.tickticktodo.data.dao.CountdownEventDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.CountdownEvent;

public class CountdownEventRepository {
    private CountdownEventDao eventDao;
    private LiveData<List<CountdownEvent>> allEvents;
    private ExecutorService executorService;

    public CountdownEventRepository(Application application) {
        TaskDatabase db = TaskDatabase.getInstance(application);
        eventDao = db.countdownEventDao();
        allEvents = eventDao.getAllEvents();
        executorService = Executors.newFixedThreadPool(1);
    }

    public LiveData<List<CountdownEvent>> getAllEvents() {
        return allEvents;
    }

    public void insert(CountdownEvent event) {
        executorService.execute(() -> eventDao.insert(event));
    }

    public void delete(CountdownEvent event) {
        executorService.execute(() -> eventDao.delete(event));
    }
}
