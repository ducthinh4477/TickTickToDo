package hcmute.edu.vn.tickticktodo;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.res.ColorStateList;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import hcmute.edu.vn.tickticktodo.BaseActivity;
import hcmute.edu.vn.tickticktodo.adapter.HeaderAdapter;
import hcmute.edu.vn.tickticktodo.adapter.ListPanelAdapter;
import hcmute.edu.vn.tickticktodo.adapter.TaskAdapter;
import hcmute.edu.vn.tickticktodo.helper.SwipeToDeleteCallback;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.ui.AddListDialog;
import hcmute.edu.vn.tickticktodo.ui.AddTaskBottomSheet;
import hcmute.edu.vn.tickticktodo.ui.CalendarActivity;
import hcmute.edu.vn.tickticktodo.ui.CountdownActivity;
import hcmute.edu.vn.tickticktodo.ui.LanguageSelectionDialog;
import hcmute.edu.vn.tickticktodo.ui.StatisticsActivity;
import hcmute.edu.vn.tickticktodo.ui.ThemeSelectionDialog;
import hcmute.edu.vn.tickticktodo.ui.TaskDetailBottomSheet;
import hcmute.edu.vn.tickticktodo.ui.ViewOptionsBottomSheet;
import hcmute.edu.vn.tickticktodo.ui.SchoolLoginActivity;
import hcmute.edu.vn.tickticktodo.viewmodel.TaskViewModel;

public class MainActivity extends BaseActivity {

    private TaskViewModel taskViewModel;

    // UI — Menu overlay
    private FrameLayout menuOverlay;
    private View menuBackdrop;
    private LinearLayout menuBoxContainer;
    private RecyclerView rvListsPanel;
    private ListPanelAdapter listPanelAdapter;
    private ImageButton btnAddList;

    // UI — Header
    private TextView tvHeaderTitle;
    private ImageView navAvatar;
    private ImageButton btnHamburger;
    private TextView tvHeaderDate;
    private ImageButton btnSort;
    private ImageButton btnMore;

    // UI — Content
    private RecyclerView rvTasks;
    private LinearLayout layoutEmpty;
    private EditText etQuickAdd;
    private ImageButton btnSendTask;
    private FloatingActionButton fabAddTask;

    // UI — Nav Rail items
    private LinearLayout navItemHome, navItemCalendar, navItemFocus, navItemSchool, navItemSettings;
    private ImageView navIconHome, navIconCalendar, navIconFocus, navIconSchool, navIconSettings;
    private TextView navLabelHome, navLabelCalendar, navLabelFocus, navLabelSchool, navLabelSettings;

    // Adapters
    private TaskAdapter overdueAdapter;
    private HeaderAdapter overdueHeaderAdapter;
    private HeaderAdapter todayHeaderAdapter;
    private TaskAdapter incompleteAdapter;
    private HeaderAdapter completedHeaderAdapter;
    private TaskAdapter completedAdapter;
    private ConcatAdapter concatAdapter;
    
    // View Options State
    private boolean isHideCompleted = false;
    private boolean isShowDetails = true;
    private int currentMode = 0; // 0 = Today, 1 = 7 Days

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Bật edge-to-edge để layout nhận đúng window insets (tai thỏ / status bar)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        initViews();
        setupNavRailInsets();   // ← xử lý paddingTop theo status bar
        setupHeader();
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        setupMenuPanel();
        setupNavRail();
        initAdapters();
        setupRecyclerView();
        setupViewModel();
        setupQuickAdd();
        setupFab();
    }

    // ─── View binding ───────────────────────────────────────────────────────

    private void initViews() {
        menuOverlay      = findViewById(R.id.menu_overlay);
        menuBackdrop     = findViewById(R.id.menu_backdrop);
        menuBoxContainer = findViewById(R.id.menuBoxContainer);
        rvListsPanel     = findViewById(R.id.rv_lists_panel);
        btnAddList       = findViewById(R.id.btn_add_list);

        navAvatar        = findViewById(R.id.nav_avatar);
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

        // Nav Rail items
        navItemHome     = findViewById(R.id.nav_item_home);
        navItemCalendar = findViewById(R.id.nav_item_calendar);
        navItemFocus    = findViewById(R.id.nav_item_focus);
        navItemSchool   = findViewById(R.id.nav_item_school);
        navItemSettings  = findViewById(R.id.nav_item_settings);
        navIconHome     = findViewById(R.id.nav_icon_home);
        navIconCalendar = findViewById(R.id.nav_icon_calendar);
        navIconFocus    = findViewById(R.id.nav_icon_focus);
        navIconSchool   = findViewById(R.id.nav_icon_school);
        navIconSettings  = findViewById(R.id.nav_icon_settings);
        navLabelHome    = findViewById(R.id.nav_label_home);
        navLabelCalendar= findViewById(R.id.nav_label_calendar);
        navLabelFocus   = findViewById(R.id.nav_label_focus);
        navLabelSchool  = findViewById(R.id.nav_label_school);
        navLabelSettings = findViewById(R.id.nav_label_settings);

        // Đặt chiều rộng drawer = 2/3 content_frame sau khi layout được đo xong
        FrameLayout contentFrame = findViewById(R.id.content_frame);
        contentFrame.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        contentFrame.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int drawerWidth = contentFrame.getWidth() * 2 / 3;
                        ViewGroup.LayoutParams lp = menuBoxContainer.getLayoutParams();
                        lp.width = drawerWidth;
                        menuBoxContainer.setLayoutParams(lp);
                    }
                });
    }

    // ─── Header ─────────────────────────────────────────────────────────────

    private void setupHeader() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        tvHeaderDate.setText(dateFormat.format(new Date()));

        btnHamburger.setOnClickListener(v -> toggleMenu());

        btnSort.setOnClickListener(v -> showSortPopupMenu(v));

        btnMore.setOnClickListener(v -> {
            ViewOptionsBottomSheet bottomSheet = ViewOptionsBottomSheet.newInstance();
            bottomSheet.setOnOptionSelectedListener(new ViewOptionsBottomSheet.OnOptionSelectedListener() {
                @Override
                public void onViewModeSelected(int viewMode) { }

                @Override
                public void onHideCompletedToggled() {
                    isHideCompleted = !isHideCompleted;
                    if (isHideCompleted) {
                        concatAdapter.removeAdapter(completedHeaderAdapter);
                        concatAdapter.removeAdapter(completedAdapter);
                    } else {
                        concatAdapter.addAdapter(completedHeaderAdapter);
                        concatAdapter.addAdapter(completedAdapter);
                    }
                    updateEmptyStateCheck();
                }

                @Override
                public void onShowDetailsToggled() {
                    isShowDetails = !isShowDetails;
                    overdueAdapter.setShowDetails(isShowDetails);
                    incompleteAdapter.setShowDetails(isShowDetails);
                    completedAdapter.setShowDetails(isShowDetails);
                }

                @Override
                public void onViewOptionsClicked() { }

                @Override
                public void onAddSectionClicked() { }

                @Override
                public void onListActivitiesClicked() {
                    startActivity(new android.content.Intent(MainActivity.this, hcmute.edu.vn.tickticktodo.ui.ActivityLogActivity.class));
                }
            });
            bottomSheet.show(getSupportFragmentManager(), "ViewOptions");
        });
    }

    // ─── Slide-in menu ──────────────────────────────────────────────────────

    private void toggleMenu() {
        if (menuOverlay.getVisibility() == View.VISIBLE) {
            closeMenu();
        } else {
            openMenu();
        }
    }

    private void openMenu() {
        menuOverlay.setVisibility(View.VISIBLE);
        float drawerWidth = menuBoxContainer.getLayoutParams().width;
        menuBoxContainer.setTranslationX(-drawerWidth);
        menuBoxContainer.animate().translationX(0).setDuration(250).start();
        menuBackdrop.setAlpha(0f);
        menuBackdrop.animate().alpha(1f).setDuration(250).start();
    }

    private void closeMenu() {
        float drawerWidth = menuBoxContainer.getLayoutParams().width;
        menuBoxContainer.animate().translationX(-drawerWidth).setDuration(200)
                .withEndAction(() -> menuOverlay.setVisibility(View.GONE))
                .start();
        menuBackdrop.animate().alpha(0f).setDuration(200).start();
    }

    private void setupMenuPanel() {
        listPanelAdapter = new ListPanelAdapter(todoList -> {
            closeMenu();
            Toast.makeText(this, "List: " + todoList.getName(), Toast.LENGTH_SHORT).show();
        });
        rvListsPanel.setLayoutManager(new LinearLayoutManager(this));
        rvListsPanel.setAdapter(listPanelAdapter);

        navAvatar.setOnClickListener(v -> showLoginDialog());
        btnAddList.setOnClickListener(v -> AddListDialog.show(this, taskViewModel));

        // Tapping backdrop closes menu
        menuBackdrop.setOnClickListener(v -> closeMenu());

        findViewById(R.id.panel_item_today).setOnClickListener(v -> {
            closeMenu();
            currentMode = 0;
            tvHeaderTitle.setText(R.string.header_today);
            updateIncompleteList(taskViewModel.getTodayIncompleteTasks().getValue());
        });
        findViewById(R.id.panel_item_next7days).setOnClickListener(v -> {
            closeMenu();
            currentMode = 1;
            tvHeaderTitle.setText("7 Ngày tới");
            updateIncompleteList(taskViewModel.getNext7DaysTasks().getValue());
        });
        findViewById(R.id.panel_item_inbox).setOnClickListener(v -> onPanelItemSelected(getString(R.string.panel_inbox)));
        findViewById(R.id.panel_item_completed).setOnClickListener(v -> onPanelItemSelected(getString(R.string.menu_completed)));
        findViewById(R.id.panel_item_trash).setOnClickListener(v -> onPanelItemSelected(getString(R.string.menu_trash)));
        findViewById(R.id.panel_item_notifications).setOnClickListener(v -> {
            closeMenu();
            Toast.makeText(this, R.string.toast_notifications_wip, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.panel_item_help).setOnClickListener(v -> {
            closeMenu();
            Toast.makeText(this, R.string.toast_help_wip, Toast.LENGTH_SHORT).show();
        });
    }

    private void onPanelItemSelected(String label) {
        closeMenu();
        tvHeaderTitle.setText(label);
    }

    private void showLoginDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_login);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        android.widget.Button btnGoogle = dialog.findViewById(R.id.btnLoginGoogle);
        android.widget.Button btnFacebook = dialog.findViewById(R.id.btnLoginFacebook);

        btnGoogle.setOnClickListener(v -> {
            // TODO: Tích hợp Google Sign-In hoặc Firebase Auth tại đây
            Toast.makeText(this, "Google Login Clicked", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnFacebook.setOnClickListener(v -> {
            // TODO: Tích hợp Facebook Login hoặc Firebase Auth tại đây
            Toast.makeText(this, "Facebook Login Clicked", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        // Điều chỉnh width nếu cần
        dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dialog.show();
    }

    /**
     * Hiển thị menu Sort: Date / Priority / Title / Custom.
     * Khi chọn, gọi taskViewModel.setSortMode() → LiveData tự cập nhật UI.
     */
    private void showSortPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.popup_sort_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.sort_date_asc) {
                taskViewModel.setSortMode(TaskViewModel.SORT_BY_DATE_ASC);
            } else if (id == R.id.sort_priority) {
                taskViewModel.setSortMode(TaskViewModel.SORT_BY_PRIORITY);
            }
            return true;
        });
        popup.show();
    }

    // ─── Nav Rail ───────────────────────────────────────────────────────────

    /**
     * Áp dụng paddingTop cho nav_rail = status bar height + 16dp khoảng an toàn,
     * đảm bảo btn_hamburger không bị che bởi tai thỏ / camera / đồng hồ.
     */
    private void setupNavRailInsets() {
        LinearLayout navRail = findViewById(R.id.nav_rail);
        ViewCompat.setOnApplyWindowInsetsListener(navRail, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int extraDp = (int) (16 * view.getResources().getDisplayMetrics().density);
            view.setPadding(
                    view.getPaddingLeft(),
                    insets.top + extraDp,   // status bar + 16dp khoảng an toàn
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupNavRail() {
        navItemHome.setOnClickListener(v -> selectNavItem(R.id.nav_item_home));

        navItemCalendar.setOnClickListener(v -> {
            selectNavItem(R.id.nav_item_calendar);
            startActivity(CalendarActivity.newIntent(this));
        });

        navItemFocus.setOnClickListener(v -> {
            selectNavItem(R.id.nav_item_focus);
            startActivity(CountdownActivity.newIntent(this));
        });

        navItemSchool.setOnClickListener(v -> {
            selectNavItem(R.id.nav_item_school);
            Intent intent = new Intent(this, hcmute.edu.vn.tickticktodo.ui.MoodleActivity.class);
            startActivity(intent);
        });

        navItemSettings.setOnClickListener(v -> {
            selectNavItem(R.id.nav_item_settings);
            showSettingsDialog();
        });

        // Highlight Home by default
        selectNavItem(R.id.nav_item_home);
    }

    private void showSettingsDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_settings);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            
            // Cập nhật kích thước Dialog chiếm 75% màn hình
            android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int width = (int) (displayMetrics.widthPixels * 0.75);
            int height = (int) (displayMetrics.heightPixels * 0.75);
            
            window.setLayout(width, height);
        }

        // Ánh xạ các nút góc
        dialog.findViewById(R.id.btn_close_settings).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btn_save_settings).setOnClickListener(v -> {
            // TODO: Xử lý lưu các thay đổi cấu hình nếu cần thiết
            dialog.dismiss();
        });

        // Ánh xạ các mục trong Body của Settings
        dialog.findViewById(R.id.layout_settings_language).setOnClickListener(v -> {
            dialog.dismiss();
            LanguageSelectionDialog.show(this);
        });

        dialog.findViewById(R.id.layout_settings_theme).setOnClickListener(v -> {
            dialog.dismiss();
            ThemeSelectionDialog.show(this);
        });

        dialog.findViewById(R.id.layout_settings_statistics).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(StatisticsActivity.newIntent(this));
        });

        dialog.findViewById(R.id.layout_settings_sign_out).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, R.string.toast_signout_wip, Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void selectNavItem(int selectedId) {
        int[] ids     = {R.id.nav_item_home, R.id.nav_item_calendar, R.id.nav_item_focus, R.id.nav_item_school, R.id.nav_item_settings};
        ImageView[] icons  = {navIconHome, navIconCalendar, navIconFocus, navIconSchool, navIconSettings};
        TextView[]  labels = {navLabelHome, navLabelCalendar, navLabelFocus, navLabelSchool, navLabelSettings};

        int accent    = getResources().getColor(R.color.accent_primary, getTheme());
        int secondary = getResources().getColor(R.color.text_secondary, getTheme());

        for (int i = 0; i < ids.length; i++) {
            boolean selected = ids[i] == selectedId;
            int color = selected ? accent : secondary;
            icons[i].setImageTintList(ColorStateList.valueOf(color));
            labels[i].setTextColor(color);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        selectNavItem(R.id.nav_item_home);
    }

    // ─── Adapters ───────────────────────────────────────────────────────────

    private void initAdapters() {
        // Overdue
        overdueAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> TaskDetailBottomSheet.newInstance(task.getId()).show(getSupportFragmentManager(), "TaskDetail")
        );
        overdueHeaderAdapter = new HeaderAdapter();

        // Incomplete
        todayHeaderAdapter = new HeaderAdapter();
        incompleteAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> TaskDetailBottomSheet.newInstance(task.getId()).show(getSupportFragmentManager(), "TaskDetail")
        );

        // Completed
        completedHeaderAdapter = new HeaderAdapter();
        completedAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> TaskDetailBottomSheet.newInstance(task.getId()).show(getSupportFragmentManager(), "TaskDetail")
        );

        concatAdapter = new ConcatAdapter(
                overdueHeaderAdapter, overdueAdapter,
                todayHeaderAdapter, incompleteAdapter,
                completedHeaderAdapter, completedAdapter
        );
    }

    private void setupRecyclerView() {
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(concatAdapter);

        SwipeToDeleteCallback swipeCallback = new SwipeToDeleteCallback(this, this::handleSwipeDelete);
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvTasks);
    }

    private void handleSwipeDelete(int position) {
        int overdueHead = overdueHeaderAdapter.getItemCount();
        int overdue = overdueAdapter.getItemCount();
        int todayHead = todayHeaderAdapter.getItemCount();
        int incomplete = incompleteAdapter.getItemCount();
        int compHead = isHideCompleted ? 0 : completedHeaderAdapter.getItemCount();
        int comp = isHideCompleted ? 0 : completedAdapter.getItemCount();

        Task deletedTask = null;
        if (position < overdueHead) {
            return;
        } else if (position < overdueHead + overdue) {
            deletedTask = overdueAdapter.getCurrentList().get(position - overdueHead);
        } else if (position < overdueHead + overdue + todayHead) {
            return;
        } else if (position < overdueHead + overdue + todayHead + incomplete) {
            deletedTask = incompleteAdapter.getCurrentList().get(position - overdueHead - overdue - todayHead);
        } else if (position < overdueHead + overdue + todayHead + incomplete + compHead) {
            return;
        } else if (position < overdueHead + overdue + todayHead + incomplete + compHead + comp) {
            int localPos = position - overdueHead - overdue - todayHead - incomplete - compHead;
            deletedTask = completedAdapter.getCurrentList().get(localPos);
        }

        if (deletedTask == null) return;

        final Task finalDeleted = deletedTask;
        taskViewModel.delete(finalDeleted);

        Snackbar.make(rvTasks, getString(R.string.snackbar_deleted, finalDeleted.getTitle()), Snackbar.LENGTH_LONG)
                .setAction(R.string.snackbar_undo, v -> taskViewModel.insert(new Task(
                        finalDeleted.getTitle(),
                        finalDeleted.getDescription(),
                        finalDeleted.getDueDate(),
                        finalDeleted.isCompleted(),
                        finalDeleted.getPriority()
                )))
                .setActionTextColor(getResources().getColor(R.color.accent_primary, getTheme()))
                .show();
    }

    // ─── Quick add ──────────────────────────────────────────────────────────

    private void setupQuickAdd() {
        etQuickAdd.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
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

    // ─── FAB ────────────────────────────────────────────────────────────────

    private void setupFab() {
        fabAddTask.setOnClickListener(v ->
                AddTaskBottomSheet.newInstance().show(getSupportFragmentManager(), "AddTask"));
    }

    // ─── ViewModel / LiveData ───────────────────────────────────────────────

    private void setupViewModel() {
        taskViewModel.getSortModeLiveData().observe(this, mode -> {
            if (currentMode == 0) updateIncompleteList(taskViewModel.getTodayIncompleteTasks().getValue());
            else if (currentMode == 1) updateIncompleteList(taskViewModel.getNext7DaysTasks().getValue());
        });

        taskViewModel.getOverdueTasks().observe(this, overdueTasks -> {
            List<Task> list = overdueTasks != null ? overdueTasks : new ArrayList<>();
            overdueAdapter.submitList(new ArrayList<>(list));
            overdueHeaderAdapter.setHeader("Đã quá hạn", list.size());
            updateEmptyStateCheck();
        });

        taskViewModel.getTodayIncompleteTasks().observe(this, incompleteTasks -> {
            if (currentMode == 0) updateIncompleteList(incompleteTasks);
        });

        taskViewModel.getNext7DaysTasks().observe(this, nextTasks -> {
            if (currentMode == 1) updateIncompleteList(nextTasks);
        });

        taskViewModel.getTodayCompletedTasks().observe(this, completedTasks -> {
            List<Task> list = completedTasks != null ? completedTasks : new ArrayList<>();
            completedAdapter.submitList(new ArrayList<>(list));
            completedHeaderAdapter.setHeader("Hoàn thành", list.size());
            updateEmptyStateCheck();
        });

        taskViewModel.getAllLists().observe(this, lists -> {
            if (lists != null) listPanelAdapter.submitList(new ArrayList<>(lists));
        });
    }

    private void updateIncompleteList(List<Task> tasks) {
        List<Task> list = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
        
        int sortMode = taskViewModel.getCurrentSortMode();
        if (sortMode == TaskViewModel.SORT_BY_PRIORITY) {
            java.util.Collections.sort(list, (t1, t2) -> Integer.compare(t2.getPriority(), t1.getPriority()));
        } else if (sortMode == TaskViewModel.SORT_BY_DATE_DESC) {
            java.util.Collections.sort(list, (t1, t2) -> {
                Long d1 = t1.getDueDate();
                Long d2 = t2.getDueDate();
                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1;
                if (d2 == null) return -1;
                return d2.compareTo(d1);
            });
        }

        incompleteAdapter.submitList(list);
        todayHeaderAdapter.setHeader(currentMode == 0 ? "Hôm nay" : "7 ngày tới", list.size());
        updateEmptyStateCheck();
    }

    private void updateEmptyStateCheck() {
        int total = incompleteAdapter.getItemCount() + overdueAdapter.getItemCount() + 
                    (isHideCompleted ? 0 : completedAdapter.getItemCount());
        boolean isEmpty = total == 0;
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvTasks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // ─── Utils ──────────────────────────────────────────────────────────────

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (imm != null && focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (menuOverlay.getVisibility() == View.VISIBLE) {
            closeMenu();
        } else {
            super.onBackPressed();
        }
    }
}