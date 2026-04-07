package hcmute.edu.vn.tickticktodo.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.adapter.SubtaskStepAdapter;
import hcmute.edu.vn.tickticktodo.helper.AiTaskBreakdownHelper;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.model.Subtask;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

/**
 * BottomSheet hiển thị chi tiết và chỉnh sửa Task.
 */
public class TaskDetailBottomSheet extends BottomSheetDialogFragment {

    public static final String ARG_TASK_ID = "arg_task_id";

    private TaskViewModel taskViewModel;

    // Views
    private ImageButton btnHeaderClose;
    private ImageButton btnHeaderSave;
    private ImageButton btnExpandCollapse;
    private CheckBox cbCompleted;
    private EditText etTitle;
    private EditText etDescription;
    private Chip chipDueDate;
    private MaterialButton btnAiBreakdown;
    private ProgressBar progressAiBreakdown;
    private LinearLayout btnPriorityNone;
    private LinearLayout btnPriorityLow;
    private LinearLayout btnPriorityMedium;
    private LinearLayout btnPriorityHigh;
    private LinearLayout llAttachments;
    private ImageView ivAttachmentImage;
    private ChipGroup chipGroupAttachments;
    private RecyclerView rvSubtasks;
    private TextView tvSubtasksEmpty;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private SubtaskStepAdapter subtaskAdapter;
    private LiveData<List<Subtask>> subtasksLiveData;
    private long observedTaskId = -1L;

    // State
    private Task currentTask;
    private Long selectedDueDate;
    private int selectedPriority;
    private boolean hasTimeSelected;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    public static TaskDetailBottomSheet newInstance(long taskId) {
        TaskDetailBottomSheet fragment = new TaskDetailBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_TASK_ID, taskId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            View bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) {
                return;
            }
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
            updateExpandIcon(bottomSheetBehavior.getState());
            bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    updateExpandIcon(newState);
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                }
            });
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_task_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupToolbar();
        setupPriorityButtons();
        setupDueDateChip();
        setupSubtaskList();
        setupAiBreakdown();
        loadTaskFromArgs();
    }

    private void initViews(View view) {
        btnHeaderClose = view.findViewById(R.id.btn_header_close);
        btnHeaderSave = view.findViewById(R.id.btn_header_save);
        btnExpandCollapse = view.findViewById(R.id.btn_expand_collapse);
        cbCompleted = view.findViewById(R.id.cb_completed);
        etTitle = view.findViewById(R.id.et_title);
        etDescription = view.findViewById(R.id.et_description);
        chipDueDate = view.findViewById(R.id.chip_due_date);
        btnAiBreakdown = view.findViewById(R.id.btn_ai_breakdown);
        progressAiBreakdown = view.findViewById(R.id.progress_ai_breakdown);
        btnPriorityNone = view.findViewById(R.id.btn_priority_none);
        btnPriorityLow = view.findViewById(R.id.btn_priority_low);
        btnPriorityMedium = view.findViewById(R.id.btn_priority_medium);
        btnPriorityHigh = view.findViewById(R.id.btn_priority_high);
        llAttachments = view.findViewById(R.id.ll_attachments);
        ivAttachmentImage = view.findViewById(R.id.iv_attachment_image);
        chipGroupAttachments = view.findViewById(R.id.chip_group_attachments);
        rvSubtasks = view.findViewById(R.id.rv_subtasks);
        tvSubtasksEmpty = view.findViewById(R.id.tv_subtasks_empty);

        View.OnFocusChangeListener focusChangeListener = (v, hasFocus) -> {
            if (hasFocus) {
                expandBottomSheet();
            }
        };
        etTitle.setOnFocusChangeListener(focusChangeListener);
        etDescription.setOnFocusChangeListener(focusChangeListener);

        View.OnClickListener clickListener = v -> expandBottomSheet();
        etTitle.setOnClickListener(clickListener);
        etDescription.setOnClickListener(clickListener);
    }

    private void setupSubtaskList() {
        if (rvSubtasks == null) {
            return;
        }

        subtaskAdapter = new SubtaskStepAdapter(new SubtaskStepAdapter.Listener() {
            @Override
            public void onSubtaskCheckedChanged(Subtask subtask, boolean isChecked) {
                taskViewModel.markSubtaskCompleted(subtask, isChecked);
            }

            @Override
            public void onSubtaskApproved(Subtask subtask) {
                taskViewModel.setSubtaskApproved(subtask, true);
            }

            @Override
            public void onSubtaskPriorityChanged(Subtask subtask, int newPriority) {
                taskViewModel.updateSubtaskPriority(subtask, newPriority);
            }
        });

        rvSubtasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSubtasks.setAdapter(subtaskAdapter);
    }

    private void setupToolbar() {
        btnHeaderClose.setOnClickListener(v -> dismiss());

        btnHeaderSave.setOnClickListener(v -> {
            if (saveTask()) {
                dismiss();
            }
        });

        btnExpandCollapse.setOnClickListener(v -> {
            if (bottomSheetBehavior == null) {
                return;
            }
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
    }

    private void updateExpandIcon(int state) {
        if (btnExpandCollapse == null) {
            return;
        }
        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            btnExpandCollapse.setImageResource(R.drawable.ic_back);
        } else {
            btnExpandCollapse.setImageResource(R.drawable.ic_expand);
        }
    }

    private void expandBottomSheet() {
        if (bottomSheetBehavior != null
                && bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void loadTaskFromArgs() {
        Bundle args = getArguments();
        long taskId = (args != null) ? args.getLong(ARG_TASK_ID, -1L) : -1L;
        if (taskId == -1L) {
            Toast.makeText(requireContext(), "Không tìm thấy task", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        taskViewModel.getTaskById(taskId).observe(getViewLifecycleOwner(), task -> {
            if (task == null) {
                Toast.makeText(requireContext(), "Task không tồn tại", Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }
            currentTask = task;
            populateUI(task);
        });
    }

    private void populateUI(Task task) {
        etTitle.setText(task.getTitle());
        etDescription.setText(task.getDescription());
        cbCompleted.setChecked(task.isCompleted());

        selectedDueDate = task.getDueDate();
        if (selectedDueDate != null) {
            Calendar check = Calendar.getInstance();
            check.setTimeInMillis(selectedDueDate);
            hasTimeSelected = check.get(Calendar.HOUR_OF_DAY) != 0
                    || check.get(Calendar.MINUTE) != 0;
        } else {
            hasTimeSelected = false;
        }
        updateDueDateChip(selectedDueDate);

        selectedPriority = task.getPriority();
        highlightPriorityButton(selectedPriority);
        bindAttachments(task);
        observeSubtasks(task.getId());
    }

    private void observeSubtasks(long taskId) {
        if (observedTaskId == taskId) {
            return;
        }
        observedTaskId = taskId;

        if (subtasksLiveData != null) {
            subtasksLiveData.removeObservers(getViewLifecycleOwner());
        }

        subtasksLiveData = taskViewModel.getSubtasksByTaskId(taskId);
        subtasksLiveData.observe(getViewLifecycleOwner(), this::renderSubtasks);
    }

    private void renderSubtasks(List<Subtask> subtasks) {
        if (subtaskAdapter != null) {
            subtaskAdapter.submitList(subtasks);
        }
        if (tvSubtasksEmpty != null) {
            boolean isEmpty = subtasks == null || subtasks.isEmpty();
            tvSubtasksEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void bindAttachments(Task task) {
        if (llAttachments == null || chipGroupAttachments == null || ivAttachmentImage == null) {
            return;
        }

        chipGroupAttachments.removeAllViews();
        ivAttachmentImage.setVisibility(View.GONE);
        ivAttachmentImage.setImageDrawable(null);
        ivAttachmentImage.setOnClickListener(null);

        boolean hasAnyAttachment = false;

        String imageAttachment = normalizeAttachment(task.getImageAttachment());
        if (imageAttachment != null) {
            Uri imageUri = Uri.parse(imageAttachment);
            try {
                ivAttachmentImage.setImageURI(imageUri);
                ivAttachmentImage.setVisibility(View.VISIBLE);
                ivAttachmentImage.setOnClickListener(v -> openAttachment(imageUri, "image/*"));
                hasAnyAttachment = true;
            } catch (SecurityException securityException) {
                Toast.makeText(requireContext(), R.string.detail_attachment_permission_missing, Toast.LENGTH_SHORT).show();
            }
        }

        String voiceAttachment = normalizeAttachment(task.getVoiceAttachment());
        if (voiceAttachment != null) {
            Uri voiceUri = Uri.parse(voiceAttachment);
            String label = getString(R.string.detail_attachment_audio_label, getDisplayName(voiceUri));
            addAttachmentChip(label, R.drawable.ic_audio, voiceUri, "audio/*");
            hasAnyAttachment = true;
        }

        String fileAttachment = normalizeAttachment(task.getFileAttachment());
        if (fileAttachment != null) {
            Uri fileUri = Uri.parse(fileAttachment);
            String label = getString(R.string.detail_attachment_file_label, getDisplayName(fileUri));
            addAttachmentChip(label, R.drawable.ic_attach_file, fileUri, "*/*");
            hasAnyAttachment = true;
        }

        llAttachments.setVisibility(hasAnyAttachment ? View.VISIBLE : View.GONE);
    }

    private void addAttachmentChip(String text, int iconRes, Uri uri, String mimeType) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setChipIconResource(iconRes);
        chip.setClickable(true);
        chip.setCheckable(false);
        chip.setOnClickListener(v -> openAttachment(uri, mimeType));
        chipGroupAttachments.addView(chip);
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
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (columnIndex >= 0) {
                        String displayName = cursor.getString(columnIndex);
                        if (displayName != null && !displayName.trim().isEmpty()) {
                            return displayName;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String lastPath = uri.getLastPathSegment();
        if (lastPath == null || lastPath.trim().isEmpty()) {
            return "attachment";
        }
        int slashIdx = lastPath.lastIndexOf('/');
        return slashIdx >= 0 ? lastPath.substring(slashIdx + 1) : lastPath;
    }

    private void openAttachment(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException activityNotFoundException) {
            Toast.makeText(requireContext(), R.string.detail_attachment_open_failed, Toast.LENGTH_SHORT).show();
        } catch (SecurityException securityException) {
            Toast.makeText(requireContext(), R.string.detail_attachment_permission_missing, Toast.LENGTH_SHORT).show();
        }
    }

    private void setupDueDateChip() {
        chipDueDate.setOnClickListener(v -> {
            expandBottomSheet();
            showDatePicker();
        });
    }

    private void setupAiBreakdown() {
        if (btnAiBreakdown == null) {
            return;
        }
        btnAiBreakdown.setOnClickListener(v -> requestAiBreakdown());
    }

    private void requestAiBreakdown() {
        if (currentTask == null) {
            Toast.makeText(requireContext(), R.string.ai_breakdown_parse_error, Toast.LENGTH_SHORT).show();
            return;
        }

        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), R.string.ai_breakdown_title_empty, Toast.LENGTH_SHORT).show();
            etTitle.requestFocus();
            return;
        }

        setAiBreakdownLoading(true);
        GeminiManager.getInstance().generateResponse(
                AiTaskBreakdownHelper.buildPrompt(title),
                new GeminiManager.ResponseCallback() {
                    @Override
                    public void onSuccess(String responseText) {
                        try {
                            List<String> steps = AiTaskBreakdownHelper.parseSteps(responseText);
                            taskViewModel.applyAiBreakdownToSubtasks(currentTask.getId(), steps, () -> {
                                setAiBreakdownLoading(false);
                                Toast.makeText(requireContext(), R.string.ai_breakdown_success, Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception parseError) {
                            Toast.makeText(requireContext(), R.string.ai_breakdown_parse_error, Toast.LENGTH_SHORT).show();
                            setAiBreakdownLoading(false);
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setAiBreakdownLoading(false);
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setAiBreakdownLoading(boolean loading) {
        if (btnAiBreakdown != null) {
            btnAiBreakdown.setEnabled(!loading);
            btnAiBreakdown.setText(loading
                    ? getString(R.string.ai_breakdown_loading)
                    : getString(R.string.ai_breakdown_button));
        }
        if (progressAiBreakdown != null) {
            progressAiBreakdown.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        if (selectedDueDate != null) {
            cal.setTimeInMillis(selectedDueDate);
        }

        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth, 0, 0, 0);
                    selected.set(Calendar.MILLISECOND, 0);
                    selectedDueDate = selected.getTimeInMillis();
                    showTimePicker();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        if (selectedDueDate != null) {
            cal.setTimeInMillis(selectedDueDate);
        }
        int hour = hasTimeSelected ? cal.get(Calendar.HOUR_OF_DAY) : 9;
        int minute = hasTimeSelected ? cal.get(Calendar.MINUTE) : 0;

        TimePickerDialog timePicker = new TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minuteOfDay) -> {
                    Calendar updated = Calendar.getInstance();
                    if (selectedDueDate != null) {
                        updated.setTimeInMillis(selectedDueDate);
                    }
                    updated.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    updated.set(Calendar.MINUTE, minuteOfDay);
                    updated.set(Calendar.SECOND, 0);
                    updated.set(Calendar.MILLISECOND, 0);
                    selectedDueDate = updated.getTimeInMillis();
                    hasTimeSelected = true;
                    updateDueDateChip(selectedDueDate);
                },
                hour, minute, true
        );
        timePicker.show();
    }

    private void updateDueDateChip(Long timestamp) {
        if (timestamp == null) {
            chipDueDate.setText(getString(R.string.label_no_date));
            return;
        }

        Calendar today = Calendar.getInstance();
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(timestamp);

        String chipText;
        if (isSameDay(selected, today)) {
            chipText = getString(R.string.label_today);
        } else if (isSameDay(selected, tomorrow)) {
            chipText = getString(R.string.label_tomorrow);
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
            chipText = fmt.format(selected.getTime());
        }

        if (hasTimeSelected) {
            chipText += " " + timeFormat.format(selected.getTime());
        }
        chipDueDate.setText(chipText);
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void setupPriorityButtons() {
        btnPriorityNone.setOnClickListener(v -> selectPriority(0));
        btnPriorityLow.setOnClickListener(v -> selectPriority(1));
        btnPriorityMedium.setOnClickListener(v -> selectPriority(2));
        btnPriorityHigh.setOnClickListener(v -> selectPriority(3));
    }

    private void selectPriority(int priority) {
        expandBottomSheet();
        selectedPriority = priority;
        highlightPriorityButton(priority);
    }

    private void highlightPriorityButton(int priority) {
        btnPriorityNone.setAlpha(priority == 0 ? 1f : 0.35f);
        btnPriorityLow.setAlpha(priority == 1 ? 1f : 0.35f);
        btnPriorityMedium.setAlpha(priority == 2 ? 1f : 0.35f);
        btnPriorityHigh.setAlpha(priority == 3 ? 1f : 0.35f);
    }

    private boolean saveTask() {
        if (currentTask == null) {
            return false;
        }

        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError(getString(R.string.error_title_empty));
            etTitle.requestFocus();
            return false;
        }

        currentTask.setTitle(title);
        currentTask.setDescription(etDescription.getText().toString().trim());
        currentTask.setDueDate(selectedDueDate);
        currentTask.setPriority(selectedPriority);
        currentTask.setCompleted(cbCompleted.isChecked());

        taskViewModel.update(currentTask);
        return true;
    }
}
