package hcmute.edu.vn.tickticktodo.ui;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import hcmute.edu.vn.tickticktodo.BaseActivity;

public class FloatingQuickAddActivity extends BaseActivity {

    private static final String TAG_ADD_TASK = "FloatingQuickAddTask";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new FrameLayout(this));

        if (savedInstanceState == null) {
            AddTaskBottomSheet.newOverlayPopupInstance()
                    .show(getSupportFragmentManager(), TAG_ADD_TASK);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getSupportFragmentManager().findFragmentByTag(TAG_ADD_TASK) == null) {
            finish();
        }
    }
}
