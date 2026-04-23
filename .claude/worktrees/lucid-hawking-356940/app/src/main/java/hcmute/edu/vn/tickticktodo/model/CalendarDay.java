package hcmute.edu.vn.doinbot.model;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Model đại diện cho một ô ngày trên lưới Calendar Month Grid.
 *
 * Mỗi lưới tháng có đúng 42 ô (6 hàng × 7 cột).
 * Các ô "ngoài tháng" (ngày cuối tháng trước / đầu tháng sau) được đánh dấu
 * bằng {@code isCurrentMonth = false} và hiển thị với màu xám.
 */
public class CalendarDay {

    private final int day;          // Ngày trong tháng (1–31)
    private final int month;        // Calendar.MONTH (0-based: 0=Jan … 11=Dec)
    private final int year;
    private final boolean isCurrentMonth; // true = thuộc tháng đang xem
    private final boolean isToday;        // true = hôm nay

    /** Danh sách task/event rơi vào ngày này (do Adapter gán sau khi query xong). */
    private List<Task> tasks = new ArrayList<>();

    public CalendarDay(int day, int month, int year,
                       boolean isCurrentMonth, boolean isToday) {
        this.day = day;
        this.month = month;
        this.year = year;
        this.isCurrentMonth = isCurrentMonth;
        this.isToday = isToday;
    }

    /** Convenience constructor cho ngày ngoài tháng (isToday luôn false). */
    public CalendarDay(int day, int month, int year, boolean isCurrentMonth) {
        this(day, month, year, isCurrentMonth, false);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    /**
     * Trả về timestamp đầu ngày (00:00:00.000) của ô này.
     * Dùng để so sánh với Task.dueDate.
     */
    public long getStartOfDayMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Trả về timestamp đầu ngày kế tiếp (exclusive).
     * Dùng làm endOfDay cho range query.
     */
    public long getEndOfDayMillis() {
        return getStartOfDayMillis() + 24L * 60 * 60 * 1000;
    }

    // ─── Getters / Setters ────────────────────────────────────────────────────────

    public int getDay()   { return day; }
    public int getMonth() { return month; }
    public int getYear()  { return year; }

    public boolean isCurrentMonth() { return isCurrentMonth; }
    public boolean isToday()        { return isToday; }

    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) {
        this.tasks = tasks != null ? tasks : new ArrayList<>();
    }
}
