package hcmute.edu.vn.tickticktodo.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import hcmute.edu.vn.tickticktodo.R;

/**
 * BottomSheet hiển thị các tùy chọn xem (View Options) — phong cách TickTick.
 *
 * Bao gồm:
 *   - View Mode: List / Kanban / Timeline
 *   - Hide Completed, Show Details, View Options (Sort/Group), Add Section, List Activities
 *
 * Cách sử dụng:
 *   ViewOptionsBottomSheet.newInstance().show(getSupportFragmentManager(), "ViewOptions");
 */
public class ViewOptionsBottomSheet extends BottomSheetDialogFragment {

    /** Callback interface cho các sự kiện từ bottom sheet */
    public interface OnOptionSelectedListener {
        void onViewModeSelected(int viewMode);    // 0 = List, 1 = Kanban, 2 = Timeline
        void onHideCompletedToggled();
        void onShowDetailsToggled();
        void onViewOptionsClicked();              // Mở Sort/Group sub-menu
        void onAddSectionClicked();
        void onListActivitiesClicked();
    }

    public static final int VIEW_MODE_LIST     = 0;
    public static final int VIEW_MODE_KANBAN   = 1;
    public static final int VIEW_MODE_TIMELINE = 2;

    private int currentViewMode = VIEW_MODE_LIST;
    private @Nullable OnOptionSelectedListener listener;

    // View Mode buttons
    private LinearLayout btnViewList, btnViewKanban, btnViewTimeline;
    private ImageView iconViewList, iconViewKanban, iconViewTimeline;
    private TextView labelViewList, labelViewKanban, labelViewTimeline;

    // Menu items
    private LinearLayout itemHideCompleted, itemShowDetails, itemViewOptions;
    private LinearLayout itemAddSection, itemListActivities;

    // ─── Factory ─────────────────────────────────────────────────────────────────

    public static ViewOptionsBottomSheet newInstance() {
        return new ViewOptionsBottomSheet();
    }

    // ─── Setter for listener ────────────────────────────────────────────────────

    public void setOnOptionSelectedListener(@Nullable OnOptionSelectedListener listener) {
        this.listener = listener;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_view_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setupViewModeButtons();
        setupMenuItems();
        updateViewModeUI();
    }

    // ─── View binding ────────────────────────────────────────────────────────────

    private void initViews(View view) {
        btnViewList     = view.findViewById(R.id.btn_view_list);
        btnViewKanban   = view.findViewById(R.id.btn_view_kanban);
        btnViewTimeline = view.findViewById(R.id.btn_view_timeline);

        iconViewList     = view.findViewById(R.id.icon_view_list);
        iconViewKanban   = view.findViewById(R.id.icon_view_kanban);
        iconViewTimeline = view.findViewById(R.id.icon_view_timeline);

        labelViewList     = view.findViewById(R.id.label_view_list);
        labelViewKanban   = view.findViewById(R.id.label_view_kanban);
        labelViewTimeline = view.findViewById(R.id.label_view_timeline);

        itemHideCompleted  = view.findViewById(R.id.item_hide_completed);
        itemShowDetails    = view.findViewById(R.id.item_show_details);
        itemViewOptions    = view.findViewById(R.id.item_view_options);
        itemAddSection     = view.findViewById(R.id.item_add_section);
        itemListActivities = view.findViewById(R.id.item_list_activities);
    }

    // ─── View mode switching ────────────────────────────────────────────────────

    private void setupViewModeButtons() {
        btnViewList.setOnClickListener(v -> {
            currentViewMode = VIEW_MODE_LIST;
            updateViewModeUI();
            if (listener != null) listener.onViewModeSelected(VIEW_MODE_LIST);
        });

        btnViewKanban.setOnClickListener(v -> {
            currentViewMode = VIEW_MODE_KANBAN;
            updateViewModeUI();
            if (listener != null) listener.onViewModeSelected(VIEW_MODE_KANBAN);
        });

        btnViewTimeline.setOnClickListener(v -> {
            currentViewMode = VIEW_MODE_TIMELINE;
            updateViewModeUI();
            if (listener != null) listener.onViewModeSelected(VIEW_MODE_TIMELINE);
        });
    }

    /**
     * Cập nhật giao diện nút View Mode: nút được chọn hiển thị highlight (accent color),
     * các nút còn lại hiển thị màu secondary.
     */
    private void updateViewModeUI() {
        if (getContext() == null) return;

        int accent    = ContextCompat.getColor(requireContext(), R.color.accent_primary);
        int secondary = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        // Reset all
        btnViewList.setBackgroundResource(0);
        btnViewKanban.setBackgroundResource(0);
        btnViewTimeline.setBackgroundResource(0);
        iconViewList.setImageTintList(ColorStateList.valueOf(secondary));
        iconViewKanban.setImageTintList(ColorStateList.valueOf(secondary));
        iconViewTimeline.setImageTintList(ColorStateList.valueOf(secondary));
        labelViewList.setTextColor(secondary);
        labelViewKanban.setTextColor(secondary);
        labelViewTimeline.setTextColor(secondary);

        // Highlight selected
        LinearLayout selectedBtn;
        ImageView selectedIcon;
        TextView selectedLabel;
        switch (currentViewMode) {
            case VIEW_MODE_KANBAN:
                selectedBtn   = btnViewKanban;
                selectedIcon  = iconViewKanban;
                selectedLabel = labelViewKanban;
                break;
            case VIEW_MODE_TIMELINE:
                selectedBtn   = btnViewTimeline;
                selectedIcon  = iconViewTimeline;
                selectedLabel = labelViewTimeline;
                break;
            default:
                selectedBtn   = btnViewList;
                selectedIcon  = iconViewList;
                selectedLabel = labelViewList;
                break;
        }

        selectedBtn.setBackgroundResource(R.drawable.bg_view_mode_selected);
        selectedIcon.setImageTintList(ColorStateList.valueOf(accent));
        selectedLabel.setTextColor(accent);
    }

    // ─── Menu item clicks ───────────────────────────────────────────────────────

    private void setupMenuItems() {
        itemHideCompleted.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHideCompletedToggled();
            } else {
                showToast(R.string.option_hide_completed);
            }
            dismiss();
        });

        itemShowDetails.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShowDetailsToggled();
            } else {
                showToast(R.string.option_show_details);
            }
            dismiss();
        });

        itemViewOptions.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewOptionsClicked();
            } else {
                showToast(R.string.option_view_options);
            }
            dismiss();
        });

        itemAddSection.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAddSectionClicked();
            } else {
                showToast(R.string.option_add_section);
            }
            dismiss();
        });

        itemListActivities.setOnClickListener(v -> {
            if (listener != null) {
                listener.onListActivitiesClicked();
            } else {
                showToast(R.string.option_list_activities);
            }
            dismiss();
        });
    }

    private void showToast(int stringRes) {
        if (getContext() != null) {
            Toast.makeText(getContext(), stringRes, Toast.LENGTH_SHORT).show();
        }
    }
}

