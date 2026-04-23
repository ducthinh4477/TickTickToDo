package hcmute.edu.vn.doinbot.data.repository;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hcmute.edu.vn.doinbot.data.dao.HabitDao;
import hcmute.edu.vn.doinbot.data.database.TaskDatabase;
import hcmute.edu.vn.doinbot.model.Habit;
import hcmute.edu.vn.doinbot.model.HabitLog;

public class HabitRepository {

    public interface HabitInsertCallback {
        void onInserted(long habitId);
    }

    private final HabitDao habitDao;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public HabitRepository(Application application) {
        TaskDatabase db = TaskDatabase.getInstance(application);
        habitDao = db.habitDao();
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public LiveData<List<Habit>> getAllHabits() {
        return habitDao.getAllHabits();
    }

    public LiveData<List<HabitLog>> getHabitLogsByRange(long habitId, long startDate, long endDate) {
        return habitDao.getHabitLogsByRange(habitId, startDate, endDate);
    }

    public void insertHabit(Habit habit, HabitInsertCallback callback) {
        executor.execute(() -> {
            long id = habitDao.insertHabit(habit);
            if (callback != null) {
                mainHandler.post(() -> callback.onInserted(id));
            }
        });
    }

    public void updateHabit(Habit habit) {
        executor.execute(() -> habitDao.updateHabit(habit));
    }

    public void deleteHabit(Habit habit) {
        executor.execute(() -> habitDao.deleteHabit(habit));
    }

    public void checkInHabit(long habitId, long dateMillis) {
        executor.execute(() -> habitDao.upsertHabitLog(new HabitLog(habitId, dateMillis, true)));
    }

    public void clearCheckInHabit(long habitId, long dateMillis) {
        executor.execute(() -> habitDao.upsertHabitLog(new HabitLog(habitId, dateMillis, false)));
    }

    public void isSkippedThreeConsecutiveDays(long habitId, long todayStartMillis, java.util.function.Consumer<Boolean> callback) {
        executor.execute(() -> {
            long start = todayStartMillis - (3L * 24L * 60L * 60L * 1000L);
            List<HabitLog> logs = habitDao.getHabitLogsByRangeSync(habitId, start, todayStartMillis);

            if (logs == null || logs.isEmpty()) {
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(false));
                }
                return;
            }

            Set<Long> completedDays = new HashSet<>();
            for (HabitLog log : logs) {
                if (log.isCompleted()) {
                    completedDays.add(log.getDateMillis());
                }
            }

            boolean skippedAll = true;
            for (int i = 1; i <= 3; i++) {
                long day = todayStartMillis - (i * 24L * 60L * 60L * 1000L);
                if (completedDays.contains(day)) {
                    skippedAll = false;
                    break;
                }
            }

            if (callback != null) {
                boolean result = skippedAll;
                mainHandler.post(() -> callback.accept(result));
            }
        });
    }
}
