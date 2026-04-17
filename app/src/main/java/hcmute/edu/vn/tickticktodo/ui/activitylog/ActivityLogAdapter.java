package hcmute.edu.vn.tickticktodo.ui.activitylog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.ActivityLog;

public class ActivityLogAdapter extends ListAdapter<ActivityLog, ActivityLogAdapter.LogViewHolder> {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public ActivityLogAdapter() {
        super(new DiffUtil.ItemCallback<ActivityLog>() {
            @Override
            public boolean areItemsTheSame(@NonNull ActivityLog oldItem, @NonNull ActivityLog newItem) {
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull ActivityLog oldItem, @NonNull ActivityLog newItem) {
                return oldItem.id == newItem.id &&
                       Objects.equals(oldItem.action, newItem.action) &&
                       oldItem.timestamp == newItem.timestamp &&
                       Objects.equals(oldItem.taskTitle, newItem.taskTitle);
            }
        });
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        ActivityLog log = getItem(position);
        ActivityLog previousLog = position > 0 ? getItem(position - 1) : null;
        holder.bind(log, previousLog);
    }

    class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAction;
        private final TextView tvTaskTitle;
        private final TextView tvTimestamp;
        private final View layoutHeader;
        private final TextView tvHeaderTitle;
        private final SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        private final SimpleDateFormat monthFormat = new SimpleDateFormat("MM/yyyy", Locale.getDefault());
        private final SimpleDateFormat dayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAction = itemView.findViewById(R.id.tv_log_action);
            tvTaskTitle = itemView.findViewById(R.id.tv_log_task_title);
            tvTimestamp = itemView.findViewById(R.id.tv_log_timestamp);
            layoutHeader = itemView.findViewById(R.id.layout_log_header);
            tvHeaderTitle = itemView.findViewById(R.id.tv_log_header_title);
        }

        public void bind(ActivityLog log, ActivityLog previousLog) {
            tvAction.setText(log.action);
            if (log.taskTitle != null && !log.taskTitle.isEmpty()) {
                tvTaskTitle.setText(log.taskTitle);
                tvTaskTitle.setVisibility(View.VISIBLE);
            } else {
                tvTaskTitle.setVisibility(View.GONE);
            }
            tvTimestamp.setText(dateFormat.format(new Date(log.timestamp)));

            // Header logic
            if (previousLog == null) {
                showHeader(log.timestamp);
            } else {
                String currentYear = yearFormat.format(new Date(log.timestamp));
                String prevYear = yearFormat.format(new Date(previousLog.timestamp));
                
                String currentMonth = monthFormat.format(new Date(log.timestamp));
                String prevMonth = monthFormat.format(new Date(previousLog.timestamp));

                String currentDay = dayFormat.format(new Date(log.timestamp));
                String prevDay = dayFormat.format(new Date(previousLog.timestamp));

                if (!currentYear.equals(prevYear)) {
                    layoutHeader.setVisibility(View.VISIBLE);
                    tvHeaderTitle.setText(currentYear);
                    tvHeaderTitle.setTextSize(22f); // Chữ to cho năm
                } else if (!currentMonth.equals(prevMonth)) {
                    layoutHeader.setVisibility(View.VISIBLE);
                    tvHeaderTitle.setText("Tháng " + currentMonth);
                    tvHeaderTitle.setTextSize(18f);
                } else if (!currentDay.equals(prevDay)) {
                    layoutHeader.setVisibility(View.VISIBLE);
                    tvHeaderTitle.setText(currentDay);
                    tvHeaderTitle.setTextSize(14f);
                } else {
                    layoutHeader.setVisibility(View.GONE);
                }
            }
        }
        
        private void showHeader(long timestamp) {
            layoutHeader.setVisibility(View.VISIBLE);
            tvHeaderTitle.setText(dayFormat.format(new Date(timestamp)));
            tvHeaderTitle.setTextSize(14f);
        }
    }
}
