package za.co.handyflow.platform.creative.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.creative.domain.model.CreProof;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreProofRepository extends JpaRepository<CreProof, UUID> {

    List<CreProof> findByJobIdOrderByVersionNumberDesc(UUID jobId);

    Optional<CreProof> findByApprovalToken(String token);

    @Query("SELECT COALESCE(MAX(p.versionNumber), 0) FROM CreProof p WHERE p.jobId = :jobId")
    int findMaxVersion(UUID jobId);

    @Query("SELECT p FROM CreProof p WHERE p.jobId = :jobId AND p.status = 'PENDING'")
    List<CreProof> findPendingByJobId(UUID jobId);
}
