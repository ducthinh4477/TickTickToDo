package hcmute.edu.vn.doinbot.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import hcmute.edu.vn.doinbot.data.dao.HabitDao;
import hcmute.edu.vn.doinbot.data.dao.AgentDecisionLogDao;
import hcmute.edu.vn.doinbot.data.dao.ScheduleProposalDao;
import hcmute.edu.vn.doinbot.data.dao.SuggestionDao;
import hcmute.edu.vn.doinbot.data.dao.SubtaskDao;
import hcmute.edu.vn.doinbot.data.dao.TaskDao;
import hcmute.edu.vn.doinbot.data.dao.TodoListDao;
import hcmute.edu.vn.doinbot.data.dao.UserProfileDao;
import hcmute.edu.vn.doinbot.data.dao.ActivityLogDao;
import hcmute.edu.vn.doinbot.data.dao.ChatHistoryDao;
import hcmute.edu.vn.doinbot.model.ActivityLog;
import hcmute.edu.vn.doinbot.model.ChatHistoryMessage;
import hcmute.edu.vn.doinbot.model.ChatSession;
import hcmute.edu.vn.doinbot.model.Habit;
import hcmute.edu.vn.doinbot.model.HabitLog;
import hcmute.edu.vn.doinbot.model.Subtask;
import hcmute.edu.vn.doinbot.model.Task;
import hcmute.edu.vn.doinbot.model.TodoList;
import hcmute.edu.vn.doinbot.data.model.AgentDecisionLogEntity;
import hcmute.edu.vn.doinbot.data.model.SuggestionFeedbackEntity;
import hcmute.edu.vn.doinbot.data.model.SuggestionEntity;
import hcmute.edu.vn.doinbot.data.model.UserProfileEntity;
import hcmute.edu.vn.doinbot.data.model.ScheduleBlockEntity;
import hcmute.edu.vn.doinbot.data.model.ScheduleProposalEntity;

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
    hcmute.edu.vn.doinbot.model.CountdownEvent.class,
    Habit.class,
    HabitLog.class,
    ChatSession.class,
    ChatHistoryMessage.class,
    Subtask.class,
    SuggestionEntity.class,
    UserProfileEntity.class,
    SuggestionFeedbackEntity.class,
    ScheduleProposalEntity.class,
    ScheduleBlockEntity.class,
    AgentDecisionLogEntity.class
}, version = 17, exportSchema = false)
public abstract class TaskDatabase extends RoomDatabase {

    private static volatile TaskDatabase INSTANCE;
    private static final String DATABASE_NAME = "ticktick_todo_db";

    public abstract TaskDao taskDao();
    public abstract TodoListDao todoListDao();
    public abstract ActivityLogDao activityLogDao();
    public abstract hcmute.edu.vn.doinbot.data.dao.CountdownEventDao countdownEventDao();
    public abstract HabitDao habitDao();
    public abstract ChatHistoryDao chatHistoryDao();
    public abstract SubtaskDao subtaskDao();
    public abstract SuggestionDao suggestionDao();
    public abstract AgentDecisionLogDao agentDecisionLogDao();
    public abstract UserProfileDao userProfileDao();
    public abstract ScheduleProposalDao scheduleProposalDao();


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

    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtasks` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`task_id` INTEGER NOT NULL, " +
                            "`title` TEXT, " +
                            "`is_completed` INTEGER NOT NULL DEFAULT 0, " +
                            "`is_approved` INTEGER NOT NULL DEFAULT 0, " +
                            "`priority` INTEGER NOT NULL DEFAULT 0, " +
                            "`order_index` INTEGER NOT NULL DEFAULT 0, " +
                            "FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            );
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_task_id` ON `subtasks` (`task_id`)");
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `suggestions` (" +
                            "`id` TEXT NOT NULL, " +
                            "`type` TEXT, " +
                            "`title` TEXT, " +
                            "`reason` TEXT, " +
                            "`confidence` REAL NOT NULL, " +
                            "`priority_score` REAL NOT NULL, " +
                            "`created_at_millis` INTEGER NOT NULL, " +
                            "`expires_at_millis` INTEGER NOT NULL, " +
                            "`requires_confirmation` INTEGER NOT NULL, " +
                            "`status` TEXT, " +
                            "PRIMARY KEY(`id`))"
            );
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_suggestions_type_status` ON `suggestions` (`type`, `status`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_suggestions_status` ON `suggestions` (`status`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_suggestions_expires_at_millis` ON `suggestions` (`expires_at_millis`)");
        }
    };

    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_profile` (" +
                            "`id` INTEGER NOT NULL, " +
                            "`focus_start_hour` INTEGER NOT NULL, " +
                            "`focus_end_hour` INTEGER NOT NULL, " +
                            "`preferred_session_minutes` INTEGER NOT NULL, " +
                            "`chronotype_score` REAL NOT NULL, " +
                            "`avg_daily_completion_rate` REAL NOT NULL, " +
                            "`suggestion_acceptance_rate` REAL NOT NULL, " +
                            "`suggestion_dismiss_rate` REAL NOT NULL, " +
                            "`suggestion_apply_rate` REAL NOT NULL, " +
                            "`total_feedback_count` INTEGER NOT NULL, " +
                            "`last_daily_reflection_millis` INTEGER NOT NULL, " +
                            "`last_weekly_reflection_millis` INTEGER NOT NULL, " +
                            "`updated_at_millis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
            );

            database.execSQL(
                    "INSERT OR REPLACE INTO `user_profile` (" +
                            "`id`, `focus_start_hour`, `focus_end_hour`, `preferred_session_minutes`, `chronotype_score`, " +
                            "`avg_daily_completion_rate`, `suggestion_acceptance_rate`, `suggestion_dismiss_rate`, `suggestion_apply_rate`, " +
                            "`total_feedback_count`, `last_daily_reflection_millis`, `last_weekly_reflection_millis`, `updated_at_millis`) " +
                            "VALUES (1, 9, 12, 45, 0.5, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0)"
            );

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `suggestion_feedback` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`suggestion_id` TEXT, " +
                            "`suggestion_type` TEXT, " +
                            "`feedback_type` TEXT, " +
                            "`channel` TEXT, " +
                            "`priority_score` REAL NOT NULL, " +
                            "`confidence` REAL NOT NULL, " +
                            "`created_at_millis` INTEGER NOT NULL)"
            );

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_suggestion_feedback_suggestion_id` ON `suggestion_feedback` (`suggestion_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_suggestion_feedback_feedback_type` ON `suggestion_feedback` (`feedback_type`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_suggestion_feedback_created_at_millis` ON `suggestion_feedback` (`created_at_millis`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_suggestion_feedback_suggestion_type_feedback_type_created_at_millis` ON `suggestion_feedback` (`suggestion_type`, `feedback_type`, `created_at_millis`)");
        }
    };

    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_proposals` (" +
                            "`id` TEXT NOT NULL, " +
                            "`proposal_type` TEXT, " +
                            "`anchor_date` TEXT, " +
                            "`generated_at_millis` INTEGER NOT NULL, " +
                            "`window_start_millis` INTEGER NOT NULL, " +
                            "`window_end_millis` INTEGER NOT NULL, " +
                            "`conflict_report_json` TEXT, " +
                            "`options_json` TEXT, " +
                            "`status` TEXT, " +
                            "`applied_option_id` TEXT, " +
                            "`applied_at_millis` INTEGER NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY(`id`))"
            );

            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_proposals_proposal_type_generated_at_millis` " +
                            "ON `schedule_proposals` (`proposal_type`, `generated_at_millis`)"
            );

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_blocks` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`proposal_id` TEXT, " +
                            "`option_id` TEXT, " +
                            "`task_id` INTEGER, " +
                            "`task_title` TEXT, " +
                            "`start_millis` INTEGER NOT NULL, " +
                            "`end_millis` INTEGER NOT NULL, " +
                            "`block_type` TEXT, " +
                            "`note` TEXT, " +
                            "FOREIGN KEY(`proposal_id`) REFERENCES `schedule_proposals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            );

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_blocks_proposal_id` ON `schedule_blocks` (`proposal_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_blocks_option_id` ON `schedule_blocks` (`option_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_blocks_start_millis` ON `schedule_blocks` (`start_millis`)");
        }
    };

    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `agent_decision_logs` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`source` TEXT, " +
                            "`event_type` TEXT, " +
                            "`stage` TEXT, " +
                            "`decision` TEXT, " +
                            "`detail` TEXT, " +
                            "`suggestion_id` TEXT, " +
                            "`created_at_millis` INTEGER NOT NULL)"
            );
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_decision_logs_created_at_millis` ON `agent_decision_logs` (`created_at_millis`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_decision_logs_source_stage_created_at_millis` ON `agent_decision_logs` (`source`, `stage`, `created_at_millis`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_agent_decision_logs_suggestion_id` ON `agent_decision_logs` (`suggestion_id`)");
        }
    };

    // ─── Migration v16 → v17 ──────────────────────────────────────────────────────
    // Thêm cột reminder_hour và reminder_minute vào bảng habits cho tính năng nhắc nhở thói quen.
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE habits ADD COLUMN reminder_hour INTEGER NOT NULL DEFAULT -1"
            );
            database.execSQL(
                "ALTER TABLE habits ADD COLUMN reminder_minute INTEGER NOT NULL DEFAULT 0"
            );
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
                            MIGRATION_10_11,
                            MIGRATION_11_12,
                            MIGRATION_12_13,
                            MIGRATION_13_14,
                            MIGRATION_14_15,
                            MIGRATION_15_16,
                            MIGRATION_16_17
                        ) // Migration an toàn (giữ data)
                    .fallbackToDestructiveMigration()   // Fallback nếu schema không khớp
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
