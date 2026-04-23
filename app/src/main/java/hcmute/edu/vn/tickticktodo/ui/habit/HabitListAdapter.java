package hcmute.edu.vn.tickticktodo.ui.habit;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.Habit;

public class HabitListAdapter extends RecyclerView.Adapter<HabitListAdapter.HabitViewHolder> {

    public interface HabitActionListener {
        void onHabitSelected(Habit habit);
        void onHabitCheckIn(Habit habit);
        /** Triggered by long-press → show edit reminder dialog. */
        void onHabitEdit(Habit habit);
    }

    private final List<Habit> habits = new ArrayList<>();
    private final HabitActionListener listener;
    private long selectedHabitId = -1L;

    public HabitListAdapter(HabitActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Habit> newList) {
        habits.clear();
        if (newList != null) {
            habits.addAll(newList);
        }
        notifyDataSetChanged();
    }

    public void setSelectedHabitId(long selectedHabitId) {
        this.selectedHabitId = selectedHabitId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HabitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_habit_chip, parent, false);
        return new HabitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HabitViewHolder holder, int position) {
        Habit habit = habits.get(position);
        holder.bind(habit, selectedHabitId == habit.getId(), listener);
    }

    @Override
    public int getItemCount() {
        return habits.size();
    }

    static class HabitViewHolder extends RecyclerView.ViewHolder {

        private final View root;
        private final ImageView icon;
        private final TextView name;
        private final TextView reminderTime;
        private final ImageButton checkIn;

        HabitViewHolder(@NonNull View itemView) {
            super(itemView);
            root         = itemView.findViewById(R.id.layout_habit_item_root);
            icon         = itemView.findViewById(R.id.iv_habit_icon);
            name         = itemView.findViewById(R.id.tv_habit_name);
            reminderTime = itemView.findViewById(R.id.tv_habit_reminder_time);
            checkIn      = itemView.findViewById(R.id.btn_habit_check_in);
        }

        void bind(Habit habit, boolean selected, HabitActionListener listener) {
            name.setText(habit.getName());

            // Resolve icon drawable
            int iconRes = itemView.getContext().getResources().getIdentifier(
                    habit.getIcon() == null ? "" : habit.getIcon(),
                    "drawable",
                    itemView.getContext().getPackageName()
            );
            if (iconRes == 0) iconRes = R.drawable.ic_health;
            icon.setImageResource(iconRes);

            // Show reminder time badge when a reminder is configured
            if (habit.getReminderHour() >= 0 && reminderTime != null) {
                reminderTime.setText(String.format(
                        Locale.getDefault(), "⏰ %02d:%02d",
                        habit.getReminderHour(), habit.getReminderMinute()));
                reminderTime.setVisibility(View.VISIBLE);
            } else if (reminderTime != null) {
                reminderTime.setVisibility(View.GONE);
            }

            root.setSelected(selected);

            // Single tap → select habit / show heatmap
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onHabitSelected(habit);
            });

            // Long-press → open edit reminder dialog
            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onHabitEdit(habit);
                return true;
            });

            // Check-in button
            checkIn.setOnClickListener(v -> {
                if (listener != null) listener.onHabitCheckIn(habit);
            });
        }
    }
}
