package hcmute.edu.vn.tickticktodo.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hcmute.edu.vn.tickticktodo.model.Habit;
import hcmute.edu.vn.tickticktodo.model.HabitLog;

@Dao
public interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY id ASC")
    LiveData<List<Habit>> getAllHabits();

    @Insert
    long insertHabit(Habit habit);

    @Update
    void updateHabit(Habit habit);

    @Delete
    void deleteHabit(Habit habit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertHabitLog(HabitLog habitLog);

    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId AND date_millis >= :startDate AND date_millis < :endDate ORDER BY date_millis ASC")
    LiveData<List<HabitLog>> getHabitLogsByRange(long habitId, long startDate, long endDate);

    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId AND date_millis >= :startDate AND date_millis < :endDate ORDER BY date_millis ASC")
    List<HabitLog> getHabitLogsByRangeSync(long habitId, long startDate, long endDate);

    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId ORDER BY date_millis DESC LIMIT :limit")
    List<HabitLog> getRecentHabitLogsSync(long habitId, int limit);
}
