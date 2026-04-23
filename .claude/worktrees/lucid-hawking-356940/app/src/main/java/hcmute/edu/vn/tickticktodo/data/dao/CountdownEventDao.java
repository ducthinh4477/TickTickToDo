package hcmute.edu.vn.doinbot.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hcmute.edu.vn.doinbot.model.CountdownEvent;

@Dao
public interface CountdownEventDao {
    @Insert
    void insert(CountdownEvent event);

    @Update
    void update(CountdownEvent event);

    @Delete
    void delete(CountdownEvent event);

    @Query("SELECT * FROM countdown_events ORDER BY dateMillis ASC")
    LiveData<List<CountdownEvent>> getAllEvents();

    @Query("SELECT * FROM countdown_events ORDER BY dateMillis ASC")
    List<CountdownEvent> getAllEventsSync();

    @Query("SELECT * FROM countdown_events WHERE dateMillis >= :nowMillis ORDER BY dateMillis ASC LIMIT :limit")
    List<CountdownEvent> getUpcomingEventsSync(long nowMillis, int limit);

    @Query("SELECT * FROM countdown_events WHERE dateMillis < :nowMillis ORDER BY dateMillis DESC LIMIT :limit")
    List<CountdownEvent> getRecentPastEventsSync(long nowMillis, int limit);
}
