package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpLitigationCase;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpLitigationCaseRepository extends JpaRepository<LpLitigationCase, UUID> {

    @Query("SELECT c FROM LpLitigationCase c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<LpLitigationCase> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT c FROM LpLitigationCase c WHERE c.tenantId = :tenantId AND c.matterId = :matterId")
    Optional<LpLitigationCase> findForMatter(TenantId tenantId, UUID matterId);

    /** The firm's cases, leaving out matters and clients a wall bars the caller from. An empty stage means any; pattern is already %text%. */
    @Query("SELECT c FROM LpLitigationCase c WHERE c.tenantId = :tenantId AND c.matterId NOT IN :barredMatterIds AND c.clientId NOT IN :barredClientIds "
            + "AND (:includeConcluded = true OR c.stage <> 'CONCLUDED') AND (:stage = '' OR c.stage = :stage) "
            + "AND (LOWER(COALESCE(c.caseNumber, '')) LIKE :pattern OR LOWER(c.courtName) LIKE :pattern OR LOWER(COALESCE(c.opposingParty, '')) LIKE :pattern) "
            + "ORDER BY c.updatedAt DESC")
    Page<LpLitigationCase> findFirmExcluding(TenantId tenantId, List<UUID> barredMatterIds, List<UUID> barredClientIds, boolean includeConcluded, String stage, String pattern, Pageable pageable);
}
