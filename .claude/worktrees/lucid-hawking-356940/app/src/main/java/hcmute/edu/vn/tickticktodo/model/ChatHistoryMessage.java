package hcmute.edu.vn.doinbot.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "chat_messages",
        foreignKeys = @ForeignKey(
                entity = ChatSession.class,
                parentColumns = "id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("session_id"),
                @Index("created_at")
        }
)
public class ChatHistoryMessage {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "session_id")
    public long sessionId;

    @ColumnInfo(name = "role")
    public String role;

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "source")
    public String source;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
