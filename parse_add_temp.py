import re
file_path = "app/src/main/java/hcmute/edu/vn/tickticktodo/ui/AddTaskBottomSheet.java"

with open(file_path, "r", encoding="utf-8") as f:
    text = f.read()

# We need to add the imports for media playing and file picking, and fields for URIs
imports = """
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
"""

if "import android.media.MediaPlayer;" not in text:
    text = text.replace("import android.os.Bundle;", imports + "\nimport android.os.Bundle;")

fields = """
    private ImageView btnAttachImage, btnAttachVoice, btnAttachFile;
    private LinearLayout llAttachments, llVoicePlayer;
    private ImageView ivAttachmentImage, ivPlayPause;
    private TextView tvAttachmentFile, tvVoiceDuration;
    private SeekBar sbVoiceProgress;

    private String imagePath = null;
    private String voicePath = null;
    private String filePath = null;

    private MediaPlayer mediaPlayer;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private Handler handler = new Handler();
    private Runnable runnable;
    
    private final ActivityResultLauncher<String> getContentLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    // Xử lý URI
                }
            }
    );
"""

if "private ImageView btnAttachImage;" not in text:
    text = text.replace("private Button btnSaveTask;", fields + "\n    private Button btnSaveTask;")

with open(file_path, "w", encoding="utf-8") as f:
    f.write(text)
