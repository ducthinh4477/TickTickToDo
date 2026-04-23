package hcmute.edu.vn.doinbot.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;

@Entity(
        tableName = "habit_logs",
        primaryKeys = {"habit_id", "date_millis"},
        foreignKeys = @ForeignKey(
                entity = Habit.class,
                parentColumns = "id",
                childColumns = "habit_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index(value = "habit_id"), @Index(value = "date_millis")}
)
public class HabitLog {

    @ColumnInfo(name = "habit_id")
    private long habitId;

    @ColumnInfo(name = "date_millis")
    private long dateMillis;

    @ColumnInfo(name = "is_completed")
    private boolean isCompleted;

    public HabitLog() {
    }

    @Ignore
    public HabitLog(long habitId, long dateMillis, boolean isCompleted) {
        this.habitId = habitId;
        this.dateMillis = dateMillis;
        this.isCompleted = isCompleted;
    }

    public long getHabitId() {
        return habitId;
    }

    public void setHabitId(long habitId) {
        this.habitId = habitId;
    }

    public long getDateMillis() {
        return dateMillis;
    }

    public void setDateMillis(long dateMillis) {
        this.dateMillis = dateMillis;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }
}
