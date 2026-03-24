package hcmute.edu.vn.tickticktodo.ui;

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
import hcmute.edu.vn.tickticktodo.R;

public class EisenhowerActivity extends BaseActivity {

    private FloatingActionButton fab;
    private View quadrantUrgent, quadrantNotUrgent, quadrantNormal, quadrantSlow;
    private View appBar;
    private ImageButton btnBack;

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
        String quadrantName = "";
        
        if (isViewContains(quadrantUrgent, rawX, rawY)) {
            quadrantName = "Khẩn cấp";
        } else if (isViewContains(quadrantNotUrgent, rawX, rawY)) {
            quadrantName = "Không gấp";
        } else if (isViewContains(quadrantNormal, rawX, rawY)) {
            quadrantName = "Bình thường";
        } else if (isViewContains(quadrantSlow, rawX, rawY)) {
            quadrantName = "Từ từ làm";
        }

        if (!quadrantName.isEmpty()) {
            Toast.makeText(this, "Đã thả vào ô: " + quadrantName, Toast.LENGTH_SHORT).show();
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
}