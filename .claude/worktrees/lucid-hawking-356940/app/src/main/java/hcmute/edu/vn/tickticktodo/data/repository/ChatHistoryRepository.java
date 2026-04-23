package hcmute.edu.vn.doinbot.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.doinbot.data.dao.ChatHistoryDao;
import hcmute.edu.vn.doinbot.data.database.TaskDatabase;
import hcmute.edu.vn.doinbot.model.ChatHistoryMessage;
import hcmute.edu.vn.doinbot.model.ChatSession;

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
