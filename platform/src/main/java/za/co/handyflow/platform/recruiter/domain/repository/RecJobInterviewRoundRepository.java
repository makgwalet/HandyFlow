package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.recruiter.domain.model.RecJobInterviewRound;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecJobInterviewRoundRepository extends JpaRepository<RecJobInterviewRound, UUID> {

    @Query("SELECT r FROM RecJobInterviewRound r WHERE r.jobId = :jobId ORDER BY r.sequence ASC")
    List<RecJobInterviewRound> findByJobIdOrderBySequenceAsc(UUID jobId);

    Optional<RecJobInterviewRound> findByIdAndTenantId(UUID id, TenantId tenantId);
}