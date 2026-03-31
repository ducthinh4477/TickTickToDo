import os

path = 'app/src/main/java/hcmute/edu/vn/tickticktodo/database/TaskDatabase.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# Update entities and version
text = text.replace('@Database(entities = {Task.class, TodoList.class, ActivityLog.class}, version = 8, exportSchema = false)', 
                    '@Database(entities = {Task.class, TodoList.class, ActivityLog.class, hcmute.edu.vn.tickticktodo.model.CountdownEvent.class}, version = 9, exportSchema = false)')

# Add DAO abstract method
dao_method = "public abstract hcmute.edu.vn.tickticktodo.dao.CountdownEventDao countdownEventDao();\n"
text = text.replace('public abstract ActivityLogDao activityLogDao();', 'public abstract ActivityLogDao activityLogDao();\n    ' + dao_method)

# Add migration
migration_8_9 = """
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `countdown_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `dateMillis` INTEGER NOT NULL)");
        }
    };
"""

text = text.replace('static final Migration MIGRATION_7_8 = new Migration(7, 8) {', migration_8_9 + '\n    static final Migration MIGRATION_7_8 = new Migration(7, 8) {')

# Add to fallbackToDestructiveMigration fallback or addMigrations
text = text.replace('.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)', 
                    '.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)')

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
print("Updated TaskDatabase")
