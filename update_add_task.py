import re

file_add = "app/src/main/java/hcmute/edu/vn/tickticktodo/ui/AddTaskBottomSheet.java"

with open(file_add, "r", encoding="utf-8") as f:
    text_add = f.read()

imports = """
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;
import android.database.Cursor;
import android.provider.OpenableColumns;
"""

if "import android.media.MediaPlayer;" not in text_add:
    text_add = text_add.replace("import android.os.Bundle;", imports + "\nimport android.os.Bundle;")

declarations = """
    private LinearLayout llAttachments, llVoicePlayer;
    private ImageView ivAttachmentImage, ivPlayPause;
    private TextView tvAttachmentFile, tvVoiceDuration;
    private SeekBar sbVoiceProgress;

    private String imagePath = null;
    private String voicePath = null;
    private String filePath = null;

    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private Handler handler = new Handler();
    private Runnable runnable;

    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> audioPickerLauncher;
    private ActivityResultLauncher<String> filePickerLauncher;
"""

if "private LinearLayout llAttachments" not in text_add:
    text_add = text_add.replace("private View btnAddFile;", "private View btnAddFile;\n" + declarations)

init_views = """
        llAttachments = view.findViewById(R.id.ll_attachments);
        llVoicePlayer = view.findViewById(R.id.ll_voice_player);
        ivAttachmentImage = view.findViewById(R.id.iv_attachment_image);
        ivPlayPause = view.findViewById(R.id.iv_play_pause);
        tvAttachmentFile = view.findViewById(R.id.tv_attachment_file);
        tvVoiceDuration = view.findViewById(R.id.tv_voice_duration);
        sbVoiceProgress = view.findViewById(R.id.sb_voice_progress);
"""

if "llAttachments = " not in text_add:
    text_add = text_add.replace("btnAddFile = view.findViewById(R.id.btn_add_file);", "btnAddFile = view.findViewById(R.id.btn_add_file);\n" + init_views)

setup_methods = """
    private void setupPickers() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                imagePath = uri.toString();
                llAttachments.setVisibility(View.VISIBLE);
                ivAttachmentImage.setVisibility(View.VISIBLE);
                ivAttachmentImage.setImageURI(uri);
            }
        });
        
        audioPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                voicePath = uri.toString();
                llAttachments.setVisibility(View.VISIBLE);
                llVoicePlayer.setVisibility(View.VISIBLE);
                setupAudioPlayer(uri);
            }
        });
        
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                filePath = uri.toString();
                llAttachments.setVisibility(View.VISIBLE);
                tvAttachmentFile.setVisibility(View.VISIBLE);
                String fileName = getFileName(uri);
                tvAttachmentFile.setText(fileName);
            }
        });
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

if "private void setupPickers" not in text_add:
    text_add = text_add.replace("private void updatePriorityIcon()", setup_methods + "\n    private void updatePriorityIcon()")

if "setupPickers();" not in text_add:
    text_add = text_add.replace("setupSaveButton();", "setupPickers();\n        setupSaveButton();")

# Hook up the existing more options buttons
text_add = re.sub(r'btnAddImage\.setOnClickListener\([^)]+\}\);', 'btnAddImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));', text_add)
text_add = re.sub(r'btnAddAudio\.setOnClickListener\([^)]+\}\);', 'btnAddAudio.setOnClickListener(v -> audioPickerLauncher.launch("audio/*"));', text_add)
text_add = re.sub(r'btnAddFile\.setOnClickListener\([^)]+\}\);', 'btnAddFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));', text_add)

# Modify saving task to include the fields
save_insert = """
        if (imagePath != null) task.setImageAttachment(imagePath);
        if (voicePath != null) task.setVoiceAttachment(voicePath);
        if (filePath != null) task.setFileAttachment(filePath);
"""

text_add = re.sub(r'(taskViewModel\.insert\(task\);)', save_insert + r'\n        \1', text_add)

# Stop player on destroy
destroy = """
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
"""

if "public void onDestroy" not in text_add:
    text_add = text_add.replace("public void onCreate(@Nullable Bundle savedInstanceState)", destroy + "\n    public void onCreate(@Nullable Bundle savedInstanceState)")

with open(file_add, "w", encoding="utf-8") as f:
    f.write(text_add)

print("AddTaskBottomSheet.java updated")
