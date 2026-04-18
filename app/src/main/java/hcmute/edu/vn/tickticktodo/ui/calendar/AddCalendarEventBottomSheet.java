package hcmute.edu.vn.tickticktodo.ui.calendar;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.ui.task.TaskViewModel;

/**
 * BottomSheet thêm sự kiện lịch mới.
 *
 * Cách dùng:
 * <pre>
 *   AddCalendarEventBottomSheet sheet = AddCalendarEventBottomSheet.newInstance(selectedDateMillis);
 *   sheet.show(getSupportFragmentManager(), "add_event");
 * </pre>
 *
 * Sau khi Lưu, listener {@link OnEventSavedListener#onEventSaved()} được gọi
 * để CalendarActivity refresh lại lưới tháng.
 */
public class AddCalendarEventBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "AddCalendarEventSheet";
    private static final String ARG_DATE_MILLIS = "arg_date_millis";

    // ─── Callback ────────────────────────────────────────────────────────────────
    public interface OnEventSavedListener {
        void onEventSaved();
    }

    private OnEventSavedListener savedListener;

    public void setOnEventSavedListener(OnEventSavedListener listener) {
        this.savedListener = listener;
    }

    // ─── Factory ─────────────────────────────────────────────────────────────────
    public static AddCalendarEventBottomSheet newInstance(long preDateMillis) {
        AddCalendarEventBottomSheet sheet = new AddCalendarEventBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_DATE_MILLIS, preDateMillis);
        sheet.setArguments(args);
        return sheet;
    }

    // ─── Fields ──────────────────────────────────────────────────────────────────
    private TaskViewModel taskViewModel;

    private TextInputEditText etTitle;
    private TextInputEditText etDate;
    private TextInputEditText etTime;
    private TextInputEditText etDescription;
    private TextInputEditText etLocation;
    private TextInputEditText etDuration;
    private Spinner spinnerRecurrence;

    /** Calendar giữ ngày+giờ mà user đã chọn. */
    private final Calendar selectedCalendar = Calendar.getInstance();

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", new Locale("vi", "VN"));

    // ─── Lifecycle ───────────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Áp theme bo góc cho BottomSheet
        setStyle(STYLE_NORMAL, R.style.Theme_TickTickToDo_BottomSheetDialog);

        // Lấy ngày được chọn trước từ argument; mặc định = hôm nay
        long preDate = requireArguments().getLong(ARG_DATE_MILLIS,
                System.currentTimeMillis());
        selectedCalendar.setTimeInMillis(preDate);
        // Đặt giờ mặc định = 8:00 sáng
        selectedCalendar.set(Calendar.HOUR_OF_DAY, 8);
        selectedCalendar.set(Calendar.MINUTE, 0);
        selectedCalendar.set(Calendar.SECOND, 0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_calendar_event,
                container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskViewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);

        bindViews(view);
        setupRecurrenceSpinner();
        setupDateTimePickers();
        setupButtons(view);

        // Pre-fill ngày/giờ
        refreshDateTimeDisplay();
    }

    // ─── Init ────────────────────────────────────────────────────────────────────

    private void bindViews(View view) {
        etTitle       = view.findViewById(R.id.et_event_title);
        etDate        = view.findViewById(R.id.et_event_date);
        etTime        = view.findViewById(R.id.et_event_time);
        etDescription = view.findViewById(R.id.et_event_description);
        etLocation    = view.findViewById(R.id.et_event_location);
        etDuration    = view.findViewById(R.id.et_event_duration);
        spinnerRecurrence = view.findViewById(R.id.spinner_recurrence);
    }

    private void setupRecurrenceSpinner() {
        String[] items = {
                getString(R.string.cal_recurrence_none),
                getString(R.string.cal_recurrence_weekly),
                getString(R.string.cal_recurrence_monthly)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRecurrence.setAdapter(adapter);
    }

    private void setupDateTimePickers() {
        // Click vào field ngày → mở DatePickerDialog
        etDate.setOnClickListener(v -> {
            int year  = selectedCalendar.get(Calendar.YEAR);
            int month = selectedCalendar.get(Calendar.MONTH);
            int day   = selectedCalendar.get(Calendar.DAY_OF_MONTH);
            new DatePickerDialog(requireContext(), (datePicker, y, m, d) -> {
                selectedCalendar.set(Calendar.YEAR, y);
                selectedCalendar.set(Calendar.MONTH, m);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, d);
                refreshDateTimeDisplay();
            }, year, month, day).show();
        });

        // Click vào field giờ → mở TimePickerDialog
        etTime.setOnClickListener(v -> {
            int hour   = selectedCalendar.get(Calendar.HOUR_OF_DAY);
            int minute = selectedCalendar.get(Calendar.MINUTE);
            new TimePickerDialog(requireContext(), (timePicker, h, min) -> {
                selectedCalendar.set(Calendar.HOUR_OF_DAY, h);
                selectedCalendar.set(Calendar.MINUTE, min);
                refreshDateTimeDisplay();
            }, hour, minute, true).show(); // true = 24h format
        });
    }

    private void setupButtons(View view) {
        Button btnCancel = view.findViewById(R.id.btn_cancel_event);
        Button btnSave   = view.findViewById(R.id.btn_save_event);

        btnCancel.setOnClickListener(v -> dismiss());
        btnSave.setOnClickListener(v -> saveEvent());
    }

    private void refreshDateTimeDisplay() {
        etDate.setText(dateFormat.format(selectedCalendar.getTime()));
        etTime.setText(timeFormat.format(selectedCalendar.getTime()));
    }

    // ─── Save ────────────────────────────────────────────────────────────────────

    private void saveEvent() {
        String title = etTitle.getText() != null
                ? etTitle.getText().toString().trim() : "";

        if (title.isEmpty()) {
            etTitle.setError(getString(R.string.error_title_empty));
            etTitle.requestFocus();
            return;
        }

        // Lấy dữ liệu từ các field
        String description = etDescription.getText() != null
                ? etDescription.getText().toString().trim() : "";
        String location = etLocation.getText() != null
                ? etLocation.getText().toString().trim() : "";

        int duration = 0;
        String durationStr = etDuration.getText() != null
                ? etDuration.getText().toString().trim() : "";
        if (!durationStr.isEmpty()) {
            try { duration = Integer.parseInt(durationStr); }
            catch (NumberFormatException ignored) {}
        }

        int recurrence = spinnerRecurrence.getSelectedItemPosition();
        // 0=Không lặp, 1=Hàng tuần, 2=Hàng tháng

        // Tạo Task với dữ liệu mới
        Task task = new Task(title, description,
                selectedCalendar.getTimeInMillis(), false, 0 /* priority=None */);
        task.setLocation(location.isEmpty() ? null : location);
        task.setDuration(duration);
        task.setRecurrence(recurrence);

        taskViewModel.insert(task);

        Toast.makeText(requireContext(),
                getString(R.string.cal_event_saved_toast), Toast.LENGTH_SHORT).show();

        if (savedListener != null) savedListener.onEventSaved();
        dismiss();
    }
}
