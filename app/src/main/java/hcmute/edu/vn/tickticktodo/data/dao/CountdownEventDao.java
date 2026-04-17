package hcmute.edu.vn.tickticktodo.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import hcmute.edu.vn.tickticktodo.model.CountdownEvent;

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
}
