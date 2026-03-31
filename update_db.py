import re

task_file = "app/src/main/java/hcmute/edu/vn/tickticktodo/model/Task.java"
db_file = "app/src/main/java/hcmute/edu/vn/tickticktodo/database/TaskDatabase.java"

with open(task_file, "r", encoding="utf-8") as f:
    text = f.read()

fields = """
    // --- Attachments ---
    @ColumnInfo(name = "image_attachment")
    private String imageAttachment;
    
    @ColumnInfo(name = "voice_attachment")
    private String voiceAttachment;
    
    @ColumnInfo(name = "file_attachment")
    private String fileAttachment;
    
    public String getImageAttachment() { return imageAttachment; }
    public void setImageAttachment(String imageAttachment) { this.imageAttachment = imageAttachment; }
    
    public String getVoiceAttachment() { return voiceAttachment; }
    public void setVoiceAttachment(String voiceAttachment) { this.voiceAttachment = voiceAttachment; }
    
    public String getFileAttachment() { return fileAttachment; }
    public void setFileAttachment(String fileAttachment) { this.fileAttachment = fileAttachment; }
"""

text = text.replace("public int getRecurrence()", fields + "\n    public int getRecurrence()")

with open(task_file, "w", encoding="utf-8") as f:
    f.write(text)

with open(db_file, "r", encoding="utf-8") as f:
    db_text = f.read()

db_text = db_text.replace("version = 7", "version = 8")

mig_text = """
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN image_attachment TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN voice_attachment TEXT");
            database.execSQL("ALTER TABLE tasks ADD COLUMN file_attachment TEXT");
        }
    };
"""

db_text = db_text.replace("public static TaskDatabase getInstance", mig_text + "\n    public static TaskDatabase getInstance")
db_text = db_text.replace("MIGRATION_5_6, MIGRATION_6_7)", "MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)")

with open(db_file, "w", encoding="utf-8") as f:
    f.write(db_text)

print("DB files updated.")