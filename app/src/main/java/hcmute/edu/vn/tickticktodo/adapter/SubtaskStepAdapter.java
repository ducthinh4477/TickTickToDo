package hcmute.edu.vn.tickticktodo.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.Subtask;

public class SubtaskStepAdapter extends RecyclerView.Adapter<SubtaskStepAdapter.SubtaskViewHolder> {

    public interface Listener {
        void onSubtaskCheckedChanged(Subtask subtask, boolean isChecked);

        void onSubtaskApproved(Subtask subtask);

        void onSubtaskPriorityChanged(Subtask subtask, int newPriority);
    }

    private final List<Subtask> items = new ArrayList<>();
    private final Listener listener;

    public SubtaskStepAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<Subtask> subtasks) {
        items.clear();
        if (subtasks != null) {
            items.addAll(subtasks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SubtaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subtask_step, parent, false);
        return new SubtaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubtaskViewHolder holder, int position) {
        holder.bind(items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class SubtaskViewHolder extends RecyclerView.ViewHolder {

        private final CheckBox cbDone;
        private final TextView tvTitle;
        private final ImageButton btnPriority;
        private final MaterialButton btnApprove;

        SubtaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cbDone = itemView.findViewById(R.id.cb_subtask_done);
            tvTitle = itemView.findViewById(R.id.tv_subtask_title);
            btnPriority = itemView.findViewById(R.id.btn_subtask_priority);
            btnApprove = itemView.findViewById(R.id.btn_subtask_approve);
        }

        void bind(Subtask subtask, int index) {
            String title = (index + 1) + ". " + subtask.getTitle();
            if (!subtask.isApproved()) {
                title += " " + itemView.getContext().getString(R.string.subtask_raw_suffix);
            }
            tvTitle.setText(title);

            cbDone.setOnCheckedChangeListener(null);
            cbDone.setChecked(subtask.isCompleted());
            cbDone.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int adapterPos = getBindingAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) {
                    return;
                }
                Subtask current = items.get(adapterPos);
                current.setCompleted(isChecked);
                listener.onSubtaskCheckedChanged(current, isChecked);
            });

            tvTitle.setPaintFlags(subtask.isCompleted()
                    ? tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            tvTitle.setAlpha(subtask.isApproved() ? 1f : 0.82f);

            btnPriority.setColorFilter(getPriorityColor(subtask.getPriority()));
            btnPriority.setOnClickListener(v -> {
                int adapterPos = getBindingAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) {
                    return;
                }
                Subtask current = items.get(adapterPos);
                int nextPriority = (current.getPriority() + 1) % 4;
                current.setPriority(nextPriority);
                notifyItemChanged(adapterPos);
                listener.onSubtaskPriorityChanged(current, nextPriority);
            });

            if (subtask.isApproved()) {
                btnApprove.setText(R.string.subtask_approved);
                btnApprove.setEnabled(false);
            } else {
                btnApprove.setText(R.string.subtask_approve);
                btnApprove.setEnabled(true);
                btnApprove.setOnClickListener(v -> {
                    int adapterPos = getBindingAdapterPosition();
                    if (adapterPos == RecyclerView.NO_POSITION) {
                        return;
                    }
                    Subtask current = items.get(adapterPos);
                    current.setApproved(true);
                    notifyItemChanged(adapterPos);
                    listener.onSubtaskApproved(current);
                });
            }
        }

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