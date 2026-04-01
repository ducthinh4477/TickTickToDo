package hcmute.edu.vn.tickticktodo.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;

public class HabitTrackerActivity extends BaseActivity {

    public static Intent newIntent(Context context) {
        return new Intent(context, HabitTrackerActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_habit_tracker);
        applyWindowInsets();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.habit_fragment_container, new HabitTrackerFragment())
                    .commit();
        }
    }

    private void applyWindowInsets() {
        View container = findViewById(R.id.habit_fragment_container);
        ViewCompat.setOnApplyWindowInsetsListener(container, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int sidePadding = (int) (16 * view.getResources().getDisplayMetrics().density);
            view.setPadding(
                    sidePadding,
                    insets.top + sidePadding,
                    sidePadding,
                    insets.bottom + sidePadding
            );
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(container);
    }
}
