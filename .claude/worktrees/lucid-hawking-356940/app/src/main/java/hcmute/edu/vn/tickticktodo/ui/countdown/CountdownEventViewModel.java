package hcmute.edu.vn.doinbot.ui.countdown;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;
import hcmute.edu.vn.doinbot.model.CountdownEvent;
import hcmute.edu.vn.doinbot.data.repository.CountdownEventRepository;

public class CountdownEventViewModel extends AndroidViewModel {
    private CountdownEventRepository repository;
    private LiveData<List<CountdownEvent>> allEvents;

    public CountdownEventViewModel(@NonNull Application application) {
        super(application);
        repository = new CountdownEventRepository(application);
        allEvents = repository.getAllEvents();
    }

    public LiveData<List<CountdownEvent>> getAllEvents() {
        return allEvents;
    }

    public void insert(CountdownEvent event) {
        repository.insert(event);
    }

    public void delete(CountdownEvent event) {
        repository.delete(event);
    }
}
