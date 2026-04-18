package hcmute.edu.vn.tickticktodo.ui.debug;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.agent.context.ContextAgent;
import hcmute.edu.vn.tickticktodo.agent.context.ContextSnapshot;
import hcmute.edu.vn.tickticktodo.agent.proactive.ProactiveEngine;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.data.model.SuggestionEntity;

public class ProactiveDebugActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView contextSnapshotText;
    private TextView suggestionListText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proactive_debug);

        contextSnapshotText = findViewById(R.id.tv_context_snapshot_debug);
        suggestionListText = findViewById(R.id.tv_suggestion_list_debug);

        findViewById(R.id.btn_debug_refresh).setOnClickListener(v -> refreshDebugData());
        findViewById(R.id.btn_debug_refresh_context).setOnClickListener(v -> {
            ContextAgent.getInstance(getApplicationContext()).refreshSnapshot("DEBUG_SCREEN_REFRESH");
            refreshDebugData();
        });
        findViewById(R.id.btn_debug_run_tick).setOnClickListener(v -> {
            ProactiveEngine.getInstance(getApplicationContext()).evaluateNow("DEBUG_SCREEN_MANUAL_TICK");
            Toast.makeText(this, "Triggered proactive tick", Toast.LENGTH_SHORT).show();
            refreshDebugData();
        });

        refreshDebugData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void refreshDebugData() {
        executor.execute(() -> {
            ContextSnapshot snapshot = ContextAgent.getInstance(getApplicationContext()).getLatestSnapshot();
            List<SuggestionEntity> suggestions = TaskDatabase.getInstance(getApplicationContext())
                    .suggestionDao()
                    .getAllSuggestionsSync();

            String snapshotDump = snapshot == null
                    ? "(empty)"
                    : snapshot.toCompactJson().toString();
            String suggestionDump = buildSuggestionDump(suggestions);

            mainHandler.post(() -> {
                contextSnapshotText.setText(snapshotDump);
                suggestionListText.setText(suggestionDump);
            });
        });
    }

    private String buildSuggestionDump(List<SuggestionEntity> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return "(No suggestions in DB)";
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        StringBuilder builder = new StringBuilder();

        for (SuggestionEntity suggestion : suggestions) {
            if (suggestion == null) {
                continue;
            }

            builder.append("ID: ").append(safe(suggestion.id)).append("\n");
            builder.append("Type: ").append(safe(suggestion.type)).append("\n");
            builder.append("Status: ").append(safe(suggestion.status)).append("\n");
            builder.append("Title: ").append(safe(suggestion.title)).append("\n");
            builder.append("Reason: ").append(safe(suggestion.reason)).append("\n");
            builder.append("Priority/Confidence: ")
                    .append(String.format(Locale.ROOT, "%.2f / %.2f", suggestion.priorityScore, suggestion.confidence))
                    .append("\n");
            builder.append("Created: ").append(format.format(new Date(suggestion.createdAtMillis))).append("\n");
            builder.append("Expires: ").append(format.format(new Date(suggestion.expiresAtMillis))).append("\n");
            builder.append("RequiresConfirmation: ").append(suggestion.requiresConfirmation).append("\n");
            builder.append("------------------------------------\n");
        }

        return builder.toString().trim();
    }

    private String safe(String value) {
        return TextUtils.isEmpty(value) ? "" : value;
    }
}
