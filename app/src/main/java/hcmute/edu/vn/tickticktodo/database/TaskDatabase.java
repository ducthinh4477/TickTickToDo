package hcmute.edu.vn.tickticktodo.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import hcmute.edu.vn.tickticktodo.dao.HabitDao;
import hcmute.edu.vn.tickticktodo.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.dao.TodoListDao;
import hcmute.edu.vn.tickticktodo.dao.ActivityLogDao;
import hcmute.edu.vn.tickticktodo.dao.ChatHistoryDao;
import hcmute.edu.vn.tickticktodo.model.ActivityLog;
import hcmute.edu.vn.tickticktodo.model.ChatHistoryMessage;
import hcmute.edu.vn.tickticktodo.model.ChatSession;
import hcmute.edu.vn.tickticktodo.model.Habit;
import hcmute.edu.vn.tickticktodo.model.HabitLog;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.model.TodoList;

/**
 * Room Database chính của ứng dụng.
 * Sử dụng Singleton pattern để đảm bảo chỉ có một instance duy nhất.
 *
 * Lịch sử version:
 *   v1 → v2: Thêm bảng todo_lists
 *   v2 → v3: Thêm cột icon_res_id vào bảng todo_lists (DEFAULT 0)
 *   v3 → v4: Thêm cột order_index, completed_date vào bảng tasks
 *   v4 → v5: Thêm cột location, duration, recurrence vào bảng tasks (Calendar event)
 *   v5 → v6: Thêm cột source vào bảng tasks (chứa nguồn như 'Moodle')
 *   v6 → v7: Thêm bảng activity_logs
 */
@Database(entities = {
    Task.class,
    TodoList.class,
    ActivityLog.class,
    hcmute.edu.vn.tickticktodo.model.CountdownEvent.class,
    Habit.class,
    HabitLog.class,
    ChatSession.class,
    ChatHistoryMessage.class
}, version = 11, exportSchema = false)
public abstract class TaskDatabase extends RoomDatabase {

    private static volatile TaskDatabase INSTANCE;
    private static final String DATABASE_NAME = "ticktick_todo_db";

    public abstract TaskDao taskDao();
    public abstract TodoListDao todoListDao();
    public abstract ActivityLogDao activityLogDao();
    public abstract hcmute.edu.vn.tickticktodo.dao.CountdownEventDao countdownEventDao();
    public abstract HabitDao habitDao();
    public abstract ChatHistoryDao chatHistoryDao();


    // ─── Migration v2 → v3 ───────────────────────────────────────────────────────
    // Thêm cột icon_res_id (INTEGER, default 0) vào bảng todo_lists.
    // Các hàng cũ sẽ tự động có giá trị 0 (= không có icon tuỳ chọn).
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE todo_lists ADD COLUMN icon_res_id INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    // ─── Migration v3 → v4 ───────────────────────────────────────────────────────
    // Thêm cột order_index (INTEGER, default 0) và completed_date (INTEGER, nullable)
    // vào bảng tasks cho tính năng Sort Custom và Statistics.
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE tasks ADD COLUMN order_index INTEGER NOT NULL DEFAULT 0"
            );
            database.execSQL(
                "ALTER TABLE tasks ADD COLUMN completed_date INTEGER"
            );
        }
    };

    // ─── Migration v4 → v5 ───────────────────────────────────────────────────────
    // Thêm 3 cột mới cho tính năng Calendar Event:
    //   location  – địa điểm (TEXT, nullable)
    //   duration  – thời lượng tính bằng phút (INTEGER, default 0)
    //   recurrence – kiểu lặp lại: 0=none, 1=weekly, 2=monthly (INTEGER, default 0)
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE tasks ADD COLUMN location TEXT"
            );
            database.execSQL(
                "ALTER TABLE tasks ADD COLUMN duration INTEGER NOT NULL DEFAULT 0"
            );
            database.execSQL(
                "ALTER TABLE tasks ADD COLUMN recurrence INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    // ─── Migration v5 → v6 ───────────────────────────────────────────────────────
    // Thêm cột source để xác định nguồn của task (vd: 'Moodle')
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE tasks ADD COLUMN source TEXT"
            );
        }
    };

    // ─── Migration v6 → v7 ───────────────────────────────────────────────────────
    // Thêm bảng activity_logs để lưu nhật ký
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `activity_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `action` TEXT, `timestamp` INTEGER NOT NULL, `task_title` TEXT)"
            );
        }
    };

    // ─── Singleton ───────────────────────────────────────────────────────────────
    
    
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `countdown_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `dateMillis` INTEGER NOT NULL)");
        }
    };

    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `icon` TEXT)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `habit_logs` (`habit_id` INTEGER NOT NULL, `date_millis` INTEGER NOT NULL, `is_completed` INTEGER NOT NULL, PRIMARY KEY(`habit_id`, `date_millis`), FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_habit_id` ON `habit_logs` (`habit_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_date_millis` ON `habit_logs` (`date_millis`)");
        }
    };

    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT, " +
                            "`source` TEXT, " +
                            "`last_message` TEXT, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "`updated_at` INTEGER NOT NULL)"
            );

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`session_id` INTEGER NOT NULL, " +
                            "`role` TEXT, " +
                            "`content` TEXT, " +
                            "`source` TEXT, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`session_id`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            );

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_session_id` ON `chat_messages` (`session_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_created_at` ON `chat_messages` (`created_at`)");
        }
    };

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN image_attachment TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN voice_attachment TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN file_attachment TEXT");
        }
    };

    public static TaskDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TaskDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TaskDatabase.class,
                            DATABASE_NAME
                    )
                        .addMigrations(
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                            MIGRATION_7_8,
                            MIGRATION_8_9,
                            MIGRATION_9_10,
                            MIGRATION_10_11
                        ) // Migration an toàn (giữ data)
                    .fallbackToDestructiveMigration()   // Fallback nếu schema không khớp
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
