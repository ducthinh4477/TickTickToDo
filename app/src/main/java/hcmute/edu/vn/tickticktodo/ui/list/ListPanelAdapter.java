package hcmute.edu.vn.tickticktodo.ui.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.TodoList;

/**
 * Adapter hiển thị danh sách TodoList trong Lists Panel (Drawer).
 * Mỗi item: icon list + tên list.
 */
public class ListPanelAdapter extends ListAdapter<TodoList, ListPanelAdapter.ViewHolder> {

    public interface OnListClickListener {
        void onListClick(TodoList todoList);
    }

    private final OnListClickListener listener;

    public ListPanelAdapter(OnListClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<TodoList> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<TodoList>() {
                @Override
                public boolean areItemsTheSame(@NonNull TodoList oldItem, @NonNull TodoList newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull TodoList oldItem, @NonNull TodoList newItem) {
                    return oldItem.getName().equals(newItem.getName())
                            && oldItem.getColorRes() == newItem.getColorRes()
                            && oldItem.getIconResId() == newItem.getIconResId();
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_list_panel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TodoList item = getItem(position);
        holder.tvName.setText(item.getName());

        // ── Icon: usar iconResId si está disponible, si no ic_list por defecto
        if (item.getIconResId() != 0) {
            holder.ivIcon.setImageResource(item.getIconResId());
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_list);
        }

        // ── Tint: usar colorRes si está disponible, si no text_secondary ──────
        int color = item.getColorRes() > 0
                ? item.getColorRes()
                : ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary);
        holder.ivIcon.setColorFilter(color);

        holder.itemView.setOnClickListener(v -> listener.onListClick(item));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_list_icon);
            tvName = itemView.findViewById(R.id.tv_list_name);
        }
    }
}
