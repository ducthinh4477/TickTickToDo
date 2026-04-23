package hcmute.edu.vn.doinbot.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import hcmute.edu.vn.doinbot.R;
import hcmute.edu.vn.doinbot.model.ChatSession;

public class ChatHistorySessionAdapter extends ListAdapter<ChatSession, ChatHistorySessionAdapter.SessionViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    private final OnSessionClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    public ChatHistorySessionAdapter(OnSessionClickListener listener) {
        super(new DiffUtil.ItemCallback<ChatSession>() {
            @Override
            public boolean areItemsTheSame(@NonNull ChatSession oldItem, @NonNull ChatSession newItem) {
                return oldItem.id == newItem.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull ChatSession oldItem, @NonNull ChatSession newItem) {
                return oldItem.id == newItem.id
                        && Objects.equals(oldItem.title, newItem.title)
                        && Objects.equals(oldItem.source, newItem.source)
                        && Objects.equals(oldItem.lastMessage, newItem.lastMessage)
                        && oldItem.createdAt == newItem.createdAt
                        && oldItem.updatedAt == newItem.updatedAt;
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class SessionViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTitle;
        private final TextView tvPreview;
        private final TextView tvTime;

        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_session_title);
            tvPreview = itemView.findViewById(R.id.tv_session_preview);
            tvTime = itemView.findViewById(R.id.tv_session_time);
        }

        void bind(ChatSession session) {
            String title = session.title;
            if (title == null || title.trim().isEmpty()) {
                title = itemView.getContext().getString(R.string.chat_history_untitled_session);
            }

            String preview = session.lastMessage;
            if (preview == null || preview.trim().isEmpty()) {
                preview = itemView.getContext().getString(R.string.chat_history_empty_preview);
            }

            tvTitle.setText(title);
            tvPreview.setText(preview);
            tvTime.setText(timeFormat.format(new Date(session.updatedAt)));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSessionClick(session);
                }
            });
        }
    }
}
