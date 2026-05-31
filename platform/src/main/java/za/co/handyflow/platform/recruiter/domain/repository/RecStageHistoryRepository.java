package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.recruiter.domain.model.RecStageHistory;

import java.util.List;
import java.util.UUID;

public interface RecStageHistoryRepository extends JpaRepository<RecStageHistory, UUID> {
    List<RecStageHistory> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
