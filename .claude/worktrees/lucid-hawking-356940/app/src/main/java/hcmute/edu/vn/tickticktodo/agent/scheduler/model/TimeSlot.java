package hcmute.edu.vn.doinbot.agent.scheduler.model;

public class TimeSlot {

    private final long startMillis;
    private final long endMillis;
    private final boolean isFree;
    private final String source;
    private final String placeHint;

    public TimeSlot(long startMillis,
                    long endMillis,
                    boolean isFree,
                    String source,
                    String placeHint) {
        this.startMillis = Math.min(startMillis, endMillis);
        this.endMillis = Math.max(startMillis, endMillis);
        this.isFree = isFree;
        this.source = source == null ? "unknown" : source;
        this.placeHint = placeHint == null ? "" : placeHint;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public boolean isFree() {
        return isFree;
    }

    public String getSource() {
        return source;
    }

    public String getPlaceHint() {
        return placeHint;
    }

    public int getDurationMinutes() {
        long duration = Math.max(0L, endMillis - startMillis);
        return (int) Math.max(0L, duration / 60000L);
    }

    public boolean overlaps(TimeSlot other) {
        if (other == null) {
            return false;
        }
        return startMillis < other.endMillis && other.startMillis < endMillis;
    }
}
