package hcmute.edu.vn.doinbot.model;

public class HabitHeatmapCell {

    private final long dateMillis;
    private final boolean completed;

    public HabitHeatmapCell(long dateMillis, boolean completed) {
        this.dateMillis = dateMillis;
        this.completed = completed;
    }

    public long getDateMillis() {
        return dateMillis;
    }

    public boolean isCompleted() {
        return completed;
    }
}
