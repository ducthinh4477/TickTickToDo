package hcmute.edu.vn.tickticktodo;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.adapter.HeaderAdapter;
import hcmute.edu.vn.tickticktodo.adapter.ListPanelAdapter;
import hcmute.edu.vn.tickticktodo.adapter.TaskAdapter;
import hcmute.edu.vn.tickticktodo.helper.SwipeToDeleteCallback;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.ui.AddTaskBottomSheet;
import hcmute.edu.vn.tickticktodo.ui.TaskDetailActivity;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

public class MainActivity extends AppCompatActivity {

    private TaskViewModel taskViewModel;

    // UI — Menu Box
    private LinearLayout menuBoxContainer;
    private RecyclerView rvListsPanel;
    private ListPanelAdapter listPanelAdapter;

    // UI — Header
    private TextView tvHeaderTitle;
    private ImageView navAvatar;
    private ImageButton btnHamburger;
    private ImageButton navTask;
    private ImageButton navCalendar;
    private ImageButton navSearch;
    private TextView tvHeaderDate;
    private ImageButton btnSort;
    private ImageButton btnMore;

    // UI — Content
    private RecyclerView rvTasks;
    private LinearLayout layoutEmpty;
    private EditText etQuickAdd;
    private ImageButton btnSendTask;
    private FloatingActionButton fabAddTask;

    // Adapters
    private TaskAdapter incompleteAdapter;
    private HeaderAdapter headerAdapter;
    private TaskAdapter completedAdapter;
    private ConcatAdapter concatAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupHeader();
        setupNavRail();
        setupListsPanel();
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        initAdapters();
        setupRecyclerView();
        setupViewModel();
        setupQuickAdd();
        setupFab();
    }

    private void initViews() {
        menuBoxContainer = findViewById(R.id.menuBoxContainer);
        rvListsPanel     = findViewById(R.id.rv_lists_panel);

        navAvatar        = findViewById(R.id.nav_avatar);
        navTask          = findViewById(R.id.nav_task);
        navCalendar      = findViewById(R.id.nav_calendar);
        navSearch        = findViewById(R.id.nav_search);

        btnHamburger     = findViewById(R.id.btn_hamburger);
        tvHeaderTitle    = findViewById(R.id.tv_header_today);
        tvHeaderDate     = findViewById(R.id.tv_header_date);
        btnSort          = findViewById(R.id.btn_sort);
        btnMore          = findViewById(R.id.btn_more);

        rvTasks          = findViewById(R.id.rv_tasks);
        layoutEmpty      = findViewById(R.id.layout_empty);
        etQuickAdd       = findViewById(R.id.et_quick_add);
        btnSendTask      = findViewById(R.id.btn_send_task);
        fabAddTask       = findViewById(R.id.fab_add_task);
    }

    private void setupHeader() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        tvHeaderDate.setText(dateFormat.format(new Date()));

        btnHamburger.setOnClickListener(v -> {
            if (menuBoxContainer.getVisibility() == View.VISIBLE) {
                menuBoxContainer.setVisibility(View.GONE);
            } else {
                menuBoxContainer.setVisibility(View.VISIBLE);
            }
        });

        btnSort.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Sắp xếp đang phát triển", Toast.LENGTH_SHORT).show());

        btnMore.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Thêm tùy chọn đang phát triển", Toast.LENGTH_SHORT).show());
    }

    private void setupNavRail() {
        navAvatar.setOnClickListener(this::showUserPopupMenu);

        navTask.setOnClickListener(v ->
                Toast.makeText(this, "Đang ở trang Today", Toast.LENGTH_SHORT).show());

        navCalendar.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Lịch đang phát triển", Toast.LENGTH_SHORT).show());

        navSearch.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Tìm kiếm đang phát triển", Toast.LENGTH_SHORT).show());
    }

    private void showUserPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_user_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_settings) {
                Toast.makeText(this, "Tính năng Cài đặt đang phát triển", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.menu_statistics) {
                Toast.makeText(this, "Tính năng Thống kê đang phát triển", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.menu_sign_out) {
                Toast.makeText(this, "Tính năng Đăng xuất đang phát triển", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }

    private void setupListsPanel() {
        listPanelAdapter = new ListPanelAdapter(todoList ->
                Toast.makeText(this, "List: " + todoList.getName(), Toast.LENGTH_SHORT).show());
        rvListsPanel.setLayoutManager(new LinearLayoutManager(this));
        rvListsPanel.setAdapter(listPanelAdapter);

        findViewById(R.id.panel_item_today).setOnClickListener(v -> onPanelItemSelected("Today"));
        findViewById(R.id.panel_item_next7days).setOnClickListener(v -> onPanelItemSelected("Next 7 Days"));
        findViewById(R.id.panel_item_inbox).setOnClickListener(v -> onPanelItemSelected("Inbox"));
        findViewById(R.id.panel_item_completed).setOnClickListener(v -> onPanelItemSelected("Completed"));
        findViewById(R.id.panel_item_trash).setOnClickListener(v -> onPanelItemSelected("Trash"));
        findViewById(R.id.panel_item_notifications).setOnClickListener(v -> {
            menuBoxContainer.setVisibility(View.GONE);
            Toast.makeText(this, "Tính năng Thông báo đang phát triển", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.panel_item_help).setOnClickListener(v -> {
            menuBoxContainer.setVisibility(View.GONE);
            Toast.makeText(this, "Tính năng Trợ giúp đang phát triển", Toast.LENGTH_SHORT).show();
        });
    }

    private void onPanelItemSelected(String label) {
        menuBoxContainer.setVisibility(View.GONE);
        tvHeaderTitle.setText(label);
        Toast.makeText(this, "Đang tải: " + label, Toast.LENGTH_SHORT).show();
    }

    private void initAdapters() {
        incompleteAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> startActivity(TaskDetailActivity.newIntent(this, task.getId()))
        );

        headerAdapter = new HeaderAdapter();

        completedAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> startActivity(TaskDetailActivity.newIntent(this, task.getId()))
        );

        concatAdapter = new ConcatAdapter(incompleteAdapter, headerAdapter, completedAdapter);
    }

    private void setupRecyclerView() {
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(concatAdapter);

        SwipeToDeleteCallback swipeCallback = new SwipeToDeleteCallback(this, this::handleSwipeDelete);
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvTasks);
    }

    private void handleSwipeDelete(int position) {
        int incompleteCount = incompleteAdapter.getItemCount();
        int headerCount = headerAdapter.getItemCount();

        Task deletedTask;
        if (position < incompleteCount) {
            deletedTask = incompleteAdapter.getCurrentList().get(position);
        } else if (position < incompleteCount + headerCount) {
            return;
        } else {
            int localPos = position - incompleteCount - headerCount;
            List<Task> completedList = completedAdapter.getCurrentList();
            if (localPos < 0 || localPos >= completedList.size()) return;
            deletedTask = completedList.get(localPos);
        }

        final Task finalDeleted = deletedTask;
        taskViewModel.delete(finalDeleted);

        Snackbar.make(rvTasks, "Đã xóa: " + finalDeleted.getTitle(), Snackbar.LENGTH_LONG)
                .setAction("Undo", v -> taskViewModel.insert(new Task(
                        finalDeleted.getTitle(),
                        finalDeleted.getDescription(),
                        finalDeleted.getDueDate(),
                        finalDeleted.isCompleted(),
                        finalDeleted.getPriority()
                )))
                .show();
    }

    private void setupQuickAdd() {
        etQuickAdd.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void afterTextChanged(Editable s) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSendTask.setVisibility(s.toString().trim().isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

        btnSendTask.setOnClickListener(v -> submitQuickAdd());

        etQuickAdd.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitQuickAdd();
                return true;
            }
            return false;
        });
    }

    private void submitQuickAdd() {
        String title = etQuickAdd.getText().toString().trim();
        if (title.isEmpty()) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        taskViewModel.insert(new Task(title, "", cal.getTimeInMillis(), false, 0));
        etQuickAdd.setText("");
        hideKeyboard();
    }

    private void setupFab() {
        fabAddTask.setOnClickListener(v ->
                AddTaskBottomSheet.newInstance().show(getSupportFragmentManager(), "AddTask"));
    }

    private void setupViewModel() {
        taskViewModel.getTodayIncompleteTasks().observe(this, incompleteTasks -> {
            List<Task> list = incompleteTasks != null ? incompleteTasks : new ArrayList<>();
            incompleteAdapter.submitList(new ArrayList<>(list));
            updateEmptyState(list, taskViewModel.getTodayCompletedTasks().getValue());
        });

        taskViewModel.getTodayCompletedTasks().observe(this, completedTasks -> {
            List<Task> list = completedTasks != null ? completedTasks : new ArrayList<>();
            completedAdapter.submitList(new ArrayList<>(list));
            headerAdapter.setCompletedCount(list.size());
            updateEmptyState(taskViewModel.getTodayIncompleteTasks().getValue(), list);
        });

        taskViewModel.getAllLists().observe(this, lists -> {
            if (lists != null) listPanelAdapter.submitList(new ArrayList<>(lists));
        });
    }

    private void updateEmptyState(List<Task> incomplete, List<Task> completed) {
        int total = (incomplete != null ? incomplete.size() : 0)
                  + (completed  != null ? completed.size()  : 0);
        boolean isEmpty = total == 0;
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}