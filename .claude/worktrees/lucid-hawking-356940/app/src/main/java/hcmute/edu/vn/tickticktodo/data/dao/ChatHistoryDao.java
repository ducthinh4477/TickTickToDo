package hcmute.edu.vn.doinbot.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import hcmute.edu.vn.doinbot.model.ChatHistoryMessage;
import hcmute.edu.vn.doinbot.model.ChatSession;

@Dao
public interface ChatHistoryDao {

    @Insert
    long insertSession(ChatSession session);

    @Insert
    long insertMessage(ChatHistoryMessage message);

    @Query("SELECT * FROM chat_sessions ORDER BY updated_at DESC")
    LiveData<List<ChatSession>> getAllSessions();

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at ASC, id ASC")
    LiveData<List<ChatHistoryMessage>> getMessagesForSession(long sessionId);

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at ASC, id ASC")
    List<ChatHistoryMessage> getMessagesForSessionSync(long sessionId);

    @Query("SELECT * FROM chat_sessions ORDER BY updated_at DESC LIMIT 1")
    ChatSession getLatestSessionSync();

    @Query("SELECT * FROM chat_sessions WHERE source = :source ORDER BY updated_at DESC LIMIT 1")
    ChatSession getLatestSessionBySourceSync(String source);

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    ChatSession getSessionByIdSync(long sessionId);

    @Query("UPDATE chat_sessions SET updated_at = :updatedAt, last_message = :lastMessage WHERE id = :sessionId")
    void updateSessionAfterMessage(long sessionId, long updatedAt, String lastMessage);

    @Query("UPDATE chat_sessions SET title = :title WHERE id = :sessionId AND (title IS NULL OR title = '')")
    void updateSessionTitleIfEmpty(long sessionId, String title);

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    void deleteSessionById(long sessionId);

    @Query("DELETE FROM chat_messages")
    void deleteAllMessages();

    @Query("DELETE FROM chat_sessions")
    void deleteAllSessions();
}
