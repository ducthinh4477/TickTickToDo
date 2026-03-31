import os

file_det = "app/src/main/java/hcmute/edu/vn/tickticktodo/ui/TaskDetailBottomSheet.java"

with open(file_det, "r", encoding="utf-8") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)
    if "private Task currentTask;" in line:
        new_lines.append("""
    private LinearLayout llAttachments, llVoicePlayer;
    private ImageView ivAttachmentImage, ivPlayPause;
    private TextView tvAttachmentFile, tvVoiceDuration;
    private android.widget.SeekBar sbVoiceProgress;
    
    private android.media.MediaPlayer mediaPlayer;
    private boolean isPlaying = false;
    private android.os.Handler handler = new android.os.Handler();
    private Runnable runnable;
""")
    if "btnPriorityHigh   = view.findViewById(R.id.btn_priority_high);" in line:
        new_lines.append("""
        llAttachments = view.findViewById(R.id.ll_attachments);
        llVoicePlayer = view.findViewById(R.id.ll_voice_player);
        ivAttachmentImage = view.findViewById(R.id.iv_attachment_image);
        ivPlayPause = view.findViewById(R.id.iv_play_pause);
        tvAttachmentFile = view.findViewById(R.id.tv_attachment_file);
        tvVoiceDuration = view.findViewById(R.id.tv_voice_duration);
        sbVoiceProgress = view.findViewById(R.id.sb_voice_progress);
""")
    if "setupTaskData(task);" in line:
        new_lines.append("                displayAttachments(task);\n")
    if "super.onDestroyView();" in line:
        new_lines.append("""
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
""")

new_code = "".join(new_lines)
display_logic = """
    private void displayAttachments(Task task) {
        boolean hasAttachment = false;
        if (task.getImageAttachment() != null) {
            hasAttachment = true;
            ivAttachmentImage.setVisibility(View.VISIBLE);
            ivAttachmentImage.setImageURI(android.net.Uri.parse(task.getImageAttachment()));
        }
        
        if (task.getVoiceAttachment() != null) {
            hasAttachment = true;
            llVoicePlayer.setVisibility(View.VISIBLE);
            setupAudioPlayer(android.net.Uri.parse(task.getVoiceAttachment()));
        }
        
        if (task.getFileAttachment() != null) {
            hasAttachment = true;
            tvAttachmentFile.setVisibility(View.VISIBLE);
            tvAttachmentFile.setText(getFileName(android.net.Uri.parse(task.getFileAttachment())));
        }
        
        if (hasAttachment && llAttachments != null) {
            llAttachments.setVisibility(View.VISIBLE);
        }
    }

    private void setupAudioPlayer(android.net.Uri uri) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        try {
            mediaPlayer = new android.media.MediaPlayer();
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
            
            sbVoiceProgress.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
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

    private String getFileName(android.net.Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
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

new_code = new_code.replace("private void updateDueDateChip()", display_logic + "\n    private void updateDueDateChip()")

with open(file_det, "w", encoding="utf-8") as f:
    f.write(new_code)
print("Done detail.")
