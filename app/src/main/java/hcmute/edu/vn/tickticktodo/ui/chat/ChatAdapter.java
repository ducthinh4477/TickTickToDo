package hcmute.edu.vn.tickticktodo.ui.chat;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.ChatMessage;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    public interface ActionClickListener {
        void onActionClick(ChatMessage message);
    }

    private List<ChatMessage> messages = new ArrayList<>();
    private ActionClickListener actionClickListener;

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    public void setActionClickListener(ActionClickListener actionClickListener) {
        this.actionClickListener = actionClickListener;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.tvMessage.setText(message.getText());

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.tvMessage.getLayoutParams();
        params.setMargins(0, 4, 0, 4);
        if (message.isUser()) {
            holder.messageContainer.setGravity(Gravity.END);
            holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble_user);
            holder.tvMessage.setTextColor(Color.WHITE);
            params.gravity = Gravity.END;
            holder.btnMessageAction.setVisibility(View.GONE);
            holder.btnMessageAction.setOnClickListener(null);
        } else {
            holder.messageContainer.setGravity(Gravity.START);
            holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble_ai);
            holder.tvMessage.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
            params.gravity = Gravity.START;

            if (message.hasAction()) {
                holder.btnMessageAction.setVisibility(View.VISIBLE);
                holder.btnMessageAction.setText(message.getActionLabel());
                holder.btnMessageAction.setOnClickListener(v -> {
                    if (actionClickListener != null) {
                        actionClickListener.onActionClick(message);
                    }
                });
            } else {
                holder.btnMessageAction.setVisibility(View.GONE);
                holder.btnMessageAction.setOnClickListener(null);
            }
        }
        holder.tvMessage.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        LinearLayout messageContainer;
        TextView btnMessageAction;

        ChatViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            btnMessageAction = itemView.findViewById(R.id.btnMessageAction);
        }
    }
}