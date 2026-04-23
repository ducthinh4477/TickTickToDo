package hcmute.edu.vn.doinbot.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import hcmute.edu.vn.doinbot.data.model.AgentDecisionLogEntity;

@Dao
public interface AgentDecisionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AgentDecisionLogEntity entity);

    @Query("SELECT * FROM agent_decision_logs ORDER BY created_at_millis DESC LIMIT :limit")
    List<AgentDecisionLogEntity> getRecentLogsSync(int limit);

    @Query("DELETE FROM agent_decision_logs WHERE created_at_millis < :cutoffMillis")
    int deleteOlderThan(long cutoffMillis);
}
