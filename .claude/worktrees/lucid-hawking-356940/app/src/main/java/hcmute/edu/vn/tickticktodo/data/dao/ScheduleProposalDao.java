package hcmute.edu.vn.doinbot.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import hcmute.edu.vn.doinbot.data.model.ScheduleBlockEntity;
import hcmute.edu.vn.doinbot.data.model.ScheduleProposalEntity;

@Dao
public interface ScheduleProposalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertProposal(ScheduleProposalEntity proposalEntity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBlocks(List<ScheduleBlockEntity> blockEntities);

    @Query("DELETE FROM schedule_blocks WHERE proposal_id = :proposalId")
    void deleteBlocksByProposalId(String proposalId);

    @Query("SELECT * FROM schedule_proposals WHERE id = :proposalId LIMIT 1")
    ScheduleProposalEntity getProposalByIdSync(String proposalId);

    @Query("SELECT * FROM schedule_blocks WHERE proposal_id = :proposalId ORDER BY option_id ASC, start_millis ASC")
    List<ScheduleBlockEntity> getBlocksByProposalIdSync(String proposalId);

    @Query("SELECT * FROM schedule_blocks WHERE proposal_id = :proposalId AND option_id = :optionId ORDER BY start_millis ASC")
    List<ScheduleBlockEntity> getBlocksForOptionSync(String proposalId, String optionId);

    @Query("SELECT * FROM schedule_proposals WHERE proposal_type = :proposalType ORDER BY generated_at_millis DESC LIMIT 1")
    ScheduleProposalEntity getLatestProposalByTypeSync(String proposalType);

    @Query("UPDATE schedule_proposals SET status = :status, applied_option_id = :appliedOptionId, applied_at_millis = :appliedAtMillis WHERE id = :proposalId")
    void markProposalApplied(String proposalId, String status, String appliedOptionId, long appliedAtMillis);
}
