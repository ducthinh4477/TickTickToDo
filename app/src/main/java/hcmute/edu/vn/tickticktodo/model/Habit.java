package hcmute.edu.vn.tickticktodo.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "habits")
public class Habit {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "icon")
    private String icon;

    /** Hour of day for daily reminder (0–23). -1 = no reminder. */
    @ColumnInfo(name = "reminder_hour")
    private int reminderHour = -1;

    /** Minute for daily reminder (0–59). */
    @ColumnInfo(name = "reminder_minute")
    private int reminderMinute = 0;

    public Habit() {
    }

    @Ignore
    public Habit(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public int getReminderHour() { return reminderHour; }
    public void setReminderHour(int reminderHour) { this.reminderHour = reminderHour; }

    public int getReminderMinute() { return reminderMinute; }
    public void setReminderMinute(int reminderMinute) { this.reminderMinute = reminderMinute; }

    /** Returns true if this habit has a daily reminder set. */
    public boolean hasReminder() { return reminderHour >= 0; }
}
