package hcmute.edu.vn.tickticktodo.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import hcmute.edu.vn.tickticktodo.R;

public class HabitTrackerActivity extends AppCompatActivity {

    public static Intent newIntent(Context context) {
        return new Intent(context, HabitTrackerActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_habit_tracker);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.habit_fragment_container, new HabitTrackerFragment())
                    .commit();
        }
    }
}
