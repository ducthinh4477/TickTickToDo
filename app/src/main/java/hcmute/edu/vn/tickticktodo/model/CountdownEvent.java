package hcmute.edu.vn.tickticktodo.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "countdown_events")
public class CountdownEvent {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String title;
    private long dateMillis;

    @Ignore
    private boolean isPast; // just for UI calculation
    @Ignore
    private long daysDiff;

    public CountdownEvent(String title, long dateMillis) {
        this.title = title;
        this.dateMillis = dateMillis;
        calculateDays();
    }

    public void calculateDays() {
        long now = System.currentTimeMillis();
        long diff = dateMillis - now;

        // Convert to days
        long days = diff / (1000 * 60 * 60 * 24);
        if (diff < 0) {
            isPast = true;
            daysDiff = Math.abs(days);
        } else {
            isPast = false;
            daysDiff = days + 1; // +1 if > 0 to include today
        }
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public long getDateMillis() { return dateMillis; }
    public void setDateMillis(long dateMillis) { this.dateMillis = dateMillis; }

    public boolean isPast() { return isPast; }
    public long getDaysDiff() { return daysDiff; }
}