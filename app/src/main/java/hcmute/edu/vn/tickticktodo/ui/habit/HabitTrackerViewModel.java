package hcmute.edu.vn.tickticktodo.ui.habit;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hcmute.edu.vn.tickticktodo.helper.GeminiManager;
import hcmute.edu.vn.tickticktodo.model.Habit;
import hcmute.edu.vn.tickticktodo.model.HabitHeatmapCell;
import hcmute.edu.vn.tickticktodo.model.HabitLog;
import hcmute.edu.vn.tickticktodo.data.repository.HabitRepository;
import hcmute.edu.vn.tickticktodo.core.background.FloatingAssistantService;

public class HabitTrackerViewModel extends AndroidViewModel {

    private static final int HEATMAP_TOTAL_DAYS = 84;
    private static final String HABIT_NUDGE_PREFS = "habit_nudge_prefs";

    private final HabitRepository repository;
    private final LiveData<List<Habit>> habits;
    private final MutableLiveData<Long> selectedHabitId = new MutableLiveData<>(-1L);
    private final long heatmapStart;
    private final long heatmapEnd;
    private final LiveData<List<HabitLog>> selectedHabitLogs;

    public HabitTrackerViewModel(@NonNull Application application) {
        super(application);
        repository = new HabitRepository(application);
        habits = repository.getAllHabits();

        long todayStart = getStartOfToday();
        heatmapEnd = todayStart + (24L * 60L * 60L * 1000L);
        heatmapStart = todayStart - ((HEATMAP_TOTAL_DAYS - 1L) * 24L * 60L * 60L * 1000L);

        selectedHabitLogs = Transformations.switchMap(selectedHabitId, habitId -> {
            if (habitId == null || habitId <= 0) {
                MutableLiveData<List<HabitLog>> empty = new MutableLiveData<>();
                empty.setValue(new ArrayList<>());
                return empty;
            }
            return repository.getHabitLogsByRange(habitId, heatmapStart, heatmapEnd);
        });
    }

    public LiveData<List<Habit>> getHabits() {
        return habits;
    }

    public LiveData<List<HabitLog>> getSelectedHabitLogs() {
        return selectedHabitLogs;
    }

    public long getHeatmapStart() {
        return heatmapStart;
    }

    public int getHeatmapTotalDays() {
        return HEATMAP_TOTAL_DAYS;
    }

    public long getSelectedHabitId() {
        Long value = selectedHabitId.getValue();
        return value == null ? -1L : value;
    }

    public void selectHabit(long habitId) {
        selectedHabitId.setValue(habitId);
    }

    public void addHabit(String name, String icon) {
        Habit habit = new Habit(name, icon);
        repository.insertHabit(habit, this::selectHabit);
    }

    public void checkInHabit(long habitId) {
        repository.checkInHabit(habitId, getStartOfToday());
    }

    public List<HabitHeatmapCell> buildHeatmapCells(List<HabitLog> logs) {
        Set<Long> completedDays = new HashSet<>();
        if (logs != null) {
            for (HabitLog log : logs) {
                if (log.isCompleted()) {
                    completedDays.add(log.getDateMillis());
                }
            }
        }

        List<HabitHeatmapCell> result = new ArrayList<>();
        for (int i = 0; i < HEATMAP_TOTAL_DAYS; i++) {
            long date = heatmapStart + (i * 24L * 60L * 60L * 1000L);
            result.add(new HabitHeatmapCell(date, completedDays.contains(date)));
        }
        return result;
    }

    public void evaluateNudgeForHabits(List<Habit> habits) {
        if (habits == null || habits.isEmpty()) {
            return;
        }

        long todayStart = getStartOfToday();
        for (Habit habit : habits) {
            repository.isSkippedThreeConsecutiveDays(habit.getId(), todayStart, skipped -> {
                if (skipped) {
                    maybeSendHabitNudge(habit, todayStart);
                }
            });
        }
    }

    private void maybeSendHabitNudge(Habit habit, long todayStart) {
        SharedPreferences prefs = getApplication().getSharedPreferences(HABIT_NUDGE_PREFS, Context.MODE_PRIVATE);
        String throttleKey = "nudge_" + habit.getId() + "_" + todayStart;
        if (prefs.getBoolean(throttleKey, false)) {
            return;
        }

        String fallback = "Ban da nghi " + habit.getName() + " 3 ngay roi, hom nay thu lam lai 10 phut nhe.";
        String prompt = "Nguoi dung da bo lo thoi quen '" + habit.getName() + "' 3 ngay lien tiep. "
                + "Hay viet mot cau dong vien ngan, than thien, thuc te, khuyen ho bat dau lai voi muc tieu nho.";

        GeminiManager.getInstance().generateResponse(prompt, new GeminiManager.ResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                sendHabitNudgeToFloating(responseText == null || responseText.trim().isEmpty() ? fallback : responseText.trim());
                prefs.edit().putBoolean(throttleKey, true).apply();
            }

            @Override
            public void onError(String errorMessage) {
                sendHabitNudgeToFloating(fallback);
                prefs.edit().putBoolean(throttleKey, true).apply();
            }
        });
    }

    private void sendHabitNudgeToFloating(String text) {
        Intent intent = new Intent(getApplication(), FloatingAssistantService.class);
        intent.setAction(FloatingAssistantService.ACTION_SHOW_HABIT_NUDGE);
        intent.putExtra(FloatingAssistantService.EXTRA_HABIT_NUDGE_TEXT, text);
        try {
            ContextCompat.startForegroundService(getApplication(), intent);
        } catch (Exception ignored) {
        }
    }

    private long getStartOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
