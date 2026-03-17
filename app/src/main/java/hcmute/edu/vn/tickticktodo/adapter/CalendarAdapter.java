package hcmute.edu.vn.tickticktodo.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.CalendarDay;
import hcmute.edu.vn.tickticktodo.model.Task;

/**
 * Adapter cho RecyclerView lưới tháng (GridLayoutManager, spanCount=7).
 *
 * Mỗi ô hiển thị:
 *   • Số ngày (có vòng tròn highlight nếu là hôm nay / được chọn)
 *   • Tối đa 3 dòng event chip (màu theo priority), thêm "+N" nếu vượt quá 3
 *
 * Màu chữ số ngày:
 *   • Hôm nay   → trắng (nền tím accent)
 *   • Tháng khác→ xám nhạt
 *   • Thứ Bảy  → accent_primary (tím)
 *   • Chủ Nhật → priority_high (đỏ)
 *   • Bình thường → text_primary
 */
public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder> {

    /** Callback khi người dùng bấm vào một ô ngày. */
    public interface OnDayClickListener {
        void onDayClick(CalendarDay day);
    }

    private final List<CalendarDay> days = new ArrayList<>();
    private OnDayClickListener listener;
    private int selectedPosition = -1; // vị trí ô đang được chọn

    public CalendarAdapter(OnDayClickListener listener) {
        this.listener = listener;
    }

    // ─── Data update ─────────────────────────────────────────────────────────────

    public void submitList(List<CalendarDay> newDays) {
        days.clear();
        if (newDays != null) days.addAll(newDays);
        notifyDataSetChanged();
    }

    /** Cập nhật task cho từng ô và redraw. */
    public void updateTasks(List<CalendarDay> updatedDays) {
        if (updatedDays == null) return;
        days.clear();
        days.addAll(updatedDays);
        notifyDataSetChanged();
    }

    /** Đặt lại ô được chọn (dùng khi Activity nhảy về hôm nay). */
    public void setSelectedPosition(int position) {
        int old = selectedPosition;
        selectedPosition = position;
        if (old >= 0) notifyItemChanged(old);
        if (selectedPosition >= 0) notifyItemChanged(selectedPosition);
    }

    // ─── RecyclerView overrides ──────────────────────────────────────────────────

    @NonNull
    @Override
    public CalendarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_calendar_cell, parent, false);
        return new CalendarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarViewHolder holder, int position) {
        CalendarDay day = days.get(position);
        holder.bind(day, position, selectedPosition, listener);
    }

    @Override
    public int getItemCount() { return days.size(); }

    // ─── ViewHolder ───────────────────────────────────────────────────────────────

    static class CalendarViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvDayNumber;
        private final LinearLayout layoutEvents;

        CalendarViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayNumber  = itemView.findViewById(R.id.tv_day_number);
            layoutEvents = itemView.findViewById(R.id.layout_events);
        }

        void bind(CalendarDay day, int position, int selectedPos,
                  OnDayClickListener listener) {
            Context ctx = itemView.getContext();

            // ── Số ngày ──────────────────────────────────────────────────────────
            tvDayNumber.setText(String.valueOf(day.getDay()));

            // Xác định màu chữ + nền vòng tròn
            int dayOfWeek = getDayOfWeek(day); // 1=Sun … 7=Sat

            if (day.isToday()) {
                // Hôm nay: nền tròn tím, chữ trắng
                tvDayNumber.setBackground(
                        ContextCompat.getDrawable(ctx, R.drawable.bg_today_circle));
                tvDayNumber.setTextColor(Color.WHITE);

            } else if (position == selectedPos && day.isCurrentMonth()) {
                // Ngày đang chọn: viền tròn tím, chữ tím
                tvDayNumber.setBackground(
                        ContextCompat.getDrawable(ctx, R.drawable.bg_selected_day_circle));
                tvDayNumber.setTextColor(
                        ContextCompat.getColor(ctx, R.color.accent_primary));

            } else if (!day.isCurrentMonth()) {
                // Ngày ngoài tháng: xám nhạt, không nền
                tvDayNumber.setBackground(null);
                tvDayNumber.setTextColor(
                        ContextCompat.getColor(ctx, R.color.text_hint));

            } else if (dayOfWeek == 1) {
                // Chủ Nhật (Sunday): đỏ
                tvDayNumber.setBackground(null);
                tvDayNumber.setTextColor(
                        ContextCompat.getColor(ctx, R.color.priority_high));

            } else if (dayOfWeek == 7) {
                // Thứ Bảy: tím accent
                tvDayNumber.setBackground(null);
                tvDayNumber.setTextColor(
                        ContextCompat.getColor(ctx, R.color.accent_primary));

            } else {
                // Ngày thường trong tháng
                tvDayNumber.setBackground(null);
                tvDayNumber.setTextColor(
                        ContextCompat.getColor(ctx, R.color.text_primary));
            }

            // ── Event chips ───────────────────────────────────────────────────────
            layoutEvents.removeAllViews();
            List<Task> tasks = day.getTasks();

            int maxChips = 3; // Hiển thị tối đa 3 chip
            int count = tasks.size();

            for (int i = 0; i < Math.min(count, maxChips); i++) {
                Task task = tasks.get(i);
                TextView chip = buildEventChip(ctx, task);
                layoutEvents.addView(chip);
            }

            // "+N nữa" nếu có nhiều hơn maxChips tasks
            if (count > maxChips) {
                TextView more = buildMoreChip(ctx, count - maxChips);
                layoutEvents.addView(more);
            }

            // ── Click listener ────────────────────────────────────────────────────
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onDayClick(day);
            });
        }

        /**
         * Tạo một TextView "chip" sự kiện nhỏ từ Task.
         * Màu nền chip phụ thuộc vào priority:
         *   3=High → đỏ, 2=Medium → cam, 1=Low → tím, 0=None → xám
         */
        private TextView buildEventChip(Context ctx, Task task) {
            TextView tv = new TextView(ctx);
            int heightPx = dp2px(ctx, 13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, heightPx);
            lp.setMargins(0, 1, 0, 1);
            tv.setLayoutParams(lp);

            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            tv.setTextColor(Color.WHITE);
            tv.setMaxLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(dp2px(ctx, 3), 0, dp2px(ctx, 3), 0);
            tv.setGravity(android.view.Gravity.CENTER_VERTICAL);

            String title = task.isCompleted()
                    ? "\u2713 " + task.getTitle()   // ✓ prefix nếu đã hoàn thành
                    : task.getTitle();
            tv.setText(title);

            // Màu chip theo priority
            int chipColorRes = getChipColor(task.getPriority());
            tv.setBackgroundColor(ContextCompat.getColor(ctx, chipColorRes));

            // Bo góc nhẹ
            Drawable bg = ContextCompat.getDrawable(ctx, R.drawable.bg_event_chip);
            if (bg != null) {
                bg.mutate().setTint(ContextCompat.getColor(ctx, chipColorRes));
                tv.setBackground(bg);
            }

            return tv;
        }

        /** Chip "+ N nữa" */
        private TextView buildMoreChip(Context ctx, int extraCount) {
            TextView tv = new TextView(ctx);
            int heightPx = dp2px(ctx, 13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, heightPx);
            lp.setMargins(0, 1, 0, 1);
            tv.setLayoutParams(lp);

            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            tv.setMaxLines(1);
            tv.setPadding(dp2px(ctx, 3), 0, dp2px(ctx, 3), 0);
            tv.setText("+" + extraCount + " nữa");
            tv.setBackground(null);
            return tv;
        }

        /** Trả về resource màu chip theo priority. */
        private int getChipColor(int priority) {
            switch (priority) {
                case 3: return R.color.priority_high;
                case 2: return R.color.priority_medium;
                case 1: return R.color.priority_low;
                default: return R.color.priority_none;
            }
        }

        /**
         * Tính thứ trong tuần của ô ngày (1=Sun, 2=Mon … 7=Sat) theo Calendar.
         * Dùng để tô màu Thứ Bảy/Chủ Nhật.
         */
        private int getDayOfWeek(CalendarDay day) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.YEAR, day.getYear());
            cal.set(java.util.Calendar.MONTH, day.getMonth());
            cal.set(java.util.Calendar.DAY_OF_MONTH, day.getDay());
            return cal.get(java.util.Calendar.DAY_OF_WEEK);
        }

        private static int dp2px(Context ctx, int dp) {
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, dp,
                    ctx.getResources().getDisplayMetrics()));
        }
    }
}
