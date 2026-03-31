package hcmute.edu.vn.tickticktodo.ui;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.receiver.TaskReminderReceiver;

public class AiReminderPopupActivity extends Activity implements TextToSpeech.OnInitListener {

    private TextToSpeech textToSpeech;
    private long taskId;
    private String taskTitle;
    private String message;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Đảm bảo hiển thị trên cả màn hình khoá và làm sáng màn hình
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.layout_ai_reminder_popup);

        taskId = getIntent().getLongExtra(TaskReminderReceiver.EXTRA_TASK_ID, -1);
        taskTitle = getIntent().getStringExtra(TaskReminderReceiver.EXTRA_TASK_TITLE);
        
        if (taskTitle == null) {
            taskTitle = "Không rõ";
        }

        TextView tvMessage = findViewById(R.id.tv_reminder_message);
        message = "Chào bạn, bạn có một công việc đến hạn: " + taskTitle + ". Bạn có muốn hoàn thành ngay không?";
        tvMessage.setText(message);

        textToSpeech = new TextToSpeech(this, this);

        Button btnComplete = findViewById(R.id.btn_complete_task);
        Button btnDismiss = findViewById(R.id.btn_dismiss_task);

        btnComplete.setOnClickListener(v -> {
            if (taskId != -1) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> {
                    TaskDatabase.getInstance(getApplicationContext())
                            .taskDao()
                            .markTaskAsCompletedWithDate(taskId, true, System.currentTimeMillis());
                });
                Toast.makeText(this, "Đã đánh dấu hoàn thành!", Toast.LENGTH_SHORT).show();
            }
            finishAndRemoveTask();
        });

        btnDismiss.setOnClickListener(v -> {
            finishAndRemoveTask();
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int langResult = textToSpeech.setLanguage(new Locale("vi", "VN"));
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Thử ngôn ngữ mặc định nếu tiếng Việt không khả dụng
                textToSpeech.setLanguage(Locale.getDefault());
            }
            // Đọc văn bản ngay khi TTS sẵn sàng
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "ReminderTTS");
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
