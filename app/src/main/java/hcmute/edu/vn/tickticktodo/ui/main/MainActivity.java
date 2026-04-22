package hcmute.edu.vn.tickticktodo.ui.main;

import android.app.TimePickerDialog;
import android.content.Intent;
import hcmute.edu.vn.tickticktodo.ui.task.EisenhowerActivity;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
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
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
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
import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.databinding.ActivityMainBinding;
import hcmute.edu.vn.tickticktodo.ui.list.HeaderAdapter;
import hcmute.edu.vn.tickticktodo.ui.list.ListPanelAdapter;
import hcmute.edu.vn.tickticktodo.ui.task.TaskAdapter;
import hcmute.edu.vn.tickticktodo.helper.SwipeToDeleteCallback;
import hcmute.edu.vn.tickticktodo.model.Task;
import hcmute.edu.vn.tickticktodo.ui.list.AddListDialog;
import hcmute.edu.vn.tickticktodo.ui.task.AddTaskBottomSheet;
import hcmute.edu.vn.tickticktodo.ui.calendar.CalendarActivity;
import hcmute.edu.vn.tickticktodo.ui.countdown.CountdownActivity;
import hcmute.edu.vn.tickticktodo.ui.habit.HabitTrackerActivity;
import hcmute.edu.vn.tickticktodo.ui.LanguageSelectionDialog;
import hcmute.edu.vn.tickticktodo.ui.MoodleActivity;
import hcmute.edu.vn.tickticktodo.ui.StatisticsActivity;
import hcmute.edu.vn.tickticktodo.ui.ThemeSelectionDialog;
import hcmute.edu.vn.tickticktodo.ui.VoicePromptActivity;
import hcmute.edu.vn.tickticktodo.ui.task.TaskDetailBottomSheet;
import hcmute.edu.vn.tickticktodo.ui.task.ViewOptionsBottomSheet;
import hcmute.edu.vn.tickticktodo.ui.SchoolLoginActivity;
import hcmute.edu.vn.tickticktodo.core.background.FloatingAssistantService;
import hcmute.edu.vn.tickticktodo.ui.task.TaskViewModel;

public class MainActivity extends BaseActivity {

    private static final String PREFS_NAME = "TickTickPrefs";
    private static final String KEY_FLOATING_ASSISTANT_ENABLED = "floating_assistant_enabled";
    public static final String EXTRA_OPEN_ADD_TASK_SHEET = "extra_open_add_task_sheet";

    private ActivityMainBinding binding;
    private MainNavigationHelper mainNavigationHelper;
    private MainPermissionHandler mainPermissionHandler;
    private MainViewModel mainStateViewModel;
    private TaskViewModel taskViewModel;

    // UI — Menu overlay (left drawer)
    private FrameLayout menuOverlay;
    private View menuBackdrop;
    private LinearLayout menuBoxContainer;

    // UI — More nav popup
    private FrameLayout moreNavOverlay;
    private View moreNavBackdrop;
    private View moreNavPopup;
    private RecyclerView rvListsPanel;
    private ListPanelAdapter listPanelAdapter;
    private ImageButton btnAddList;
    private TextView tvUserSub;
    private TextView statCompleted;
    private TextView statStreak;

    // UI — Header
    private TextView tvHeaderTitle;
    private TextView tvHeaderGreeting;
    private ImageView navAvatar;
    private ImageButton btnHamburger;
    private TextView tvHeaderDate;
    private TextView tvTaskCountSummary;
    private ImageButton btnSort;
    private ImageButton btnMore;

    // UI — Content
    private RecyclerView rvTasks;
    private LinearLayout layoutEmpty;
    private EditText etQuickAdd;
    private ImageButton btnVoiceAdd;
    private ImageButton btnSendTask;
    private FloatingActionButton fabAddTask;

    // UI — Nav Rail items
    private LinearLayout navItemHome, navItemCalendar, navItemFocus, navItemSchool, navItemHabits, navItemSettings, navItemAiAssistant, navItemMore;
    private ImageView navIconHome, navIconCalendar, navIconFocus, navIconSchool, navIconHabits, navIconSettings, navIconAiAssistant, navIconMore;
    private TextView navLabelHome, navLabelCalendar, navLabelFocus, navLabelSchool, navLabelHabits, navLabelSettings, navLabelAiAssistant, navLabelMore;

    // Adapters
    private TaskAdapter overdueAdapter;
    private HeaderAdapter overdueHeaderAdapter;
    private HeaderAdapter todayHeaderAdapter;
    private TaskAdapter incompleteAdapter;
    private HeaderAdapter completedHeaderAdapter;
    private TaskAdapter completedAdapter;
    private ConcatAdapter concatAdapter;
    
    // Cache list cho Expand/Collapse
    private List<Task> currentOverdueTasks = new ArrayList<>();
    private List<Task> currentIncompleteTasks = new ArrayList<>();
    private List<Task> currentCompletedTasks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // Bật edge-to-edge để layout nhận đúng window insets (tai thỏ / status bar)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mainNavigationHelper = new MainNavigationHelper(this);
        mainPermissionHandler = new MainPermissionHandler(this);
        mainStateViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        initViews();
        setupWindowInsets();    // ← xử lý status bar + navigation bar insets
        setupHeader();
        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        setupMenuPanel();
        setupMoreNavPopup();
        setupNavRail();
        initAdapters();
        setupRecyclerView();
        setupViewModel();
        setupQuickAdd();
        setupFab();
        handleExternalIntent(getIntent());

        Integer selectedNavId = mainStateViewModel.getSelectedNavItem().getValue();
        selectNavItem(selectedNavId == null || selectedNavId == 0 ? R.id.nav_item_home : selectedNavId);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExternalIntent(intent);
    }

    private void handleExternalIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        if (intent.getBooleanExtra(EXTRA_OPEN_ADD_TASK_SHEET, false)) {
            intent.removeExtra(EXTRA_OPEN_ADD_TASK_SHEET);
            openAddTaskSheetFromExternalTrigger();
        }
    }

    private void openAddTaskSheetFromExternalTrigger() {
        if (isFinishing()) {
            return;
        }

        View anchor = rvTasks != null ? rvTasks : findViewById(android.R.id.content);
        if (anchor == null) {
            return;
        }

        anchor.post(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (getSupportFragmentManager().findFragmentByTag("AddTask") == null) {
                AddTaskBottomSheet.newInstance().show(getSupportFragmentManager(), "AddTask");
            }
        });
    }

    // ─── View binding ───────────────────────────────────────────────────────

    private void initViews() {
        menuOverlay      = findViewById(R.id.menu_overlay);
        menuBackdrop     = findViewById(R.id.menu_backdrop);
        menuBoxContainer = findViewById(R.id.menuBoxContainer);

        moreNavOverlay   = findViewById(R.id.more_nav_overlay);
        moreNavBackdrop  = findViewById(R.id.more_nav_backdrop);
        moreNavPopup     = findViewById(R.id.more_nav_popup);
        rvListsPanel     = findViewById(R.id.rv_lists_panel);
        btnAddList       = findViewById(R.id.btn_add_list);
        tvUserSub        = findViewById(R.id.tv_user_sub);
        statCompleted    = findViewById(R.id.stat_completed);
        statStreak       = findViewById(R.id.stat_streak);

        navAvatar        = findViewById(R.id.nav_avatar);
        btnHamburger     = findViewById(R.id.btn_hamburger);
        tvHeaderGreeting = findViewById(R.id.tv_header_greeting);
        tvHeaderTitle    = findViewById(R.id.tv_header_today);
        tvHeaderDate     = findViewById(R.id.tv_header_date);
        tvTaskCountSummary = findViewById(R.id.tv_task_count_summary);
        btnSort          = findViewById(R.id.btn_sort);
        btnMore          = findViewById(R.id.btn_more);

        rvTasks          = findViewById(R.id.rv_tasks);
        layoutEmpty      = findViewById(R.id.layout_empty);
        etQuickAdd       = findViewById(R.id.et_quick_add);
        btnVoiceAdd      = findViewById(R.id.btn_voice_add);
        btnSendTask      = findViewById(R.id.btn_send_task);
        fabAddTask       = findViewById(R.id.fab_add_task);

        // Bottom nav items (5 visible items)
        navItemHome        = findViewById(R.id.nav_item_home);
        navItemCalendar    = findViewById(R.id.nav_item_calendar);
        navItemFocus       = findViewById(R.id.nav_item_focus);
        navItemAiAssistant = findViewById(R.id.nav_item_ai_assistant);
        navItemMore        = findViewById(R.id.nav_item_more);
        // Removed nav items (school/habits/settings/eisenhower/countdown → More popup)
        navItemSchool   = null;
        navItemHabits   = null;
        navItemSettings = null;

        navIconHome        = findViewById(R.id.nav_icon_home);
        navIconCalendar    = findViewById(R.id.nav_icon_calendar);
        navIconFocus       = findViewById(R.id.nav_icon_focus);
        navIconAiAssistant = findViewById(R.id.nav_icon_ai_assistant);
        navIconMore        = findViewById(R.id.nav_icon_more);
        navIconSchool      = null;
        navIconHabits      = null;
        navIconSettings    = null;

        navLabelHome        = findViewById(R.id.nav_label_home);
        navLabelCalendar    = findViewById(R.id.nav_label_calendar);
        navLabelFocus       = findViewById(R.id.nav_label_focus);
        navLabelAiAssistant = findViewById(R.id.nav_label_ai_assistant);
        navLabelMore        = findViewById(R.id.nav_label_more);
        navLabelSchool      = null;
        navLabelHabits      = null;
        navLabelSettings    = null;

        // Đặt chiều rộng drawer = 2/3 content_frame sau khi layout được đo xong
        FrameLayout contentFrame = findViewById(R.id.content_frame);
        if (contentFrame != null && menuBoxContainer != null) {
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
    }

    // ─── Header ─────────────────────────────────────────────────────────────

    private void setupHeader() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
        tvHeaderDate.setText(dateFormat.format(new Date()));
        updateHeaderGreeting();
        updateTaskCountSummary();

        btnHamburger.setOnClickListener(v -> toggleMenu());

        btnSort.setOnClickListener(v -> showSortPopupMenu(v));

        btnMore.setOnClickListener(v -> {
            ViewOptionsBottomSheet bottomSheet = ViewOptionsBottomSheet.newInstance();
            bottomSheet.setOnOptionSelectedListener(new ViewOptionsBottomSheet.OnOptionSelectedListener() {
                @Override
                public void onViewModeSelected(int viewMode) { }

                @Override
                public void onHideCompletedToggled() {
                    mainStateViewModel.toggleHideCompleted();
                    if (mainStateViewModel.isHideCompleted()) {
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
                    mainStateViewModel.toggleShowDetails();
                    boolean showDetails = mainStateViewModel.isShowDetails();
                    overdueAdapter.setShowDetails(showDetails);
                    incompleteAdapter.setShowDetails(showDetails);
                    completedAdapter.setShowDetails(showDetails);
                }

                @Override
                public void onViewOptionsClicked() { }

                @Override
                public void onAddSectionClicked() { }

                @Override
                public void onListActivitiesClicked() {
                    startActivity(new android.content.Intent(MainActivity.this, hcmute.edu.vn.tickticktodo.ui.activitylog.ActivityLogActivity.class));
                }
            });
            bottomSheet.show(getSupportFragmentManager(), "ViewOptions");
        });
    }

    private void updateHeaderGreeting() {
        if (tvHeaderGreeting == null) {
            return;
        }

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) {
            greeting = "Chào buổi sáng!";
        } else if (hour < 18) {
            greeting = "Chào buổi chiều!";
        } else {
            greeting = "Chào buổi tối!";
        }
        tvHeaderGreeting.setText(greeting);
    }

    private void updateTaskCountSummary() {
        if (tvTaskCountSummary == null) {
            return;
        }

        int todayCount = 0;
        if (taskViewModel != null && taskViewModel.getTodayIncompleteTasks().getValue() != null) {
            todayCount = taskViewModel.getTodayIncompleteTasks().getValue().size();
        } else if (mainStateViewModel.getCurrentModeValue() == 0) {
            todayCount = currentIncompleteTasks.size();
        }

        int overdueCount = currentOverdueTasks.size();
        tvTaskCountSummary.setText(getString(R.string.header_task_summary_template, todayCount, overdueCount));
    }

    private void updateDrawerStats() {
        if (statCompleted != null) {
            statCompleted.setText(String.valueOf(currentCompletedTasks.size()));
        }
        if (statStreak != null) {
            int streak = currentCompletedTasks.isEmpty() ? 0 : 1;
            statStreak.setText(String.valueOf(streak));
        }
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
        if (tvUserSub != null) {
            tvUserSub.setOnClickListener(v -> showLoginDialog());
        }
        btnAddList.setOnClickListener(v -> AddListDialog.show(this, taskViewModel));
        updateDrawerStats();

        // Tapping backdrop closes menu
        menuBackdrop.setOnClickListener(v -> closeMenu());

        setClickListenerIfPresent(R.id.panel_item_today, v -> {
            closeMenu();
            mainStateViewModel.setCurrentMode(0);
            tvHeaderTitle.setText(R.string.header_today);
            updateIncompleteList(taskViewModel.getTodayIncompleteTasks().getValue());
        });
        setClickListenerIfPresent(R.id.panel_item_next7days, v -> {
            closeMenu();
            mainStateViewModel.setCurrentMode(1);
            tvHeaderTitle.setText("7 Ngày tới");
            updateIncompleteList(taskViewModel.getNext7DaysTasks().getValue());
        });
        setClickListenerIfPresent(R.id.panel_item_inbox, v -> showDeveloperMessageDialog());
        // nav_item_eisenhower / nav_item_countdown are now in the More popup (see setupMoreNavPopup)
        setClickListenerIfPresent(R.id.panel_item_completed, v -> showHistoryDialog("Nhật ký: Đã hoàn thành", taskViewModel.getAllCompletedTasksLog()));
        setClickListenerIfPresent(R.id.panel_item_trash, v -> showHistoryDialog("Nhật ký: Quá hạn", taskViewModel.getAllOverdueTasksLog()));
        setClickListenerIfPresent(R.id.panel_item_notifications, v -> {
            closeMenu();
            Toast.makeText(this, R.string.toast_notifications_wip, Toast.LENGTH_SHORT).show();
        });
        setClickListenerIfPresent(R.id.panel_item_settings, v -> {
            closeMenu();
            showSettingsDialog();
        });
        setClickListenerIfPresent(R.id.panel_item_help, v -> {
            closeMenu();
            Toast.makeText(this, R.string.toast_help_wip, Toast.LENGTH_SHORT).show();
        });
    }

    private void setClickListenerIfPresent(int viewId, View.OnClickListener listener) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    private void onPanelItemSelected(String label) {
        closeMenu();
        tvHeaderTitle.setText(label);
    }

    
    private void showHistoryDialog(String title, androidx.lifecycle.LiveData<java.util.List<hcmute.edu.vn.tickticktodo.model.Task>> liveData) {
        closeMenu();
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_history_log);
        
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = (int) (metrics.widthPixels * 0.75);
        int height = (int) (metrics.heightPixels * 0.75);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(width, height);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        android.widget.TextView tvTitle = dialog.findViewById(R.id.tv_dialog_title);
        tvTitle.setText(title);
        
        androidx.recyclerview.widget.RecyclerView rv = dialog.findViewById(R.id.rv_history_log);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        
        hcmute.edu.vn.tickticktodo.ui.task.TaskAdapter adapter = new hcmute.edu.vn.tickticktodo.ui.task.TaskAdapter(
            (task, isChecked) -> {}, 
            task -> {}
        );
        rv.setAdapter(adapter);
        
        liveData.observe(this, tasks -> {
            if (tasks != null) {
                adapter.submitList(tasks);
            }
        });
        
        dialog.findViewById(R.id.btn_close_dialog).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDeveloperMessageDialog() {
        closeMenu();
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Tin nhắn từ nhà phát triển")
            .setMessage("Cảm ơn bạn đã sử dụng TickTickToDo! Phiên bản này đang trong quá trình thử nghiệm. Các tính năng mở rộng sẽ sớm ra mắt.")
            .setPositiveButton("Đóng", (d, w) -> d.dismiss())
            .show();
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

    // ─── Bottom Navigation ──────────────────────────────────────────────────

    /**
     * Apply system bar insets:
     *   - status bar height → header paddingTop (keeps content below notch)
     *   - navigation bar height → bottom_nav_container paddingBottom (avoids gesture area)
     */
    private void setupWindowInsets() {
        LinearLayout header = findViewById(R.id.header);
        if (header != null) {
            final int[] originalTopPad = {header.getPaddingTop()};
            ViewCompat.setOnApplyWindowInsetsListener(header, (view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(
                        view.getPaddingLeft(),
                        insets.top + originalTopPad[0],
                        view.getPaddingRight(),
                        view.getPaddingBottom()
                );
                return WindowInsetsCompat.CONSUMED;
            });
        }

        FrameLayout bottomContainer = findViewById(R.id.bottom_nav_container);
        if (bottomContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomContainer, (view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(0, 0, 0, insets.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    private void setupNavRail() {
        mainNavigationHelper.setupNavRail(
                navItemHome,
                navItemCalendar,
                navItemFocus,
                navItemSchool,
                navItemHabits,
                navItemSettings,
                navItemAiAssistant,
                new MainNavigationHelper.NavSelectionCallback() {
                    @Override
                    public void onNavSelected(int selectedId) {
                        selectNavItem(selectedId);
                    }

                    @Override
                    public void onOpenSettings() {
                        showSettingsDialog();
                    }
                }
        );

        if (navItemMore != null) {
            navItemMore.setOnClickListener(v -> toggleMoreNavPopup());
        }

        Integer selectedNavId = mainStateViewModel.getSelectedNavItem().getValue();
        selectNavItem(selectedNavId == null || selectedNavId == 0 ? R.id.nav_item_home : selectedNavId);
    }

    // ─── More nav popup ─────────────────────────────────────────────────────

    private void setupMoreNavPopup() {
        if (moreNavBackdrop != null) {
            moreNavBackdrop.setOnClickListener(v -> closeMoreNavPopup());
        }
        setClickListenerIfPresent(R.id.nav_more_school, v -> {
            closeMoreNavPopup();
            selectNavItem(R.id.nav_item_more);
            startActivity(new Intent(this, MoodleActivity.class));
        });
        setClickListenerIfPresent(R.id.nav_more_habits, v -> {
            closeMoreNavPopup();
            selectNavItem(R.id.nav_item_more);
            startActivity(HabitTrackerActivity.newIntent(this));
        });
        setClickListenerIfPresent(R.id.nav_more_matrix, v -> {
            closeMoreNavPopup();
            selectNavItem(R.id.nav_item_more);
            startActivity(new Intent(this, EisenhowerActivity.class));
        });
        setClickListenerIfPresent(R.id.nav_more_countdown, v -> {
            closeMoreNavPopup();
            selectNavItem(R.id.nav_item_more);
            startActivity(new Intent(this, hcmute.edu.vn.tickticktodo.ui.countdown.EventCountdownActivity.class));
        });
    }

    private void toggleMoreNavPopup() {
        if (moreNavOverlay != null && moreNavOverlay.getVisibility() == View.VISIBLE) {
            closeMoreNavPopup();
        } else {
            openMoreNavPopup();
        }
    }

    private void openMoreNavPopup() {
        if (moreNavOverlay == null) return;
        selectNavItem(R.id.nav_item_more);
        moreNavOverlay.setVisibility(View.VISIBLE);
        moreNavBackdrop.setAlpha(0f);
        moreNavBackdrop.animate().alpha(1f).setDuration(220).start();
        // Slide card up from below
        moreNavPopup.post(() -> {
            float startY = moreNavPopup.getHeight() + 80f;
            moreNavPopup.setTranslationY(startY);
            moreNavPopup.animate()
                    .translationY(0f)
                    .setDuration(320)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .start();
        });
    }

    private void closeMoreNavPopup() {
        if (moreNavOverlay == null || moreNavOverlay.getVisibility() != View.VISIBLE) return;
        float endY = moreNavPopup.getHeight() + 80f;
        moreNavBackdrop.animate().alpha(0f).setDuration(180).start();
        moreNavPopup.animate()
                .translationY(endY)
                .setDuration(220)
                .setInterpolator(new FastOutSlowInInterpolator())
                .withEndAction(() -> moreNavOverlay.setVisibility(View.GONE))
                .start();
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
        mainStateViewModel.setSelectedNavItem(selectedId);
        // Only the 5 visible bottom nav items
        int[] ids      = {R.id.nav_item_home, R.id.nav_item_calendar, R.id.nav_item_ai_assistant, R.id.nav_item_focus, R.id.nav_item_more};
        ImageView[] icons  = {navIconHome, navIconCalendar, navIconAiAssistant, navIconFocus, navIconMore};
        TextView[]  labels = {navLabelHome, navLabelCalendar, navLabelAiAssistant, navLabelFocus, navLabelMore};
        mainNavigationHelper.selectNavItem(selectedId, ids, icons, labels);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mainPermissionHandler.syncFloatingAssistantState(PREFS_NAME, KEY_FLOATING_ASSISTANT_ENABLED);
        Integer selectedNavId = mainStateViewModel.getSelectedNavItem().getValue();
        selectNavItem(selectedNavId == null || selectedNavId == 0 ? R.id.nav_item_home : selectedNavId);
    }

    // ─── Adapters ───────────────────────────────────────────────────────────

    private void initAdapters() {
        // Overdue
        overdueAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> TaskDetailBottomSheet.newInstance(task.getId()).show(getSupportFragmentManager(), "TaskDetail")
        );
        overdueHeaderAdapter = new HeaderAdapter();
        overdueHeaderAdapter.setOnHeaderClickListener(isExpanded -> {
            overdueAdapter.submitList(isExpanded ? new ArrayList<>(currentOverdueTasks) : new ArrayList<>());
        });

        // Incomplete
        todayHeaderAdapter = new HeaderAdapter();
        incompleteAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> TaskDetailBottomSheet.newInstance(task.getId()).show(getSupportFragmentManager(), "TaskDetail")
        );
        todayHeaderAdapter.setOnHeaderClickListener(isExpanded -> {
            incompleteAdapter.submitList(isExpanded ? new ArrayList<>(currentIncompleteTasks) : new ArrayList<>());
        });

        // Completed
        completedHeaderAdapter = new HeaderAdapter();
        completedAdapter = new TaskAdapter(
                (task, isChecked) -> taskViewModel.markTaskAsCompleted(task, isChecked),
                task -> TaskDetailBottomSheet.newInstance(task.getId()).show(getSupportFragmentManager(), "TaskDetail")
        );
        completedHeaderAdapter.setOnHeaderClickListener(isExpanded -> {
            completedAdapter.submitList(isExpanded ? new ArrayList<>(currentCompletedTasks) : new ArrayList<>());
        });

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

        // Swipe right on task list → open the left drawer
        GestureDetectorCompat swipeOpenDetector = new GestureDetectorCompat(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                        if (e1 == null || e2 == null) return false;
                        float dX = e2.getX() - e1.getX();
                        float dY = Math.abs(e2.getY() - e1.getY());
                        if (dX > 80 && Math.abs(vX) > 200 && dY < Math.abs(dX)) {
                            openMenu();
                            return true;
                        }
                        return false;
                    }
                });
        rvTasks.setOnTouchListener((v, event) -> {
            swipeOpenDetector.onTouchEvent(event);
            return false; // let RecyclerView handle scrolling/clicks normally
        });
    }

    private void handleSwipeDelete(int position) {
        int overdueHead = overdueHeaderAdapter.getItemCount();
        int overdue = overdueAdapter.getItemCount();
        int todayHead = todayHeaderAdapter.getItemCount();
        int incomplete = incompleteAdapter.getItemCount();
        int compHead = mainStateViewModel.isHideCompleted() ? 0 : completedHeaderAdapter.getItemCount();
        int comp = mainStateViewModel.isHideCompleted() ? 0 : completedAdapter.getItemCount();

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

        if (btnVoiceAdd != null) {
            btnVoiceAdd.setOnClickListener(v -> startActivity(new Intent(this, VoicePromptActivity.class)));
        }

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

        showQuickAddDueTimePicker(title);
    }

    private void showQuickAddDueTimePicker(String title) {
        Calendar now = Calendar.getInstance();

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    Calendar dueCal = Calendar.getInstance();
                    dueCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    dueCal.set(Calendar.MINUTE, minute);
                    dueCal.set(Calendar.SECOND, 0);
                    dueCal.set(Calendar.MILLISECOND, 0);

                    taskViewModel.insert(new Task(title, "", dueCal.getTimeInMillis(), false, 0));
                    etQuickAdd.setText("");
                    hideKeyboard();
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true
        );
        timePickerDialog.setTitle(getString(R.string.quick_add_due_time_title));
        timePickerDialog.setOnCancelListener(dialog ->
                Toast.makeText(this, R.string.quick_add_due_time_required, Toast.LENGTH_SHORT).show());
        timePickerDialog.show();
    }

    // ─── FAB ────────────────────────────────────────────────────────────────

    private void setupFab() {
        fabAddTask.setOnClickListener(v ->
                AddTaskBottomSheet.newInstance().show(getSupportFragmentManager(), "AddTask"));
    }

    // ─── ViewModel / LiveData ───────────────────────────────────────────────

    private void setupViewModel() {
        taskViewModel.getSortModeLiveData().observe(this, mode -> {
            if (mainStateViewModel.getCurrentModeValue() == 0) updateIncompleteList(taskViewModel.getTodayIncompleteTasks().getValue());
            else if (mainStateViewModel.getCurrentModeValue() == 1) updateIncompleteList(taskViewModel.getNext7DaysTasks().getValue());
        });

        taskViewModel.getOverdueTasks().observe(this, overdueTasks -> {
            currentOverdueTasks = overdueTasks != null ? overdueTasks : new ArrayList<>();
            if (overdueHeaderAdapter.isExpanded()) {
                overdueAdapter.submitList(new ArrayList<>(currentOverdueTasks));
            }
            overdueHeaderAdapter.setHeader("Đã quá hạn", currentOverdueTasks.size());
            updateTaskCountSummary();
            updateDrawerStats();
            updateEmptyStateCheck();
        });

        taskViewModel.getTodayIncompleteTasks().observe(this, incompleteTasks -> {
            if (mainStateViewModel.getCurrentModeValue() == 0) updateIncompleteList(incompleteTasks);
            updateTaskCountSummary();
        });

        taskViewModel.getNext7DaysTasks().observe(this, nextTasks -> {
            if (mainStateViewModel.getCurrentModeValue() == 1) updateIncompleteList(nextTasks);
        });

        taskViewModel.getTodayCompletedTasks().observe(this, completedTasks -> {
            currentCompletedTasks = completedTasks != null ? completedTasks : new ArrayList<>();
            if (completedHeaderAdapter.isExpanded()) {
                completedAdapter.submitList(new ArrayList<>(currentCompletedTasks));
            }
            completedHeaderAdapter.setHeader("Hoàn thành", currentCompletedTasks.size());
            updateTaskCountSummary();
            updateDrawerStats();
            updateEmptyStateCheck();
        });

        taskViewModel.getAllLists().observe(this, lists -> {
            if (lists != null) listPanelAdapter.submitList(new ArrayList<>(lists));
        });

        updateTaskCountSummary();
        updateDrawerStats();
    }

    private void updateIncompleteList(List<Task> tasks) {
        currentIncompleteTasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
        
        int sortMode = taskViewModel.getCurrentSortMode();
        if (sortMode == TaskViewModel.SORT_BY_PRIORITY) {
            java.util.Collections.sort(currentIncompleteTasks, (t1, t2) -> Integer.compare(t2.getPriority(), t1.getPriority()));
        } else if (sortMode == TaskViewModel.SORT_BY_DATE_DESC) {
            java.util.Collections.sort(currentIncompleteTasks, (t1, t2) -> {
                Long d1 = t1.getDueDate();
                Long d2 = t2.getDueDate();
                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1;
                if (d2 == null) return -1;
                return d2.compareTo(d1);
            });
        }

        if (todayHeaderAdapter.isExpanded()) {
            incompleteAdapter.submitList(new ArrayList<>(currentIncompleteTasks));
        }
        todayHeaderAdapter.setHeader(mainStateViewModel.getCurrentModeValue() == 0 ? "Hôm nay" : "7 ngày tới", currentIncompleteTasks.size());
        updateTaskCountSummary();
        updateEmptyStateCheck();
    }

    private void updateEmptyStateCheck() {
        int total = currentIncompleteTasks.size() + currentOverdueTasks.size() + 
                    (mainStateViewModel.isHideCompleted() ? 0 : currentCompletedTasks.size());
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
        if (moreNavOverlay != null && moreNavOverlay.getVisibility() == View.VISIBLE) {
            closeMoreNavPopup();
        } else if (menuOverlay != null && menuOverlay.getVisibility() == View.VISIBLE) {
            closeMenu();
        } else {
            super.onBackPressed();
        }
    }
}
