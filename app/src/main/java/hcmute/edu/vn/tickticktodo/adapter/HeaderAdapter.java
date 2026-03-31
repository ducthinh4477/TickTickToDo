package hcmute.edu.vn.tickticktodo.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import hcmute.edu.vn.tickticktodo.R;

/**
 * Adapter đơn giản hiển thị 1 item duy nhất: section header "Completed (N)".
 * Dùng với ConcatAdapter để ngăn cách phần incomplete và completed.
 *
 * Khi completedCount = 0, header sẽ ẩn đi (0 item).
 */
public class HeaderAdapter extends RecyclerView.Adapter<HeaderAdapter.HeaderViewHolder> {

    public interface OnHeaderClickListener {
        void onHeaderClick(boolean isExpanded);
    }

    private String title = "";
    private int itemCount = 0;
    private boolean visible = false;
    private boolean isExpanded = true;
    private OnHeaderClickListener listener;

    public void setOnHeaderClickListener(OnHeaderClickListener listener) {
        this.listener = listener;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    /**
     * Cập nhật thông tin header.
     */
    public void setHeader(String titleText, int count) {
        boolean wasVisible = visible;
        this.title = titleText;
        this.itemCount = count;
        this.visible = count > 0;

        if (wasVisible && visible) {
            notifyItemChanged(0);
        } else if (!wasVisible && visible) {
            notifyItemInserted(0);
        } else if (wasVisible && !visible) {
            notifyItemRemoved(0);
        }
    }
    
    // Giữ lại hàm cũ để không vỡ MainActivity hiện tại
    public void setCompletedCount(int count) {
        setHeader("Completed", count);
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    @NonNull
    @Override
    public HeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_section_header, parent, false);
        return new HeaderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HeaderViewHolder holder, int position) {
        String text = title + " (" + itemCount + ")";
        holder.tvSectionHeader.setText(text);
        
        float rotation = isExpanded ? 0f : -90f;
        holder.ivExpandCollapse.setRotation(rotation);

        holder.itemView.setOnClickListener(v -> {
            isExpanded = !isExpanded;
            // Xoay icon mượt
            holder.ivExpandCollapse.animate().rotation(isExpanded ? 0f : -90f).setDuration(200).start();
            if (listener != null) {
                listener.onHeaderClick(isExpanded);
            }
        });
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSectionHeader;
        final ImageView ivExpandCollapse;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSectionHeader = itemView.findViewById(R.id.tv_section_header);
            ivExpandCollapse = itemView.findViewById(R.id.iv_expand_collapse);
        }
    }
}
