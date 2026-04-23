package hcmute.edu.vn.doinbot.ui.countdown;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import hcmute.edu.vn.doinbot.R;
import hcmute.edu.vn.doinbot.model.CountdownEvent;

public class CountdownEventAdapter extends RecyclerView.Adapter<CountdownEventAdapter.ViewHolder> {

    private List<CountdownEvent> events;

    public CountdownEventAdapter(List<CountdownEvent> events) {
        this.events = events;
    }

    public List<CountdownEvent> getEvents() { return events; }

    public void setEvents(List<CountdownEvent> events) {
        this.events = events;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event_countdown, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CountdownEvent event = events.get(position);
        event.calculateDays();

        holder.tvTitle.setText(event.getTitle());
        holder.tvDays.setText(String.valueOf(event.getDaysDiff()));
        
        if (event.isPast()) {
            holder.tvType.setText("Số ngày đã qua");
            holder.tvDays.setTextColor(Color.parseColor("#4CAF50")); // Green
            holder.tvType.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            holder.tvType.setText("Số ngày đến");
            holder.tvDays.setTextColor(Color.parseColor("#8C9EFF")); // Purple
            holder.tvType.setTextColor(Color.parseColor("#8C9EFF"));
        }
    }

    @Override
    public int getItemCount() {
        return events != null ? events.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDays, tvType;
        ImageView ivIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
            tvDays = itemView.findViewById(R.id.tv_event_days);
            tvType = itemView.findViewById(R.id.tv_event_type);
            ivIcon = itemView.findViewById(R.id.iv_event_icon);
        }
    }
}