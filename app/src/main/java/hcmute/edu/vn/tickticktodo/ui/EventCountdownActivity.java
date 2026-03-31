package hcmute.edu.vn.tickticktodo.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.lifecycle.ViewModelProvider;
import hcmute.edu.vn.tickticktodo.viewmodel.CountdownEventViewModel;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.adapter.CountdownEventAdapter;
import hcmute.edu.vn.tickticktodo.model.CountdownEvent;

public class EventCountdownActivity extends BaseActivity {

    private RecyclerView rvEvents;
    private CountdownEventAdapter adapter;
    private List<CountdownEvent> eventList;
    private CountdownEventViewModel viewModel;
    private FloatingActionButton fabAdd;
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_event_countdown);

        initViews();
        loadData();
    }

    private void initViews() {
        rvEvents = findViewById(R.id.rv_events);
        fabAdd = findViewById(R.id.fab_add_event);
        btnBack = findViewById(R.id.btn_back);

        rvEvents.setLayoutManager(new LinearLayoutManager(this));
        eventList = new ArrayList<>();
        adapter = new CountdownEventAdapter(eventList);
        rvEvents.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(CountdownEventViewModel.class);
        viewModel.getAllEvents().observe(this, events -> {
            // refresh calculation for each event before displaying
            for (CountdownEvent e : events) {
                e.calculateDays();
            }
            adapter.setEvents(events);
            if (events != null && events.isEmpty()) {
                loadData(); // Load defaults if empty
            }
        });

        setupSwipeToDelete();

        btnBack.setOnClickListener(v -> finish());
        fabAdd.setOnClickListener(v -> showAddEventDialog());
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                CountdownEvent eventToDelete = adapter.getEvents().get(position);
                viewModel.delete(eventToDelete);
                Toast.makeText(EventCountdownActivity.this, "Đã xóa sự kiện", Toast.LENGTH_SHORT).show();
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvEvents);
    }

    private void loadData() {
        // Load default holidays and weekends
        eventList.clear();
        
        // 1. Weekend
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysUntilSaturday = (Calendar.SATURDAY - dayOfWeek + 7) % 7;
        if (daysUntilSaturday == 0) daysUntilSaturday = 7; // if today is Saturday, next weekend
        cal.add(Calendar.DAY_OF_YEAR, daysUntilSaturday);
        eventList.add(new CountdownEvent("Cuối tuần", cal.getTimeInMillis()));

        // 2. New Year (Tết Dương lịch)
        Calendar nyCal = Calendar.getInstance();
        nyCal.set(Calendar.MONTH, Calendar.JANUARY);
        nyCal.set(Calendar.DAY_OF_MONTH, 1);
        if (nyCal.getTimeInMillis() < System.currentTimeMillis()) {
            nyCal.add(Calendar.YEAR, 1);
        }
        eventList.add(new CountdownEvent("Tết Dương lịch", nyCal.getTimeInMillis()));

        // 3. TickTick Usage (Past) - 21 days ago as an example
        Calendar pastCal = Calendar.getInstance();
        pastCal.add(Calendar.DAY_OF_YEAR, -21);
        eventList.add(new CountdownEvent("Sử dụng TickTick", pastCal.getTimeInMillis()));

        adapter.setEvents(eventList);
    }

    private void showAddEventDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_event_add);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        EditText etTitle = dialog.findViewById(R.id.et_event_title);
        TextView tvDate = dialog.findViewById(R.id.tv_event_date);
        Button btnCancel = dialog.findViewById(R.id.btn_event_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_event_save);

        final Calendar selectedDate = Calendar.getInstance();
        tvDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(this,
                    (view, year, month, dayOfMonth) -> {
                        selectedDate.set(year, month, dayOfMonth);
                        tvDate.setText(dayOfMonth + "/" + (month + 1) + "/" + year);
                    },
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tên", Toast.LENGTH_SHORT).show();
                return;
            }
            if (tvDate.getText().toString().equals("Ngày sự kiện")) {
                Toast.makeText(this, "Vui lòng chọn ngày", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.insert(new CountdownEvent(title, selectedDate.getTimeInMillis()));
            rvEvents.scrollToPosition(0);
            dialog.dismiss();
        });

        dialog.show();
    }
}