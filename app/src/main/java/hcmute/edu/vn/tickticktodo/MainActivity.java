package hcmute.edu.vn.tickticktodo;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
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

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
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
import hcmute.edu.vn.tickticktodo.model.TodoList;
import hcmute.edu.vn.tickticktodo.ui.AddTaskBottomSheet;
import hcmute.edu.vn.tickticktodo.ui.TaskDetailActivity;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

public class MainActivity extends AppCompatActivity {

    private TaskViewModel taskViewModel;

    // UI components — Drawer
    private DrawerLayout drawerLayout;
    private LinearLayout listsPanel;
    private RecyclerView rvListsPanel;
    private ListPanelAdapter listPanelAdapter;

    // UI components — Nav Rail
    private ImageButton btnHamburger;
    private ImageView navAvatar;
    private ImageButton navTask;
    private ImageButton navCalendar;
    private ImageButton navSearch;

    // UI components — Header
    private TextView tvHeaderDate;
    private ImageButton btnSort;
    private ImageButton btnMore;

    // UI components — Content
    private RecyclerView rvTasks;
    private LinearLayout layoutEmpty;
    private EditText etQuickAdd;
    private ImageButton btnSendTask;
    private FloatingActionButton fabAddTask;

    // Task Adapters (ConcatAdapter)
    private TaskAdapter incompleteAdapter;
    private HeaderAdapter headerAdapter;
    private TaskAdapter completedAdapter;
    private ConcatAdapter concatAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupHeader();
        setupNavRail();
        setupListsPanel();
        setupRecyclerView();
        setupQuickAdd();
        setupFab();
        setupViewModel();
    }

    // ─── Khởi tạo view references ────────────────────────────────────────────────

    private void initViews() {
        // Drawer + Lists Panel
        drawerLayout = findViewById(R.id.drawer_layout);
        listsPanel = findViewById(R.id.lists_panel);
        rvListsPanel = findViewById(R.id.rv_lists_panel);

        // Nav Rail
        btnHamburger = findViewById(R.id.btn_hamburger);
        navAvatar = findViewById(R.id.nav_avatar);
        navTask = findViewById(R.id.nav_task);
        navCalendar = findViewById(R.id.nav_calendar);
        navSearch = findViewById(R.id.nav_search);

        // Header
        tvHeaderDate = findViewById(R.id.tv_header_date);
        btnSort = findViewById(R.id.btn_sort);
        btnMore = findViewById(R.id.btn_more);

        // Content
        rvTasks = findViewById(R.id.rv_tasks);
        layoutEmpty = findViewById(R.id.layout_empty);
        etQuickAdd = findViewById(R.id.et_quick_add);
        btnSendTask = findViewById(R.id.btn_send_task);
        fabAddTask = findViewById(R.id.fab_add_task);
    }

    // ─── Header: ngày hiện tại + Sort + More ─────────────────────────────────────

    private void setupHeader() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        tvHeaderDate.setText(dateFormat.format(new Date()));

        // Sort button
        btnSort.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Sắp xếp đang phát triển",
                        Toast.LENGTH_SHORT).show());

        // More button
        btnMore.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Thêm tùy chọn đang phát triển",
                        Toast.LENGTH_SHORT).show());
    }

    // ─── Navigation Rail setup ───────────────────────────────────────────────────

    private void setupNavRail() {
        // Hamburger → mở/đóng Lists Panel (Drawer)
        btnHamburger.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // Avatar → PopupMenu (Settings, Statistics, Sign Out)
        navAvatar.setOnClickListener(this::showUserPopupMenu);

        // Task (Today) — đã ở màn hình này
        navTask.setOnClickListener(v ->
                Toast.makeText(this, "Đang ở trang Today", Toast.LENGTH_SHORT).show());

        // Calendar
        navCalendar.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Lịch đang phát triển",
                        Toast.LENGTH_SHORT).show());

        // Search
        navSearch.setOnClickListener(v ->
                Toast.makeText(this, "Tính năng Tìm kiếm đang phát triển",
                        Toast.LENGTH_SHORT).show());

        // Back press: đóng drawer nếu đang mở
        OnBackPressedCallback drawerBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, drawerBackCallback);

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                drawerBackCallback.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                drawerBackCallback.setEnabled(false);
            }
        });
    }

    // ─── User PopupMenu (Avatar) ─────────────────────────────────────────────────

    private void showUserPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_user_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_settings) {
                Toast.makeText(this, "Tính năng Cài đặt đang phát triển",
                        Toast.LENGTH_SHORT).show();
            } else if (id == R.id.menu_statistics) {
                Toast.makeText(this, "Tính năng Thống kê đang phát triển",
                        Toast.LENGTH_SHORT).show();
            } else if (id == R.id.menu_sign_out) {
                Toast.makeText(this, "Tính năng Đăng xuất đang phát triển",
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        popup.show();
    }

    // ─── Lists Panel (Drawer) setup ──────────────────────────────────────────────

    private void setupListsPanel() {
        // Adapter cho dynamic TodoList items
        listPanelAdapter = new ListPanelAdapter(todoList -> {
            Toast.makeText(this, "List: " + todoList.getName(),
                    Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(GravityCompat.START);
            // TODO: filter tasks by todoList.getId()
        });
        rvListsPanel.setLayoutManager(new LinearLayoutManager(this));
        rvListsPanel.setAdapter(listPanelAdapter);

        // Built-in panel items
        LinearLayout panelInbox = listsPanel.findViewById(R.id.panel_item_inbox);
        LinearLayout panelToday = listsPanel.findViewById(R.id.panel_item_today);
        LinearLayout panelCalendar = listsPanel.findViewById(R.id.panel_item_calendar);
        LinearLayout panelNotifications = listsPanel.findViewById(R.id.panel_item_notifications);
        LinearLayout panelHelp = listsPanel.findViewById(R.id.panel_item_help);

        panelInbox.setOnClickListener(v -> {
            Toast.makeText(this, "Inbox", Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        panelToday.setOnClickListener(v -> {
            Toast.makeText(this, "Đang ở trang Today", Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        panelCalendar.setOnClickListener(v -> {
            Toast.makeText(this, "Tính năng Lịch đang phát triển",
                    Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        panelNotifications.setOnClickListener(v -> {
            Toast.makeText(this, "Tính năng Thông báo đang phát triển",
                    Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        panelHelp.setOnClickListener(v -> {
            Toast.makeText(this, "Tính năng Trợ giúp đang phát triển",
                    Toast.LENGTH_SHORT).show();
            drawerLayout.closeDrawer(GravityCompat.START);
        });
    }

    // ─── RecyclerView setup ──────────────────────────────────────────────────────

    private void setupRecyclerView() {
        incompleteAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> openTaskDetail(task.getId())
        );

        headerAdapter = new HeaderAdapter();

        completedAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> openTaskDetail(task.getId())
        );

        concatAdapter = new ConcatAdapter(incompleteAdapter, headerAdapter, completedAdapter);

        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(concatAdapter);

        SwipeToDeleteCallback swipeCallback = new SwipeToDeleteCallback(this, this::handleSwipeDelete);
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvTasks);
    }

    // ─── Swipe to delete + Undo ───────────────────────────────────────────────

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
            deletedTask = completedAdapter.getCurrentList().get(localPos);
        }

        taskViewModel.delete(deletedTask);

        Snackbar.make(rvTasks, "Đã xóa: " + deletedTask.getTitle(), Snackbar.LENGTH_LONG)
                .setAction("Undo", v -> {
                    Task restoredTask = new Task(
                            deletedTask.getTitle(),
                            deletedTask.getDescription(),
                            deletedTask.getDueDate(),
                            deletedTask.isCompleted(),
                            deletedTask.getPriority()
                    );
                    taskViewModel.insert(restoredTask);
                })
                .setActionTextColor(getResources().getColor(R.color.priority_low, getTheme()))
                .show();
    }

    // ─── Quick Add bar ───────────────────────────────────────────────────────────

    private void setupQuickAdd() {
        etQuickAdd.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSendTask.setVisibility(
                        s.toString().trim().isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) { }
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

        Task newTask = new Task(title, "", cal.getTimeInMillis(), false, 0);
        taskViewModel.insert(newTask);

        etQuickAdd.setText("");
        hideKeyboard();
    }

    // ─── Mở TaskDetailActivity ────────────────────────────────────────────────────

    private void openTaskDetail(long taskId) {
        startActivity(TaskDetailActivity.newIntent(this, taskId));
    }

    // ─── FAB → mở BottomSheet ────────────────────────────────────────────────────

    private void setupFab() {
        fabAddTask.setOnClickListener(v -> openAddTaskBottomSheet(null));
    }

    private void openAddTaskBottomSheet(String initialTitle) {
        AddTaskBottomSheet bottomSheet;
        if (initialTitle != null && !initialTitle.isEmpty()) {
            bottomSheet = AddTaskBottomSheet.newInstance(initialTitle);
        } else {
            bottomSheet = AddTaskBottomSheet.newInstance();
        }
        bottomSheet.show(getSupportFragmentManager(), "AddTaskBottomSheet");
    }

    // ─── ViewModel + LiveData observe ────────────────────────────────────────────

    private void setupViewModel() {
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        taskViewModel.getTodayIncompleteTasks().observe(this, incompleteTasks -> {
            if (incompleteTasks == null) incompleteTasks = new ArrayList<>();
            incompleteAdapter.submitList(new ArrayList<>(incompleteTasks));

            List<Task> completedTasks = taskViewModel.getTodayCompletedTasks().getValue();
            updateEmptyState(incompleteTasks, completedTasks);
        });

        taskViewModel.getTodayCompletedTasks().observe(this, completedTasks -> {
            if (completedTasks == null) completedTasks = new ArrayList<>();
            completedAdapter.submitList(new ArrayList<>(completedTasks));
            headerAdapter.setCompletedCount(completedTasks.size());

            List<Task> incompleteTasks = taskViewModel.getTodayIncompleteTasks().getValue();
            updateEmptyState(incompleteTasks, completedTasks);
        });

        // Observe TodoLists → cập nhật Lists Panel
        taskViewModel.getAllLists().observe(this, lists -> {
            if (lists != null) {
                listPanelAdapter.submitList(new ArrayList<>(lists));
            }
        });
    }

    private void updateEmptyState(List<Task> incomplete, List<Task> completed) {
        int total = (incomplete != null ? incomplete.size() : 0)
                  + (completed != null ? completed.size() : 0);
        boolean isEmpty = total == 0;
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // ─── Utilities ───────────────────────────────────────────────────────────────

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View currentFocus = getCurrentFocus();
        if (imm != null && currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }
}