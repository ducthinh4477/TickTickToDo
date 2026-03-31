import re

file_det = "app/src/main/java/hcmute/edu/vn/tickticktodo/ui/TaskDetailBottomSheet.java"
with open(file_det, "r", encoding="utf-8") as f:
    text = f.read()

fields = """
    private LinearLayout llAttachments, llVoicePlayer;
    private ImageView ivAttachmentImage, ivPlayPause;
    private TextView tvAttachmentFile, tvVoiceDuration;
    private SeekBar sbVoiceProgress;

    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private Handler handler = new Handler();
    private Runnable runnable;
"""

if "private MediaPlayer mediaPlayer;" not in text:
    if "private ImageButton btnDelete;" in text:
        text = text.replace("private ImageButton btnDelete;", "private ImageButton btnDelete;\n" + fields)
    else:
        text = text.replace("public class TaskDetailBottomSheet", "public class TaskDetailBottomSheet\n" + fields)
    with open(file_det, "w", encoding="utf-8") as f:
        f.write(text)

print("Fields fixed.")
