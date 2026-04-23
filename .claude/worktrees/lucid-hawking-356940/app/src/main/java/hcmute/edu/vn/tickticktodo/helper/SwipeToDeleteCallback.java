package hcmute.edu.vn.doinbot.helper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import hcmute.edu.vn.doinbot.R;

/**
 * ItemTouchHelper.SimpleCallback cho tính năng "Vuốt sang trái để xóa".
 *
 * Khi vuốt sang trái:
 *   - Background chuyển đỏ dần
 *   - Icon thùng rác (trắng) xuất hiện bên phải
 *   - Thả tay → trigger callback onSwiped
 *
 * Sử dụng:
 *   SwipeToDeleteCallback callback = new SwipeToDeleteCallback(context, listener);
 *   new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
 */
public class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {

    private final OnSwipeDeleteListener listener;
    private final Drawable deleteIcon;
    private final Paint backgroundPaint;
    private final int iconMargin;

    // ─── Callback interface ──────────────────────────────────────────────────────

    public interface OnSwipeDeleteListener {
        /**
         * Được gọi khi item bị vuốt xóa hoàn toàn.
         * @param position vị trí trong adapter
         */
        void onSwipedDelete(int position);
    }

    // ─── Constructor ─────────────────────────────────────────────────────────────

    public SwipeToDeleteCallback(@NonNull Context context,
                                  @NonNull OnSwipeDeleteListener listener) {
        // 0 = không hỗ trợ drag; ItemTouchHelper.LEFT = vuốt trái
        super(0, ItemTouchHelper.LEFT);
        this.listener = listener;

        // Icon thùng rác (trắng)
        deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete);

        // Background đỏ
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(ContextCompat.getColor(context, R.color.swipe_delete_bg));

        // Khoảng cách icon cách mép phải
        iconMargin = (int) (context.getResources().getDisplayMetrics().density * 20);
    }

    // ─── Drag (không dùng) ───────────────────────────────────────────────────────

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false; // Không hỗ trợ kéo thả
    }

    // ─── Swipe completed ─────────────────────────────────────────────────────────

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            listener.onSwipedDelete(position);
        }
    }

    // ─── Vẽ background đỏ + icon trong khi vuốt ─────────────────────────────────

    @Override
    public void onChildDraw(@NonNull Canvas c,
                            @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {

        View itemView = viewHolder.itemView;

        // Chỉ vẽ khi đang vuốt sang trái (dX < 0)
        if (dX >= 0) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            return;
        }

        // ── Background đỏ (bo góc nhẹ) ──────────────────────────────────
        float alpha = Math.min(1f, Math.abs(dX) / (float) itemView.getWidth());
        backgroundPaint.setAlpha((int) (255 * alpha));

        RectF bgRect = new RectF(
                itemView.getRight() + dX,  // mép trái background
                itemView.getTop(),
                itemView.getRight(),        // mép phải = mép phải item
                itemView.getBottom()
        );
        float cornerRadius = 8f * recyclerView.getContext().getResources().getDisplayMetrics().density;
        c.drawRoundRect(bgRect, cornerRadius, cornerRadius, backgroundPaint);

        // ── Icon thùng rác ───────────────────────────────────────────────
        if (deleteIcon != null) {
            int iconSize = deleteIcon.getIntrinsicHeight();
            int itemHeight = itemView.getBottom() - itemView.getTop();

            // Căn giữa theo chiều dọc
            int iconTop = itemView.getTop() + (itemHeight - iconSize) / 2;
            int iconBottom = iconTop + iconSize;

            // Đặt bên phải, cách mép iconMargin px
            int iconRight = itemView.getRight() - iconMargin;
            int iconLeft = iconRight - deleteIcon.getIntrinsicWidth();

            // Chỉ hiện icon khi vuốt đủ xa
            if (Math.abs(dX) > iconMargin + deleteIcon.getIntrinsicWidth()) {
                deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                deleteIcon.setAlpha((int) (255 * Math.min(1f, alpha * 2)));
                deleteIcon.draw(c);
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    // ─── Tốc độ swipe tối thiểu (dễ trigger hơn) ────────────────────────────────

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 0.4f; // 40% chiều rộng item
    }
}
