package hcmute.edu.vn.doinbot.ui.activitylog;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import hcmute.edu.vn.doinbot.R;
import hcmute.edu.vn.doinbot.ui.activitylog.ActivityLogAdapter;
import hcmute.edu.vn.doinbot.ui.activitylog.ActivityLogViewModel;

public class ActivityLogActivity extends AppCompatActivity {

    private ActivityLogViewModel viewModel;
    private ActivityLogAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_log);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_activity_log);
        adapter = new ActivityLogAdapter();
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ActivityLogViewModel.class);

        // Load all initially
        viewModel.getAllLogs().observe(this, logs -> adapter.submitList(logs));

        SearchView searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    viewModel.getAllLogs().observe(ActivityLogActivity.this, logs -> adapter.submitList(logs));
                } else {
                    viewModel.searchLogs(newText).observe(ActivityLogActivity.this, logs -> adapter.submitList(logs));
                }
                return true;
            }
        });
    }
}
