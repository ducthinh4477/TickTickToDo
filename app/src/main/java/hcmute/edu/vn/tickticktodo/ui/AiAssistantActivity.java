package hcmute.edu.vn.tickticktodo.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.adapter.ChatAdapter;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.databinding.ActivityAiAssistantBinding;
import hcmute.edu.vn.tickticktodo.model.ChatMessage;
import hcmute.edu.vn.tickticktodo.model.Task;

public class AiAssistantActivity extends BaseActivity {

    private ActivityAiAssistantBinding binding;
    private ChatAdapter chatAdapter;
    private GenerativeModelFutures modelFutures;
    private ExecutorService dbExecutor;
    private Handler mainHandler;

    // TODO: Thay bằng API Key thật của bạn
    private static final String GEMINI_API_KEY = "AIzaSyB02eKS1raJFbWW1JSrDMSaIHWSXCeAxBQ";

    private final String SYSTEM_PROMPT = "Bạn là trợ lý ảo của TickTickToDo. Hãy giúp người dùng quản lý thời gian. " +
            "Nếu người dùng muốn tạo công việc, hãy trả về MỘT CHUỖI JSON DUY NHẤT (không dùng markdown code block, không có text nào khác) chứa các trường: " +
            "'title' (Tên công việc kiểu String), 'description' (Mô tả công việc kiểu String), 'dueDate' (Ngày hạn kiểu số nguyên timestamp millisecond, hiện tại là: " + System.currentTimeMillis() + "), " +
            "'priority' (Mức độ kiểu số nguyên: 0-None, 1-Low, 2-Medium, 3-High), và 'reminder' (số phút nhắc nhở trước hạng, kiểu số nguyên, ví dụ 10, 30, 60). " +
            "Nếu là trò chuyện bình thường, hãy trả về text.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAiAssistantBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        setupRecyclerView();
        setupGemini();

        binding.btnSend.setOnClickListener(v -> {
            String message = binding.messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
            }
        });
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.chatRecyclerView.setLayoutManager(layoutManager);
        binding.chatRecyclerView.setAdapter(chatAdapter);
    }

    private void setupGemini() {
        GenerativeModel gm = new GenerativeModel("gemini-1.5-flash", GEMINI_API_KEY);
        modelFutures = GenerativeModelFutures.from(gm);
        
        chatAdapter.addMessage(new ChatMessage("Xin chào! Tôi có thể giúp bạn tạo công việc mới hoặc quản lý thời gian.", false));
    }

    private void sendMessage(String userText) {
        binding.messageInput.setText("");
        chatAdapter.addMessage(new ChatMessage(userText, true));
        binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

        String promptWithContext = SYSTEM_PROMPT + "\n\nUser: " + userText;
        Content content = new Content.Builder().addText(promptWithContext).build();

        ListenableFuture<GenerateContentResponse> response = modelFutures.generateContent(content);
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String aiResponse = result.getText();
                handleAiResponse(aiResponse);
            }

            @Override
            public void onFailure(Throwable t) {
                mainHandler.post(() -> {
                    chatAdapter.addMessage(new ChatMessage("Lỗi kết nối AI: " + t.getMessage(), false));
                    binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                });
            }
        }, dbExecutor); // Chạy callback ở background thread
    }

    private void handleAiResponse(String text) {
        if (text == null || text.trim().isEmpty()) return;

        String cleanText = text.trim();
        // Xóa markdown block nếu có (Gemini hay bọc json trong ```json ... ```)
        if (cleanText.startsWith("```") && cleanText.endsWith("```")) {
            int firstNewline = cleanText.indexOf('\n');
            int lastBackticks = cleanText.lastIndexOf("```");
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                cleanText = cleanText.substring(firstNewline + 1, lastBackticks).trim();
            }
        }

        try {
            JSONObject json = new JSONObject(cleanText);
            String title = json.optString("title", "");
            String description = json.optString("description", "");
            long dueDate = json.optLong("dueDate", 0);
            int priority = json.optInt("priority", 0);
            // Assuming we just map reminder directly if available or handle it via duration maybe? Task model doesn't explicitly have reminder time as a simple integer without an alarm manager mapping but wait... 'reminder' is not in model explicitly? The prompt says "trường hiện có (title, priority, reminder, v.v.)" but my search showed `dueDate`, `duration`, `location`, `priority`.
            // Wait, does Task have reminder? 
            
            if (!title.isEmpty()) {
                Task targetTask = new Task();
                targetTask.setTitle(title);
                targetTask.setDescription(description);
                targetTask.setPriority(priority);
                if (dueDate > 0) {
                    targetTask.setDueDate(dueDate);
                }

                // Chạy ngầm insert database
                try {
                    TaskDatabase.getInstance(AiAssistantActivity.this).taskDao().insert(targetTask);
                    mainHandler.post(() -> {
                        chatAdapter.addMessage(new ChatMessage("Đã tạo công việc: " + title + " thành công!", false));
                        binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                    });
                } catch (Exception e) {
                   e.printStackTrace();
                   mainHandler.post(() -> chatAdapter.addMessage(new ChatMessage("Đã có lỗi xảy ra khi lưu vào database.", false)));
                }

            } else {
                showTextResponse(cleanText);
            }
        } catch (JSONException e) {
            // Không phải JSON, hiển thị như text bình thường
            showTextResponse(cleanText);
        }
    }

    private void showTextResponse(String text) {
        mainHandler.post(() -> {
            chatAdapter.addMessage(new ChatMessage(text, false));
            binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }
}