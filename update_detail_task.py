import re

file_det = "app/src/main/java/hcmute/edu/vn/tickticktodo/ui/TaskDetailBottomSheet.java"

with open(file_det, "r", encoding="utf-8") as f:
    text_det = f.read()

imports = """
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import android.database.Cursor;
import android.provider.OpenableColumns;
"""

if "import android.media.MediaPlayer;" not in text_det:
    text_det = text_det.replace("import android.os.Bundle;", imports + "\nimport android.os.Bundle;")

declarations = """
    private LinearLayout llAttachments, llVoicePlayer;
    private ImageView ivAttachmentImage, ivPlayPause;
    private TextView tvAttachmentFile, tvVoiceDuration;
    private SeekBar sbVoiceProgress;

    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();
    private Runnable runnable;
"""

if "private LinearLayout llAttachments" not in text_det:
    text_det = text_det.replace("private ImageButton btnDelete;", "private ImageButton btnDelete;\n" + declarations)


init_views = """
        llAttachments = view.findViewById(R.id.ll_attachments);
        llVoicePlayer = view.findViewById(R.id.ll_voice_player);
        ivAttachmentImage = view.findViewById(R.id.iv_attachment_image);
        ivPlayPause = view.findViewById(R.id.iv_play_pause);
        tvAttachmentFile = view.findViewById(R.id.tv_attachment_file);
        tvVoiceDuration = view.findViewById(R.id.tv_voice_duration);
        sbVoiceProgress = view.findViewById(R.id.sb_voice_progress);
"""

if "llAttachments = " not in text_det:
    text_det = text_det.replace("btnDelete = view.findViewById(R.id.btn_delete_task);", "btnDelete = view.findViewById(R.id.btn_delete_task);\n" + init_views)


display_logic = """
    private void displayAttachments(Task task) {
        boolean hasAttachment = false;
        if (task.getImageAttachment() != null) {
            hasAttachment = true;
            ivAttachmentImage.setVisibility(View.VISIBLE);
            ivAttachmentImage.setImageURI(Uri.parse(task.getImageAttachment()));
        }
        
        if (task.getVoiceAttachment() != null) {
            hasAttachment = true;
            llVoicePlayer.setVisibility(View.VISIBLE);
            setupAudioPlayer(Uri.parse(task.getVoiceAttachment()));
        }
        
        if (task.getFileAttachment() != null) {
            hasAttachment = true;
            tvAttachmentFile.setVisibility(View.VISIBLE);
            tvAttachmentFile.setText(getFileName(Uri.parse(task.getFileAttachment())));
        }
        
        if (hasAttachment) {
            llAttachments.setVisibility(View.VISIBLE);
        }
    }

    private void setupAudioPlayer(Uri uri) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(requireContext(), uri);
            mediaPlayer.prepare();
            
            sbVoiceProgress.setMax(mediaPlayer.getDuration());
            
            int duration = mediaPlayer.getDuration() / 1000;
            tvVoiceDuration.setText(String.format("%02d:%02d", duration / 60, duration % 60));
            
            ivPlayPause.setOnClickListener(v -> {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    ivPlayPause.setImageResource(R.drawable.ic_play);
                } else {
                    mediaPlayer.start();
                    ivPlayPause.setImageResource(R.drawable.ic_pause);
                    updateSeekBar();
                }
            });
            
            mediaPlayer.setOnCompletionListener(mp -> {
                ivPlayPause.setImageResource(R.drawable.ic_play);
                sbVoiceProgress.setProgress(0);
            });
            
            sbVoiceProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateSeekBar() {
        if (mediaPlayer != null) {
            sbVoiceProgress.setProgress(mediaPlayer.getCurrentPosition());
            if (mediaPlayer.isPlaying()) {
                runnable = this::updateSeekBar;
                handler.postDelayed(runnable, 100);
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }
"""

if "private void displayAttachments" not in text_det:
    text_det = text_det.replace("private void updateDueDateChip()", display_logic + "\n    private void updateDueDateChip()")

# Call this method inside observe
if "displayAttachments(" not in text_det:
    text_det = re.sub(r'(setupTaskData\(task\);)', r'\1\n                displayAttachments(task);', text_det)


# Stop player on destroy
destroy = """
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
"""

if "public void onDestroyView" not in text_det:
    text_det = text_det.replace("public void onCreate(@Nullable Bundle savedInstanceState)", destroy + "\n    public void onCreate(@Nullable Bundle savedInstanceState)")

with open(file_det, "w", encoding="utf-8") as f:
    f.write(text_det)

print("TaskDetailBottomSheet.java updated")
