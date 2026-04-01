package hcmute.edu.vn.tickticktodo.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Bitmap;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import android.database.Cursor;
import android.provider.OpenableColumns;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.helper.ImageProcessingHelper;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

/**
 * BottomSheet để thêm công việc mới — phong cách TickTick.
 *
 * Cách sử dụng:
 *   AddTaskBottomSheet.newInstance().show(getSupportFragmentManager(), "AddTask");
 *
 * Có thể truyền title mặc định từ Quick Add bar:
 *   AddTaskBottomSheet.newInstance("Buy groceries").show(...);
 */
public class AddTaskBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_INITIAL_TITLE = "initial_title";

    // Views
    private EditText etTitle;
    private EditText etDescription;
    private Chip chipDueDate;
    private ImageButton btnPriority;
    private ImageButton btnMoreOptions;
    private MaterialButton btnSave;
    private View layoutExtraOptions;
    private View btnAddImage;
    private View btnAddAudio;
    private View btnAddFile;
    private View btnScanFromCamera;
    private View layoutScanProgress;

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
    private ActivityResultLauncher<Uri> cameraCaptureLauncher;

    private Uri pendingCaptureUri;
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();


    // State
    private TaskViewModel taskViewModel;
    private final Calendar selectedDate = Calendar.getInstance(); // mặc định = today
    private int selectedPriority = 0; // 0 = None
    private boolean hasTimeSelected = false;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    // Priority cycle: 0 → 1 → 2 → 3 → 0
    private static final int[] PRIORITY_CYCLE = {0, 1, 2, 3};

    // ─── Factory methods ─────────────────────────────────────────────────────────

    public static AddTaskBottomSheet newInstance() {
        return new AddTaskBottomSheet();
    }

    /**
     * Tạo instance với title được điền sẵn (từ Quick Add bar).
     */
    public static AddTaskBottomSheet newInstance(String initialTitle) {
        AddTaskBottomSheet fragment = new AddTaskBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_TITLE, initialTitle);
        fragment.setArguments(args);
        return fragment;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
        scanExecutor.shutdownNow();
    }

    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Lấy ViewModel của Activity (share giữa Activity và Fragment)
        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupInitialTitle();
        setupTitleWatcher();
        setupDueDateChip();
        setupPriorityButton();
        setupMoreOptions();
        setupPickers();
        setupSaveButton();
        expandAndShowKeyboard();
    }

    // ─── View binding ────────────────────────────────────────────────────────────

    private void initViews(View view) {
        etTitle = view.findViewById(R.id.et_task_title);
        etDescription = view.findViewById(R.id.et_task_description);
        chipDueDate = view.findViewById(R.id.chip_due_date);
        btnPriority = view.findViewById(R.id.btn_priority);
        btnMoreOptions = view.findViewById(R.id.btn_more_options);
        btnSave = view.findViewById(R.id.btn_save_task);
        layoutExtraOptions = view.findViewById(R.id.layout_extra_options);
        btnAddImage = view.findViewById(R.id.btn_add_image);
        btnAddAudio = view.findViewById(R.id.btn_add_audio);
        btnAddFile = view.findViewById(R.id.btn_add_file);
        btnScanFromCamera = view.findViewById(R.id.btn_scan_camera);
        layoutScanProgress = view.findViewById(R.id.layout_scan_progress);

        llAttachments = view.findViewById(R.id.ll_attachments);
        llVoicePlayer = view.findViewById(R.id.ll_voice_player);
        ivAttachmentImage = view.findViewById(R.id.iv_attachment_image);
        ivPlayPause = view.findViewById(R.id.iv_play_pause);
        tvAttachmentFile = view.findViewById(R.id.tv_attachment_file);
        tvVoiceDuration = view.findViewById(R.id.tv_voice_duration);
        sbVoiceProgress = view.findViewById(R.id.sb_voice_progress);

        if (layoutScanProgress != null) {
            layoutScanProgress.setVisibility(View.GONE);
        }

    }

    /**
     * Nếu được mở từ Quick Add bar → pre-fill title.
     */
    private void setupInitialTitle() {
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_INITIAL_TITLE)) {
            String title = args.getString(ARG_INITIAL_TITLE, "");
            etTitle.setText(title);
            etTitle.setSelection(title.length()); // cursor cuối
        }

        // Mặc định due date chip hiển thị "Today"
        updateDueDateChip();
    }

    // ─── Title watcher → enable/disable Save ─────────────────────────────────────

    private void setupTitleWatcher() {
        etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSave.setEnabled(!s.toString().trim().isEmpty());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    // ─── Due Date picker ─────────────────────────────────────────────────────────

    private void setupDueDateChip() {
        chipDueDate.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        DatePickerDialog picker = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    // Tự động mở TimePicker ngay sau khi chọn ngày
                    showTimePicker();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        picker.show();
    }

    private void showTimePicker() {
        int hour = hasTimeSelected ? selectedDate.get(Calendar.HOUR_OF_DAY) : 9;
        int minute = hasTimeSelected ? selectedDate.get(Calendar.MINUTE) : 0;

        TimePickerDialog timePicker = new TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minuteOfDay) -> {
                    selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedDate.set(Calendar.MINUTE, minuteOfDay);
                    selectedDate.set(Calendar.SECOND, 0);
                    selectedDate.set(Calendar.MILLISECOND, 0);
                    hasTimeSelected = true;
                    updateDueDateChip();
                },
                hour, minute, true // is24HourView
        );
        timePicker.show();
    }

    /**
     * Cập nhật text trên chip: "Today", "Tomorrow", hoặc "Wed, Mar 5".
     */
    private void updateDueDateChip() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);

        Calendar selected = (Calendar) selectedDate.clone();
        selected.set(Calendar.HOUR_OF_DAY, 0);
        selected.set(Calendar.MINUTE, 0);
        selected.set(Calendar.SECOND, 0);
        selected.set(Calendar.MILLISECOND, 0);

        String dateText;
        if (selected.getTimeInMillis() == today.getTimeInMillis()) {
            dateText = getString(R.string.label_today);
        } else if (selected.getTimeInMillis() == tomorrow.getTimeInMillis()) {
            dateText = getString(R.string.label_tomorrow);
        } else {
            dateText = dateFormat.format(selected.getTime());
        }

        if (hasTimeSelected) {
            dateText += " " + timeFormat.format(selectedDate.getTime());
        }
        chipDueDate.setText(dateText);
    }

    // ─── Priority button (cycle through 0 → 1 → 2 → 3 → 0) ─────────────────────

    private void setupPriorityButton() {
        updatePriorityIcon();

        btnPriority.setOnClickListener(v -> {
            // Cycle to next priority
            selectedPriority = (selectedPriority + 1) % PRIORITY_CYCLE.length;
            updatePriorityIcon();
        });
    }

    
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

        cameraCaptureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && pendingCaptureUri != null) {
                handleCameraCapturedImage(pendingCaptureUri);
            } else {
                toggleScanProgress(false);
            }
        });
    }

    private void launchCameraCapture() {
        try {
            File cacheDir = new File(requireContext().getCacheDir(), "scan_images");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                Toast.makeText(requireContext(), R.string.scan_prepare_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            File captureFile = File.createTempFile(
                    "scan_" + System.currentTimeMillis(),
                    ".jpg",
                    cacheDir
            );

            pendingCaptureUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    captureFile
            );

            toggleScanProgress(true);
            cameraCaptureLauncher.launch(pendingCaptureUri);
        } catch (IOException e) {
            toggleScanProgress(false);
            Toast.makeText(requireContext(), R.string.scan_prepare_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleCameraCapturedImage(Uri imageUri) {
        toggleScanProgress(true);
        scanExecutor.execute(() -> {
            try {
                Bitmap bitmap = ImageProcessingHelper.decodeSampledBitmapFromUri(
                        requireContext(),
                        imageUri,
                        1600,
                        1600
                );

                if (bitmap == null) {
                    postScanError(getString(R.string.scan_image_decode_failed));
                    return;
                }

                String prompt = "Day la hinh anh chua danh sach cong viec/bai tap. "
                        + "Hay nhan dien van ban va trich xuat tung dau viec. "
                        + "Tra ve JSON array chi gom tieu de cong viec. "
                        + "Chi tra ve JSON.";

                GeminiManager.getInstance().generateVisionResponse(bitmap, prompt, new GeminiManager.ResponseCallback() {
                    @Override
                    public void onSuccess(String responseText) {
                        List<String> titles = parseTaskTitlesFromAi(responseText);
                        if (titles.isEmpty()) {
                            postScanError(getString(R.string.scan_no_tasks_found));
                            return;
                        }

                        List<Task> tasks = new ArrayList<>();
                        long dueDate = getScanDefaultDueDate();
                        for (String title : titles) {
                            if (title == null) {
                                continue;
                            }
                            String cleanTitle = title.trim();
                            if (cleanTitle.isEmpty()) {
                                continue;
                            }
                            tasks.add(new Task(cleanTitle, "", dueDate, false, 0));
                        }

                        if (tasks.isEmpty()) {
                            postScanError(getString(R.string.scan_no_tasks_found));
                            return;
                        }

                        taskViewModel.insertBatch(tasks, () -> {
                            if (!isAdded() || getView() == null) {
                                return;
                            }
                            toggleScanProgress(false);
                            Snackbar.make(getView(),
                                            getString(R.string.scan_tasks_added_count, tasks.size()),
                                            Snackbar.LENGTH_LONG)
                                    .show();
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        postScanError(errorMessage);
                    }
                });
            } catch (Exception e) {
                postScanError(getString(R.string.scan_processing_failed));
            }
        });
    }

    private void postScanError(String message) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            toggleScanProgress(false);
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

    private long getScanDefaultDueDate() {
        Calendar dueCal = (Calendar) selectedDate.clone();
        dueCal.set(Calendar.HOUR_OF_DAY, 0);
        dueCal.set(Calendar.MINUTE, 0);
        dueCal.set(Calendar.SECOND, 0);
        dueCal.set(Calendar.MILLISECOND, 0);
        return dueCal.getTimeInMillis();
    }

    private List<String> parseTaskTitlesFromAi(String rawResponse) {
        List<String> titles = new ArrayList<>();
        try {
            String jsonArrayText = extractJsonArray(rawResponse);
            JSONArray jsonArray = new JSONArray(jsonArrayText);
            for (int i = 0; i < jsonArray.length(); i++) {
                Object item = jsonArray.opt(i);
                if (item instanceof String) {
                    titles.add((String) item);
                } else if (item instanceof JSONObject) {
                    String title = ((JSONObject) item).optString("title", "");
                    if (!title.trim().isEmpty()) {
                        titles.add(title.trim());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return titles;
    }

    private String extractJsonArray(String raw) {
        if (raw == null) {
            return "[]";
        }

        String text = raw.trim();
        if (text.startsWith("```") && text.endsWith("```")) {
            int firstLineBreak = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineBreak != -1 && lastFence > firstLineBreak) {
                text = text.substring(firstLineBreak + 1, lastFence).trim();
            }
        }

        int left = text.indexOf('[');
        int right = text.lastIndexOf(']');
        if (left >= 0 && right > left) {
            return text.substring(left, right + 1);
        }
        return text;
    }

    private void toggleScanProgress(boolean loading) {
        if (layoutScanProgress == null) {
            return;
        }
        layoutScanProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnSave != null) {
            btnSave.setEnabled(!loading && !etTitle.getText().toString().trim().isEmpty());
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

    private void updatePriorityIcon() {
        int colorRes;
        switch (selectedPriority) {
            case 1:  colorRes = R.color.priority_low;    break;
            case 2:  colorRes = R.color.priority_medium;  break;
            case 3:  colorRes = R.color.priority_high;    break;
            default: colorRes = R.color.priority_none;    break;
        }
        btnPriority.setColorFilter(ContextCompat.getColor(requireContext(), colorRes));
    }

    // ─── More options (Image, Audio, File) ───────────────────────────────────────

    private void setupMoreOptions() {
        btnMoreOptions.setOnClickListener(v -> {
            if (layoutExtraOptions.getVisibility() == View.GONE) {
                layoutExtraOptions.setVisibility(View.VISIBLE);
            } else {
                layoutExtraOptions.setVisibility(View.GONE);
            }
        });

        btnAddImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        btnAddAudio.setOnClickListener(v -> audioPickerLauncher.launch("audio/*"));

        btnAddFile.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

        if (btnScanFromCamera != null) {
            btnScanFromCamera.setOnClickListener(v -> launchCameraCapture());
        }
    }

    // ─── Save ────────────────────────────────────────────────────────────────────

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> saveTask());
    }

    private void saveTask() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) return;

        String description = etDescription.getText().toString().trim();

        // Due date: nếu có chọn giờ thì giữ nguyên, không thì 00:00
        Calendar dueCal = (Calendar) selectedDate.clone();
        if (!hasTimeSelected) {
            dueCal.set(Calendar.HOUR_OF_DAY, 0);
            dueCal.set(Calendar.MINUTE, 0);
        }
        dueCal.set(Calendar.SECOND, 0);
        dueCal.set(Calendar.MILLISECOND, 0);

        Task newTask = new Task(title, description, dueCal.getTimeInMillis(), false, selectedPriority);
        taskViewModel.insert(newTask);

        dismiss();
    }

    // ─── UX: tự mở rộng bottom sheet và show keyboard ───────────────────────────

    private void expandAndShowKeyboard() {
        // Đảm bảo bottom sheet mở hết (expanded) thay vì half-expanded
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();

            dialog.setOnShowListener(d -> {
                BottomSheetDialog bsd = (BottomSheetDialog) d;
                View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setSkipCollapsed(true);
                }
            });

            // Mở keyboard và focus vào title
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE |
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        }
        etTitle.requestFocus();
    }
}
