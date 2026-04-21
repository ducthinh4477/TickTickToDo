package hcmute.edu.vn.tickticktodo.ui.task;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import hcmute.edu.vn.tickticktodo.BaseActivity;

import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import androidx.lifecycle.ViewModelProvider;
import hcmute.edu.vn.tickticktodo.ui.task.TaskViewModel;
import hcmute.edu.vn.tickticktodo.model.Task;
import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;

import hcmute.edu.vn.tickticktodo.R;

public class EisenhowerActivity extends BaseActivity {

    private FloatingActionButton fab;
    private View quadrantUrgent, quadrantNotUrgent, quadrantNormal, quadrantSlow;
    private View appBar;
    private ImageButton btnBack;
    private LinearLayout llTasksUrgent, llTasksNotUrgent, llTasksNormal, llTasksSlow;
    private android.widget.TextView tvEmptyUrgent, tvEmptyNotUrgent, tvEmptyNormal, tvEmptySlow;
    private TaskViewModel taskViewModel;

    private float dX, dY;
    private float initialX, initialY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_eisenhower);

        initViews();
        applyWindowInsets();
        setupDraggableFab();
    }

    private void initViews() {
        appBar = findViewById(R.id.app_bar);
        fab = findViewById(R.id.fab_add_eisenhower);
        quadrantUrgent = findViewById(R.id.quadrant_urgent);
        quadrantNotUrgent = findViewById(R.id.quadrant_not_urgent);
        quadrantNormal = findViewById(R.id.quadrant_normal);
        quadrantSlow = findViewById(R.id.quadrant_slow);
        btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        llTasksUrgent = findViewById(R.id.ll_tasks_urgent);
        llTasksNotUrgent = findViewById(R.id.ll_tasks_not_urgent);
        llTasksNormal = findViewById(R.id.ll_tasks_normal);
        llTasksSlow = findViewById(R.id.ll_tasks_slow);
        tvEmptyUrgent = findViewById(R.id.tv_empty_urgent);
        tvEmptyNotUrgent = findViewById(R.id.tv_empty_not_urgent);
        tvEmptyNormal = findViewById(R.id.tv_empty_normal);
        tvEmptySlow = findViewById(R.id.tv_empty_slow);

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        taskViewModel.getTodayAllTasks().observe(this, this::updateMatrixes);
        fab.setOnClickListener(v -> showAddTaskDialog(-1));

    }

    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(appBar, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), insets.top, view.getPaddingRight(), view.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggableFab() {
        fab.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Record original position so we can animate back
                    if (initialX == 0 && initialY == 0) {
                        initialX = view.getX();
                        initialY = view.getY();
                    }
                    dX = view.getX() - event.getRawX();
                    dY = view.getY() - event.getRawY();
                    view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start();
                    break;

                case MotionEvent.ACTION_MOVE:
                    view.setX(event.getRawX() + dX);
                    view.setY(event.getRawY() + dY);
                    break;

                case MotionEvent.ACTION_UP:
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    // Handle drop and determine the quadrant
                    handleDrop(event.getRawX(), event.getRawY());

                    // Animate back to original position
                    animateFabToOriginalPosition();
                    break;

                default:
                    return false;
            }
            return true;
        });
    }

    private void handleDrop(float rawX, float rawY) {
        int quadrantIndex = -1;
        
        if (isViewContains(quadrantUrgent, rawX, rawY)) {
            quadrantIndex = 0;
        } else if (isViewContains(quadrantNotUrgent, rawX, rawY)) {
            quadrantIndex = 1;
        } else if (isViewContains(quadrantNormal, rawX, rawY)) {
            quadrantIndex = 2;
        } else if (isViewContains(quadrantSlow, rawX, rawY)) {
            quadrantIndex = 3;
        }

        if (quadrantIndex != -1) {
            showAddTaskDialog(quadrantIndex);
        }
    }

    private boolean isViewContains(View view, float rx, float ry) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        int w = view.getWidth();
        int h = view.getHeight();

        return !(rx < x || rx > x + w || ry < y || ry > y + h);
    }

    private void animateFabToOriginalPosition() {
        if (initialX == 0 && initialY == 0) return;

        PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat(View.X, initialX);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.Y, initialY);
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(fab, pvhX, pvhY);
        animator.setDuration(300);
        animator.setInterpolator(new android.view.animation.OvershootInterpolator());
        animator.start();
    }

    private void updateMatrixes(List<Task> tasks) {
        if (tasks == null) return;

        llTasksUrgent.removeAllViews();
        llTasksNotUrgent.removeAllViews();
        llTasksNormal.removeAllViews();
        llTasksSlow.removeAllViews();

        for (Task task : tasks) {
            View taskView = LayoutInflater.from(this).inflate(R.layout.item_eisenhower_task, null);
            TextView title = taskView.findViewById(R.id.tv_eisenhower_title);
            title.setText(task.getTitle());
            
            TextView desc = taskView.findViewById(R.id.tv_eisenhower_desc);
            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                desc.setVisibility(View.VISIBLE);
                desc.setText(task.getDescription());
            }

            ImageView check = taskView.findViewById(R.id.iv_eisenhower_check);
            
            if (task.isCompleted()) {
                check.setImageResource(R.drawable.ic_checkbox_square_checked);
                title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                title.setTextColor(Color.parseColor("#808080"));
                desc.setTextColor(Color.parseColor("#808080"));
            } else {
                check.setImageResource(R.drawable.ic_checkbox_square_unchecked);
                title.setPaintFlags(title.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                // Do not change default text colors to keeping it readable based on light/dark mode if not overriding
            }

            check.setOnClickListener(v -> {
                boolean isNowCompleted = !task.isCompleted();
                check.setImageResource(isNowCompleted ? R.drawable.ic_checkbox_square_checked : R.drawable.ic_checkbox_square_unchecked);
                check.postDelayed(() -> {
                    task.setCompleted(isNowCompleted);
                    task.setCompletedDate(isNowCompleted ? System.currentTimeMillis() : null);
                    taskViewModel.update(task);
                }, 300);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            
            taskView.setLayoutParams(params);

            switch (task.getPriority()) {
                case 3: llTasksUrgent.addView(taskView); break;
                case 2: llTasksNotUrgent.addView(taskView); break;
                case 1: llTasksNormal.addView(taskView); break;
                case 0: llTasksSlow.addView(taskView); break;
            }
        }
        if (tvEmptyUrgent != null) tvEmptyUrgent.setVisibility(llTasksUrgent.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        if (tvEmptyNotUrgent != null) tvEmptyNotUrgent.setVisibility(llTasksNotUrgent.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        if (tvEmptyNormal != null) tvEmptyNormal.setVisibility(llTasksNormal.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        if (tvEmptySlow != null) tvEmptySlow.setVisibility(llTasksSlow.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void showAddTaskDialog(int quadrantIndex) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_eisenhower_add);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        EditText etTitle = dialog.findViewById(R.id.et_eisenhower_title);
        EditText etDesc = dialog.findViewById(R.id.et_eisenhower_desc);
        Spinner spinner = dialog.findViewById(R.id.spinner_eisenhower_quadrant);
        Button btnCancel = dialog.findViewById(R.id.btn_eisenhower_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_eisenhower_save);

        String[] options = {"1. Khẩn cấp & Quan trọng", "2. Quan trọng, ko gắp", "3. Gấp, ko quan trọng", "4. Ko gấp, ko quan trọng"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (quadrantIndex >= 0 && quadrantIndex <= 3) {
            spinner.setSelection(quadrantIndex);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) return;
            
            int priority = 0; // mapping from spinner to priority
            switch (spinner.getSelectedItemPosition()) {
                case 0: priority = 3; break;
                case 1: priority = 2; break;
                case 2: priority = 1; break;
                case 3: priority = 0; break;
            }

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            
            Task t = new Task(title, etDesc.getText().toString().trim(), cal.getTimeInMillis(), false, 0);
            t.setPriority(priority);
            taskViewModel.insert(t);
            
            dialog.dismiss();
        });

        dialog.show();
    }
}