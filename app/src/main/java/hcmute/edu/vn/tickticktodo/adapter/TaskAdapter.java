package hcmute.edu.vn.tickticktodo.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.Task;

/**
 * RecyclerView Adapter hiển thị danh sách Task theo phong cách TickTick.
 * Kế thừa ListAdapter (sử dụng DiffUtil) để tự động tính toán sự khác biệt
 * giữa list cũ và list mới, tối ưu hiệu suất cập nhật.
 */
public class TaskAdapter extends ListAdapter<Task, TaskAdapter.TaskViewHolder> {

    // ─── Callback interfaces cho UI events ───────────────────────────────────────

    /**
     * Callback khi user click vào checkbox (đánh dấu hoàn thành / chưa hoàn thành).
     */
    public interface OnTaskCheckedChangeListener {
        void onTaskCheckedChanged(Task task, boolean isChecked);
    }

    /**
     * Callback khi user click vào item (mở chi tiết task).
     */
    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    // ─── Fields ──────────────────────────────────────────────────────────────────

    private final OnTaskCheckedChangeListener checkedChangeListener;
    private final OnTaskClickListener clickListener;
    private final SimpleDateFormat timeFormat;
    private boolean showDetails = true;

    // ─── Constructor ─────────────────────────────────────────────────────────────

    public TaskAdapter(@NonNull OnTaskCheckedChangeListener checkedChangeListener,
                       @NonNull OnTaskClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.checkedChangeListener = checkedChangeListener;
        this.clickListener = clickListener;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }
    
    public void setShowDetails(boolean showDetails) {
        this.showDetails = showDetails;
        notifyDataSetChanged();
    }

    // ─── DiffUtil ────────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<Task> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Task>() {
                @Override
                public boolean areItemsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                            && Objects.equals(oldItem.getDescription(), newItem.getDescription())
                            && Objects.equals(oldItem.getDueDate(), newItem.getDueDate())
                            && oldItem.isCompleted() == newItem.isCompleted()
                            && oldItem.getPriority() == newItem.getPriority();
                }
            };

    // ─── Adapter overrides ───────────────────────────────────────────────────────

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    // ─── ViewHolder ──────────────────────────────────────────────────────────────

    class TaskViewHolder extends RecyclerView.ViewHolder {

        private final CheckBox cbCompleted;
        private final TextView tvTitle;
        private final TextView tvTime;
        private final TextView tvSubtitle;
        private final ImageView ivPriorityFlag;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cbCompleted = itemView.findViewById(R.id.cb_task_completed);
            tvTitle = itemView.findViewById(R.id.tv_task_title);
            tvTime = itemView.findViewById(R.id.tv_task_time);
            tvSubtitle = itemView.findViewById(R.id.tv_task_subtitle);
            ivPriorityFlag = itemView.findViewById(R.id.iv_priority_flag);
        }

        void bind(Task task) {
            // ── Title ────────────────────────────────────────────────────────
            tvTitle.setText(task.getTitle());

            // ── Time ─────────────────────────────────────────────────────────
            if (task.getDueDate() != null) {
                tvTime.setVisibility(View.VISIBLE);
                tvTime.setText(timeFormat.format(new Date(task.getDueDate())));
            } else {
                tvTime.setVisibility(View.GONE);
            }

            // ── Strikethrough nếu đã hoàn thành ─────────────────────────────
            // Bỏ hiệu ứng gạch ngang và không làm mờ theo yêu cầu
            tvTitle.setPaintFlags(tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            tvTitle.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_primary));

            // ── Subtitle (description + due time) ───────────────────────────
            String subtitle = buildSubtitle(task);
            if (!showDetails || subtitle.isEmpty()) {
                tvSubtitle.setVisibility(View.GONE);
            } else {
                tvSubtitle.setVisibility(View.VISIBLE);
                tvSubtitle.setText(subtitle);
            }

            // ── Checkbox ─────────────────────────────────────────────────────
            // Tạm bỏ listener trước khi set checked để tránh trigger callback
            cbCompleted.setOnCheckedChangeListener(null);
            cbCompleted.setChecked(task.isCompleted());
            cbCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int adapterPos = getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    checkedChangeListener.onTaskCheckedChanged(getItem(adapterPos), isChecked);
                }
            });

            // ── Priority flag ────────────────────────────────────────────────
            ivPriorityFlag.setColorFilter(getPriorityColor(task.getPriority()));

            // Ẩn flag nếu priority = 0 (None)
            ivPriorityFlag.setVisibility(task.getPriority() == 0 ? View.INVISIBLE : View.VISIBLE);

            // ── Item click ───────────────────────────────────────────────────
            itemView.setOnClickListener(v -> {
                int adapterPos = getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    clickListener.onTaskClick(getItem(adapterPos));
                }
            });
        }

        /**
         * Ghép description và thời gian due date thành subtitle.
         * Ví dụ: "Thảo luận Room DB ∙ 14:00"
         */
        private String buildSubtitle(Task task) {
            StringBuilder sb = new StringBuilder();

            String desc = task.getDescription();
            if (desc != null && !desc.trim().isEmpty()) {
                sb.append(desc.trim());
            }

            Long dueDate = task.getDueDate();
            if (dueDate != null && dueDate > 0) {
                String time = timeFormat.format(new Date(dueDate));
                // Chỉ hiện giờ nếu khác 00:00 (tức task có giờ cụ thể)
                if (!"00:00".equals(time)) {
                    if (sb.length() > 0) {
                        sb.append(" ∙ ");
                    }
                    sb.append(time);
                }
            }

            return sb.toString();
        }

        /**
         * Trả về mã màu tương ứng với priority level.
         */
        private int getPriorityColor(int priority) {
            switch (priority) {
                case 1:
                    return ContextCompat.getColor(itemView.getContext(), R.color.priority_low);
                case 2:
                    return ContextCompat.getColor(itemView.getContext(), R.color.priority_medium);
                case 3:
                    return ContextCompat.getColor(itemView.getContext(), R.color.priority_high);
                default:
                    return ContextCompat.getColor(itemView.getContext(), R.color.priority_none);
            }
        }
    }
}
