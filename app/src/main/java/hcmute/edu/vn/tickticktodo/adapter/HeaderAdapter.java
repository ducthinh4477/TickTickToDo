package hcmute.edu.vn.tickticktodo.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    private int completedCount = 0;
    private boolean visible = false;

    /**
     * Cập nhật số lượng task đã hoàn thành.
     * Header chỉ hiện khi count > 0.
     */
    public void setCompletedCount(int count) {
        boolean wasVisible = visible;
        this.completedCount = count;
        this.visible = count > 0;

        if (wasVisible && visible) {
            // Chỉ thay đổi text
            notifyItemChanged(0);
        } else if (!wasVisible && visible) {
            // Hiện header
            notifyItemInserted(0);
        } else if (wasVisible && !visible) {
            // Ẩn header
            notifyItemRemoved(0);
        }
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
        String text = holder.itemView.getContext()
                .getString(R.string.section_completed, completedCount);
        holder.tvSectionHeader.setText(text);
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSectionHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSectionHeader = itemView.findViewById(R.id.tv_section_header);
        }
    }
}
