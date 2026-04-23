package hcmute.edu.vn.tickticktodo.ui.task;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.data.dao.SubtaskDao;
import hcmute.edu.vn.tickticktodo.data.dao.TaskDao;
import hcmute.edu.vn.tickticktodo.data.database.TaskDatabase;
import hcmute.edu.vn.tickticktodo.model.Subtask;
import hcmute.edu.vn.tickticktodo.model.Task;

/**
 * RecyclerView Adapter hiển thị danh sách Task theo phong cách TickTick.
 * Kế thừa ListAdapter (sử dụng DiffUtil) để tự động tính toán sự khác biệt
 * giữa list cũ và list mới, tối ưu hiệu suất cập nhật.
 */
public class TaskAdapter extends ListAdapter<Task, TaskAdapter.TaskViewHolder> {

    // ─── Callback interfaces cho UI events ───────────────────────────────────────

    /**
     * Callback khi user click vào checkbox (đánh dấu hoàn thành / chưa hoàn thành).
     */
    public interface OnTaskCheckedChangeListener {
        void onTaskCheckedChanged(Task task, boolean isChecked);
    }

    /**
     * Callback khi user click vào item (mở chi tiết task).
     */
    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    // ─── Fields ──────────────────────────────────────────────────────────────────

    private final OnTaskCheckedChangeListener checkedChangeListener;
    private final OnTaskClickListener clickListener;
    private final SimpleDateFormat timeFormat;
    private final Map<Long, List<Subtask>> subtaskCache = new HashMap<>();
    private final ExecutorService subtaskExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean showDetails = true;
    private boolean showExpandedAttachments = false;
    private SubtaskDao subtaskDao;
    private TaskDao taskDao;

    // ─── Constructor ─────────────────────────────────────────────────────────────

    public TaskAdapter(@NonNull OnTaskCheckedChangeListener checkedChangeListener,
                       @NonNull OnTaskClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.checkedChangeListener = checkedChangeListener;
        this.clickListener = clickListener;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }
    
    public void setShowDetails(boolean showDetails) {
        this.showDetails = showDetails;
        notifyDataSetChanged();
    }

    public void setShowExpandedAttachments(boolean showExpandedAttachments) {
        this.showExpandedAttachments = showExpandedAttachments;
        notifyDataSetChanged();
    }

    // ─── DiffUtil ────────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<Task> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Task>() {
                @Override
                public boolean areItemsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Task oldItem, @NonNull Task newItem) {
                    return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                            && Objects.equals(oldItem.getDescription(), newItem.getDescription())
                            && Objects.equals(oldItem.getDueDate(), newItem.getDueDate())
                            && oldItem.isCompleted() == newItem.isCompleted()
                            && oldItem.getPriority() == newItem.getPriority()
                            && Objects.equals(oldItem.getImageAttachment(), newItem.getImageAttachment())
                            && Objects.equals(oldItem.getVoiceAttachment(), newItem.getVoiceAttachment())
                            && Objects.equals(oldItem.getFileAttachment(), newItem.getFileAttachment());
                }
            };

    // ─── Adapter overrides ───────────────────────────────────────────────────────

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (subtaskDao == null || taskDao == null) {
            TaskDatabase database = TaskDatabase.getInstance(parent.getContext().getApplicationContext());
            subtaskDao = database.subtaskDao();
            taskDao = database.taskDao();
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    // ─── ViewHolder ──────────────────────────────────────────────────────────────

    class TaskViewHolder extends RecyclerView.ViewHolder {

        private final CheckBox cbCompleted;
        private final TextView tvTitle;
        private final TextView tvTime;
        private final TextView tvSubtitle;
        private final ImageView ivPriorityFlag;
        private final LinearLayout layoutAttachments;
        private final ImageView ivAttachmentImage;
        private final TextView tvAttachmentFile;
        private final TextView tvAttachmentVoice;
        private final LinearLayout layoutSubtasks;
        private long boundTaskId = -1L;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            cbCompleted = itemView.findViewById(R.id.cb_task_completed);
            tvTitle = itemView.findViewById(R.id.tv_task_title);
            tvTime = itemView.findViewById(R.id.tv_task_time);
            tvSubtitle = itemView.findViewById(R.id.tv_task_subtitle);
            ivPriorityFlag = itemView.findViewById(R.id.iv_priority_flag);
            layoutAttachments = itemView.findViewById(R.id.layout_task_attachments);
            ivAttachmentImage = itemView.findViewById(R.id.iv_task_attachment_image);
            tvAttachmentFile = itemView.findViewById(R.id.tv_task_attachment_file);
            tvAttachmentVoice = itemView.findViewById(R.id.tv_task_attachment_voice);
            layoutSubtasks = itemView.findViewById(R.id.layout_task_subtasks);
        }

        void bind(Task task) {
            boundTaskId = task.getId();

            // ── Title ────────────────────────────────────────────────────────
            tvTitle.setText(task.getTitle());

            // ── Time ─────────────────────────────────────────────────────────
            if (task.getDueDate() != null) {
                tvTime.setVisibility(View.VISIBLE);
                tvTime.setText(timeFormat.format(new Date(task.getDueDate())));
            } else {
                tvTime.setVisibility(View.GONE);
            }

            // ── Strikethrough nếu đã hoàn thành ─────────────────────────────
            // Bỏ hiệu ứng gạch ngang và không làm mờ theo yêu cầu
            tvTitle.setPaintFlags(tvTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            tvTitle.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_primary));

            // ── Subtitle (description + due time) ───────────────────────────
            String subtitle = buildSubtitle(task);
            if (!showDetails || subtitle.isEmpty()) {
                tvSubtitle.setVisibility(View.GONE);
            } else {
                tvSubtitle.setVisibility(View.VISIBLE);
                tvSubtitle.setText(subtitle);
            }

            // ── Checkbox ─────────────────────────────────────────────────────
            // Tạm bỏ listener trước khi set checked để tránh trigger callback
            cbCompleted.setOnCheckedChangeListener(null);
            cbCompleted.setChecked(task.isCompleted());
            cbCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int adapterPos = getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    checkedChangeListener.onTaskCheckedChanged(getItem(adapterPos), isChecked);
                }
            });

            // ── Priority flag ────────────────────────────────────────────────
            ivPriorityFlag.setColorFilter(getPriorityColor(task.getPriority()));

            // Ẩn flag nếu priority = 0 (None)
            ivPriorityFlag.setVisibility(task.getPriority() == 0 ? View.INVISIBLE : View.VISIBLE);

            bindExpandedAttachments(task);
            bindSubtasks(task);

            // ── Item click ───────────────────────────────────────────────────
            itemView.setOnClickListener(v -> {
                int adapterPos = getAdapterPosition();
                if (adapterPos != RecyclerView.NO_POSITION) {
                    clickListener.onTaskClick(getItem(adapterPos));
                }
            });
        }

        /**
         * Ghép description và thời gian due date thành subtitle.
         * Ví dụ: "Thảo luận Room DB ∙ 14:00"
         */
        private String buildSubtitle(Task task) {
            StringBuilder sb = new StringBuilder();

            String desc = task.getDescription();
            if (desc != null && !desc.trim().isEmpty()) {
                sb.append(desc.trim());
            }

            Long dueDate = task.getDueDate();
            if (dueDate != null && dueDate > 0) {
                String time = timeFormat.format(new Date(dueDate));
                // Chỉ hiện giờ nếu khác 00:00 (tức task có giờ cụ thể)
                if (!"00:00".equals(time)) {
                    if (sb.length() > 0) {
                        sb.append(" ∙ ");
                    }
                    sb.append(time);
                }
            }

            if (!showExpandedAttachments) {
                if (hasAttachment(task.getImageAttachment())) {
                    appendSubtitlePart(sb, "Anh");
                }
                if (hasAttachment(task.getVoiceAttachment())) {
                    appendSubtitlePart(sb, "Voice");
                }
                if (hasAttachment(task.getFileAttachment())) {
                    appendSubtitlePart(sb, isVideoAttachment(task.getFileAttachment()) ? "Video" : "File");
                }
            }

            return sb.toString();
        }

        private void bindExpandedAttachments(Task task) {
            if (layoutAttachments == null || ivAttachmentImage == null
                    || tvAttachmentFile == null || tvAttachmentVoice == null) {
                return;
            }

            ivAttachmentImage.setVisibility(View.GONE);
            ivAttachmentImage.setImageDrawable(null);
            ivAttachmentImage.setOnClickListener(null);

            tvAttachmentFile.setVisibility(View.GONE);
            tvAttachmentFile.setText("");
            tvAttachmentFile.setOnClickListener(null);

            tvAttachmentVoice.setVisibility(View.GONE);
            tvAttachmentVoice.setText("");
            tvAttachmentVoice.setOnClickListener(null);

            if (!showExpandedAttachments) {
                layoutAttachments.setVisibility(View.GONE);
                return;
            }

            boolean hasAnyAttachment = false;

            String imageAttachment = normalizeAttachment(task.getImageAttachment());
            if (imageAttachment != null) {
                Uri imageUri = Uri.parse(imageAttachment);
                try {
                    ivAttachmentImage.setImageURI(imageUri);
                    ivAttachmentImage.setVisibility(View.VISIBLE);
                    String mimeType = defaultMimeType(resolveMimeType(imageUri), "image/*");
                    ivAttachmentImage.setOnClickListener(v -> openAttachment(imageUri, mimeType));
                    hasAnyAttachment = true;
                } catch (SecurityException ignored) {
                }
            }

            String fileAttachment = normalizeAttachment(task.getFileAttachment());
            if (fileAttachment != null) {
                Uri fileUri = Uri.parse(fileAttachment);
                String resolvedMime = defaultMimeType(resolveMimeType(fileUri), "*/*");
                boolean isVideo = isVideoAttachment(fileAttachment) || isVideoMimeType(resolvedMime);
                int labelRes = isVideo ? R.string.detail_attachment_video_label : R.string.detail_attachment_file_label;
                tvAttachmentFile.setText(itemView.getContext().getString(labelRes, getDisplayName(fileUri)));
                tvAttachmentFile.setVisibility(View.VISIBLE);
                tvAttachmentFile.setOnClickListener(v -> openAttachment(fileUri, resolvedMime));
                hasAnyAttachment = true;
            }

            String voiceAttachment = normalizeAttachment(task.getVoiceAttachment());
            if (voiceAttachment != null) {
                Uri voiceUri = Uri.parse(voiceAttachment);
                tvAttachmentVoice.setText(itemView.getContext().getString(
                        R.string.detail_attachment_audio_label,
                        getDisplayName(voiceUri)
                ));
                tvAttachmentVoice.setVisibility(View.VISIBLE);
                tvAttachmentVoice.setOnClickListener(v -> openAttachment(voiceUri, defaultMimeType(resolveMimeType(voiceUri), "audio/*")));
                hasAnyAttachment = true;
            }

            layoutAttachments.setVisibility(hasAnyAttachment ? View.VISIBLE : View.GONE);
        }

        private void bindSubtasks(Task task) {
            if (layoutSubtasks == null) {
                return;
            }

            List<Subtask> cachedSubtasks = subtaskCache.get(task.getId());
            renderSubtasks(task.getId(), cachedSubtasks);
            loadSubtasksAsync(task.getId());
        }

        private void loadSubtasksAsync(long taskId) {
            if (subtaskDao == null) {
                return;
            }

            subtaskExecutor.execute(() -> {
                List<Subtask> loaded = subtaskDao.getSubtasksByTaskIdSync(taskId);
                List<Subtask> copy = loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);

                mainHandler.post(() -> {
                    subtaskCache.put(taskId, copy);
                    if (boundTaskId == taskId) {
                        renderSubtasks(taskId, copy);
                    }
                });
            });
        }

        private void renderSubtasks(long taskId, List<Subtask> subtasks) {
            if (layoutSubtasks == null) {
                return;
            }

            layoutSubtasks.removeAllViews();
            if (subtasks == null || subtasks.isEmpty()) {
                layoutSubtasks.setVisibility(View.GONE);
                return;
            }

            layoutSubtasks.setVisibility(View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());

            for (int i = 0; i < subtasks.size(); i++) {
                Subtask subtask = subtasks.get(i);
                View row = inflater.inflate(R.layout.item_subtask_step, layoutSubtasks, false);

                CheckBox cbSubtaskDone = row.findViewById(R.id.cb_subtask_done);
                TextView tvSubtaskTitle = row.findViewById(R.id.tv_subtask_title);
                ImageButton btnSubtaskPriority = row.findViewById(R.id.btn_subtask_priority);
                MaterialButton btnSubtaskApprove = row.findViewById(R.id.btn_subtask_approve);

                String title = "(" + (i + 1) + ") " + (subtask.getTitle() == null ? "" : subtask.getTitle());
                if (!subtask.isApproved()) {
                    title += " " + itemView.getContext().getString(R.string.subtask_raw_suffix);
                }
                tvSubtaskTitle.setText(title);
                tvSubtaskTitle.setTextColor(ContextCompat.getColor(itemView.getContext(),
                        subtask.isApproved() ? R.color.text_primary : R.color.text_secondary));

                cbSubtaskDone.setOnCheckedChangeListener(null);
                cbSubtaskDone.setChecked(subtask.isCompleted());
                cbSubtaskDone.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    subtask.setCompleted(isChecked);
                    tvSubtaskTitle.setPaintFlags(isChecked
                            ? tvSubtaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                            : tvSubtaskTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    subtaskExecutor.execute(() -> {
                        subtaskDao.markSubtaskCompleted(subtask.getId(), isChecked);
                        if (taskDao != null) {
                            taskDao.touchTask(taskId);
                        }
                    });
                });
                tvSubtaskTitle.setPaintFlags(subtask.isCompleted()
                        ? tvSubtaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                        : tvSubtaskTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);

                btnSubtaskPriority.setColorFilter(getPriorityColor(subtask.getPriority()));
                btnSubtaskPriority.setOnClickListener(v -> {
                    int nextPriority = (subtask.getPriority() + 1) % 4;
                    subtask.setPriority(nextPriority);
                    btnSubtaskPriority.setColorFilter(getPriorityColor(nextPriority));
                    subtaskExecutor.execute(() -> {
                        subtaskDao.updateSubtaskPriority(subtask.getId(), nextPriority);
                        if (taskDao != null) {
                            taskDao.touchTask(taskId);
                        }
                    });
                });

                btnSubtaskApprove.setVisibility(View.GONE);

                layoutSubtasks.addView(row);
            }
        }

        private String normalizeAttachment(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }

        private String getDisplayName(Uri uri) {
            if (uri == null) {
                return "attachment";
            }

            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = itemView.getContext().getContentResolver()
                        .query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (index >= 0) {
                            String displayName = cursor.getString(index);
                            if (!TextUtils.isEmpty(displayName)) {
                                return displayName;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            String path = uri.getLastPathSegment();
            if (TextUtils.isEmpty(path)) {
                return "attachment";
            }
            int slashIndex = path.lastIndexOf('/');
            return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
        }

        private String resolveMimeType(Uri uri) {
            if (uri == null) {
                return "*/*";
            }

            try {
                String mimeType = itemView.getContext().getContentResolver().getType(uri);
                if (!TextUtils.isEmpty(mimeType)) {
                    return mimeType;
                }
            } catch (Exception ignored) {
            }

            String value = uri.toString().toLowerCase(Locale.US);
            if (value.endsWith(".jpg") || value.endsWith(".jpeg") || value.endsWith(".png") || value.endsWith(".webp")) {
                return "image/*";
            }
            if (value.endsWith(".mp4") || value.endsWith(".mkv") || value.endsWith(".mov") || value.endsWith(".avi") || value.endsWith(".webm")) {
                return "video/*";
            }
            if (value.endsWith(".mp3") || value.endsWith(".wav") || value.endsWith(".m4a") || value.endsWith(".aac")) {
                return "audio/*";
            }
            return "*/*";
        }

        private String defaultMimeType(String mimeType, String fallback) {
            return TextUtils.isEmpty(mimeType) ? fallback : mimeType;
        }

        private boolean isVideoMimeType(String mimeType) {
            return mimeType != null && mimeType.startsWith("video/");
        }

        private void openAttachment(Uri uri, String mimeType) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, defaultMimeType(mimeType, "*/*"));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                itemView.getContext().startActivity(intent);
            } catch (ActivityNotFoundException exception) {
                Toast.makeText(itemView.getContext(), R.string.detail_attachment_open_failed, Toast.LENGTH_SHORT).show();
            } catch (SecurityException exception) {
                Toast.makeText(itemView.getContext(), R.string.detail_attachment_permission_missing, Toast.LENGTH_SHORT).show();
            }
        }

        private void appendSubtitlePart(StringBuilder subtitleBuilder, String part) {
            if (subtitleBuilder.length() > 0) {
                subtitleBuilder.append(" ∙ ");
            }
            subtitleBuilder.append(part);
        }

        private boolean hasAttachment(String value) {
            return value != null && !value.trim().isEmpty();
        }

        private boolean isVideoAttachment(String fileAttachment) {
            if (fileAttachment == null) {
                return false;
            }

            String normalized = fileAttachment.toLowerCase(Locale.US);
            return normalized.contains("video")
                    || normalized.endsWith(".mp4")
                    || normalized.endsWith(".mkv")
                    || normalized.endsWith(".mov")
                    || normalized.endsWith(".avi")
                    || normalized.endsWith(".webm");
        }

        /**
         * Trả về mã màu tương ứng với priority level.
         */
        private int getPriorityColor(int priority) {
            switch (priority) {
                case 1:
                    return ContextCompat.getColor(itemView.getContext(), R.color.priority_low);
                case 2:
                    return ContextCompat.getColor(itemView.getContext(), R.color.priority_medium);
                case 3:
                    return ContextCompat.getColor(itemView.getContext(), R.color.priority_high);
                default:
                    return ContextCompat.getColor(itemView.getContext(), R.color.priority_none);
            }
        }
    }
}
