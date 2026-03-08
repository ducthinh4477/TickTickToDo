package hcmute.edu.vn.tickticktodo.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import hcmute.edu.vn.tickticktodo.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.dao.TodoListDao;
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
 */
@Database(entities = {Task.class, TodoList.class}, version = 4, exportSchema = false)
public abstract class TaskDatabase extends RoomDatabase {

    private static volatile TaskDatabase INSTANCE;
    private static final String DATABASE_NAME = "ticktick_todo_db";

    public abstract TaskDao taskDao();
    public abstract TodoListDao todoListDao();

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

    // ─── Singleton ───────────────────────────────────────────────────────────────
    public static TaskDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TaskDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TaskDatabase.class,
                            DATABASE_NAME
                    )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4) // Migration an toàn (giữ data)
                    .fallbackToDestructiveMigration()   // Fallback nếu schema không khớp
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
