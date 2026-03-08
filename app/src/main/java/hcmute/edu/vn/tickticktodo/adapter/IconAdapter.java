package hcmute.edu.vn.tickticktodo.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.IconItem;

/**
 * Adapter cho RecyclerView lưới icon bên trong AddListDialog.
 *
 * Tính năng:
 *  - Hiển thị icon + tên trong lưới (GridLayoutManager 4 cột).
 *  - Khi click một item → highlight nền xanh, bỏ highlight các item khác.
 *  - Gọi OnIconSelectedListener để Dialog biết icon đang chọn.
 */
public class IconAdapter extends RecyclerView.Adapter<IconAdapter.IconViewHolder> {

    // ─── Callback ────────────────────────────────────────────────────────────────

    public interface OnIconSelectedListener {
        /** @param drawableResId R.drawable.* của icon vừa được chọn */
        void onIconSelected(int drawableResId);
    }

    // ─── Fields ──────────────────────────────────────────────────────────────────

    private final List<IconItem> items;
    private final OnIconSelectedListener listener;
    private int selectedPosition = 0; // mặc định item đầu tiên

    // ─── Constructor ─────────────────────────────────────────────────────────────

    public IconAdapter(@NonNull List<IconItem> items,
                       @NonNull OnIconSelectedListener listener) {
        this.items = items;
        this.listener = listener;
    }

    // ─── Adapter overrides ───────────────────────────────────────────────────────

    @NonNull
    @Override
    public IconViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_icon_picker, parent, false);
        return new IconViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull IconViewHolder holder, int position) {
        IconItem item = items.get(position);
        boolean isSelected = (position == selectedPosition);

        // ── Icon drawable ────────────────────────────────────────────────────
        holder.ivIcon.setImageResource(item.getDrawableResId());

        // ── Tint: trắng khi selected, xám khi không ─────────────────────────
        int iconTint = isSelected
                ? ContextCompat.getColor(holder.itemView.getContext(), R.color.white)
                : ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary);
        holder.ivIcon.setColorFilter(iconTint);

        // ── Nền: xanh khi selected, xám nhạt khi không ──────────────────────
        holder.flIconBg.setBackgroundResource(
                isSelected ? R.drawable.bg_icon_picker_selected
                           : R.drawable.bg_icon_picker_normal);

        // ── Tên icon ─────────────────────────────────────────────────────────
        holder.tvIconName.setText(item.getLabelResId());
        holder.tvIconName.setTextColor(isSelected
                ? ContextCompat.getColor(holder.itemView.getContext(), R.color.accent_primary)
                : ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));

        // ── Click ─────────────────────────────────────────────────────────────
        holder.itemView.setOnClickListener(v -> {
            int prev = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
            listener.onIconSelected(items.get(selectedPosition).getDrawableResId());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─── Public helpers ──────────────────────────────────────────────────────────

    /** Trả về drawableResId của icon hiện đang được chọn. */
    public int getSelectedDrawableResId() {
        if (items.isEmpty()) return 0;
        return items.get(selectedPosition).getDrawableResId();
    }

    // ─── ViewHolder ──────────────────────────────────────────────────────────────

    static class IconViewHolder extends RecyclerView.ViewHolder {
        final FrameLayout flIconBg;
        final ImageView   ivIcon;
        final TextView    tvIconName;

        IconViewHolder(@NonNull View itemView) {
            super(itemView);
            flIconBg    = itemView.findViewById(R.id.fl_icon_bg);
            ivIcon      = itemView.findViewById(R.id.iv_icon);
            tvIconName  = itemView.findViewById(R.id.tv_icon_name);
        }
    }
}

