package hcmute.edu.vn.tickticktodo.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.data.dao.ChatHistoryDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.ChatHistoryMessage;
import hcmute.edu.vn.tickticktodo.model.ChatSession;

public class ChatHistoryRepository {

    private final ChatHistoryDao chatHistoryDao;
    private final ExecutorService executor;

    public ChatHistoryRepository(Application application) {
        TaskDatabase db = TaskDatabase.getInstance(application);
        chatHistoryDao = db.chatHistoryDao();
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<ChatSession>> getAllSessions() {
        return chatHistoryDao.getAllSessions();
    }

    public LiveData<List<ChatHistoryMessage>> getMessagesForSession(long sessionId) {
        return chatHistoryDao.getMessagesForSession(sessionId);
    }

    public void clearAllHistory() {
        executor.execute(() -> {
            chatHistoryDao.deleteAllMessages();
            chatHistoryDao.deleteAllSessions();
        });
    }
}
