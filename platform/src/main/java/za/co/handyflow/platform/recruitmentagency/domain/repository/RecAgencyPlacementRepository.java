package za.co.handyflow.platform.recruitmentagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyPlacement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecAgencyPlacementRepository extends JpaRepository<RecAgencyPlacement, UUID> {

    @Query("SELECT p FROM RecAgencyPlacement p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<RecAgencyPlacement> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT p FROM RecAgencyPlacement p WHERE p.requisitionId = :requisitionId ORDER BY p.createdAt DESC")
    List<RecAgencyPlacement> findByRequisition(@Param("requisitionId") UUID requisitionId);

    @Query("SELECT p FROM RecAgencyPlacement p WHERE p.candidateId = :candidateId ORDER BY p.createdAt DESC")
    List<RecAgencyPlacement> findByCandidate(@Param("candidateId") UUID candidateId);

    /** Backs the guarantee-period sweep — every currently-PLACED placement, for the scheduler to check against guaranteeEndsAt. */
    @Query("SELECT p FROM RecAgencyPlacement p WHERE p.tenantId = :tenantId AND p.stage = 'PLACED'")
    List<RecAgencyPlacement> findAllPlaced(@Param("tenantId") UUID tenantId);

    boolean existsByRequisitionIdAndCandidateId(UUID requisitionId, UUID candidateId);
}