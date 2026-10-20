package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthFieldSubmission;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthFieldSubmissionRepository extends JpaRepository<EarthFieldSubmission, UUID> {

    @Query("SELECT s FROM EarthFieldSubmission s WHERE s.tenantId = :tenantId AND s.submissionId = :submissionId")
    Optional<EarthFieldSubmission> findBySubmissionId(TenantId tenantId, UUID submissionId);

    @Query("SELECT s FROM EarthFieldSubmission s WHERE s.tenantId = :tenantId AND s.submissionId IN :ids")
    List<EarthFieldSubmission> findBySubmissionIds(TenantId tenantId, Collection<UUID> ids);

    @Query("SELECT s FROM EarthFieldSubmission s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<EarthFieldSubmission> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT s FROM EarthFieldSubmission s WHERE s.tenantId = :tenantId AND s.status = 'CONFLICT' AND s.reviewDecision IS NULL ORDER BY s.receivedAt DESC")
    List<EarthFieldSubmission> findOpenConflicts(TenantId tenantId);

    /** Entries of one kind for a machine that were applied, newest captured first. */
    @Query("SELECT s FROM EarthFieldSubmission s WHERE s.tenantId = :tenantId AND s.assetId = :assetId AND s.kind = :kind AND (s.status = 'SYNCED' OR s.reviewDecision = 'APPLIED') ORDER BY s.capturedAt DESC")
    List<EarthFieldSubmission> findAppliedByAsset(TenantId tenantId, UUID assetId, String kind, Pageable pageable);

    @Query("SELECT s FROM EarthFieldSubmission s WHERE s.tenantId = :tenantId AND s.submittedBy = :userId ORDER BY s.receivedAt DESC")
    List<EarthFieldSubmission> findMine(TenantId tenantId, UUID userId, Pageable pageable);
}
