package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpDisbursement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Mirrors {@link LpTimeEntryRepository}'s shape exactly for the sibling billable-cost entity. */
public interface LpDisbursementRepository extends JpaRepository<LpDisbursement, UUID> {

    @Query("SELECT d FROM LpDisbursement d WHERE d.tenantId = :tenantId AND d.id = :id")
    Optional<LpDisbursement> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT d FROM LpDisbursement d
        WHERE d.tenantId = :tenantId AND d.matterId = :matterId
        ORDER BY d.disbursementDate DESC
        """)
    Page<LpDisbursement> findAllForMatter(TenantId tenantId, UUID matterId, Pageable pageable);

    @Query("""
        SELECT d FROM LpDisbursement d
        WHERE d.tenantId = :tenantId AND d.matterId = :matterId AND d.status = 'UNBILLED'
        ORDER BY d.disbursementDate ASC
        """)
    List<LpDisbursement> findUnbilledByMatter(TenantId tenantId, UUID matterId);
}
