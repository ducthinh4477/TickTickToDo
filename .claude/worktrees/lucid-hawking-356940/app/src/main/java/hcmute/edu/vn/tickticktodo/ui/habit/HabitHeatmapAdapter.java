package hcmute.edu.vn.doinbot.ui.habit;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import hcmute.edu.vn.doinbot.R;
import hcmute.edu.vn.doinbot.model.HabitHeatmapCell;

public class HabitHeatmapAdapter extends RecyclerView.Adapter<HabitHeatmapAdapter.HeatmapViewHolder> {

    private final List<HabitHeatmapCell> cells = new ArrayList<>();

    public void submitList(List<HabitHeatmapCell> newCells) {
        cells.clear();
        if (newCells != null) {
            cells.addAll(newCells);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HeatmapViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_habit_heatmap_cell, parent, false);
        return new HeatmapViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HeatmapViewHolder holder, int position) {
        HabitHeatmapCell cell = cells.get(position);

        int color = ContextCompat.getColor(
                holder.itemView.getContext(),
                cell.isCompleted() ? R.color.habit_heatmap_active : R.color.habit_heatmap_inactive
        );

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(holder.itemView.getResources().getDisplayMetrics().density * 4f);
        background.setColor(color);
        holder.dotView.setBackground(background);
    }

    @Override
    public int getItemCount() {
        return cells.size();
    }

    static class HeatmapViewHolder extends RecyclerView.ViewHolder {

        private final View dotView;

        HeatmapViewHolder(@NonNull View itemView) {
            super(itemView);
            dotView = itemView.findViewById(R.id.view_heatmap_dot);
        }
    }
}
