package hcmute.edu.vn.tickticktodo.ui.chat;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import hcmute.edu.vn.tickticktodo.model.ChatSession;
import hcmute.edu.vn.tickticktodo.data.repository.ChatHistoryRepository;

public class ChatHistoryViewModel extends AndroidViewModel {

    private final ChatHistoryRepository repository;
    private final LiveData<List<ChatSession>> sessions;

    public ChatHistoryViewModel(@NonNull Application application) {
        super(application);
        repository = new ChatHistoryRepository(application);
        sessions = repository.getAllSessions();
    }

    public LiveData<List<ChatSession>> getSessions() {
        return sessions;
    }

    public void clearAllHistory() {
        repository.clearAllHistory();
    }
}
