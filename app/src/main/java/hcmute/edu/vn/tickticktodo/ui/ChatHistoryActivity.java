package hcmute.edu.vn.tickticktodo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.adapter.ChatHistorySessionAdapter;
import hcmute.edu.vn.tickticktodo.model.ChatSession;
import hcmute.edu.vn.tickticktodo.viewmodel.ChatHistoryViewModel;

public class ChatHistoryActivity extends BaseActivity {

    private ChatHistoryViewModel viewModel;
    private ChatHistorySessionAdapter adapter;
    private View emptyView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_chat_history);

        ImageButton btnBack = findViewById(R.id.btn_history_back);
        ImageButton btnClear = findViewById(R.id.btn_history_clear);
        TextView btnNewSession = findViewById(R.id.btn_new_session);
        RecyclerView rvSessions = findViewById(R.id.rv_chat_sessions);
        emptyView = findViewById(R.id.layout_chat_history_empty);

        applyWindowInsets();

        adapter = new ChatHistorySessionAdapter(this::onSessionSelected);
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ChatHistoryViewModel.class);
        viewModel.getSessions().observe(this, sessions -> {
            adapter.submitList(sessions);
            if (emptyView != null) {
                boolean isEmpty = sessions == null || sessions.isEmpty();
                emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            }
        });

        btnBack.setOnClickListener(v -> finish());

        btnNewSession.setOnClickListener(v -> {
            Intent data = new Intent();
            data.putExtra(AiAssistantActivity.EXTRA_CREATE_NEW_SESSION, true);
            setResult(RESULT_OK, data);
            finish();
        });

        btnClear.setOnClickListener(v -> showClearHistoryDialog());
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.chat_history_root);
        View toolbar = findViewById(R.id.chat_history_toolbar);
        if (root == null || toolbar == null) {
            return;
        }

        final int rootLeft = root.getPaddingLeft();
        final int rootTop = root.getPaddingTop();
        final int rootRight = root.getPaddingRight();
        final int toolbarLeft = toolbar.getPaddingLeft();
        final int toolbarTop = toolbar.getPaddingTop();
        final int toolbarRight = toolbar.getPaddingRight();
        final int toolbarBottom = toolbar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            toolbar.setPadding(
                    toolbarLeft,
                    toolbarTop + insets.top,
                    toolbarRight,
                    toolbarBottom
            );
            view.setPadding(rootLeft, rootTop, rootRight, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void onSessionSelected(ChatSession session) {
        Intent data = new Intent();
        data.putExtra(AiAssistantActivity.EXTRA_SESSION_ID, session.id);
        setResult(RESULT_OK, data);
        finish();
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_history_clear_title)
                .setMessage(R.string.chat_history_clear_message)
                .setNegativeButton(R.string.add_list_btn_cancel, null)
                .setPositiveButton(R.string.chat_history_clear_confirm, (dialog, which) -> viewModel.clearAllHistory())
                .show();
    }
}
