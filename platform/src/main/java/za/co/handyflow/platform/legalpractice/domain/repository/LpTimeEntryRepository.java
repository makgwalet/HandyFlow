package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpTimeEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code findUnbilledByMatter} is what {@code LpBillingService.generateInvoice()}
 * calls to resolve the caller-supplied {@code timeEntryIds} against real,
 * still-billable rows for the matter in question.
 */
public interface LpTimeEntryRepository extends JpaRepository<LpTimeEntry, UUID> {

    @Query("SELECT t FROM LpTimeEntry t WHERE t.tenantId = :tenantId AND t.id = :id")
    Optional<LpTimeEntry> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT t FROM LpTimeEntry t
        WHERE t.tenantId = :tenantId AND t.matterId = :matterId
        ORDER BY t.entryDate DESC
        """)
    Page<LpTimeEntry> findAllForMatter(TenantId tenantId, UUID matterId, Pageable pageable);

    @Query("""
        SELECT t FROM LpTimeEntry t
        WHERE t.tenantId = :tenantId AND t.matterId = :matterId AND t.status = 'UNBILLED'
        ORDER BY t.entryDate ASC
        """)
    List<LpTimeEntry> findUnbilledByMatter(TenantId tenantId, UUID matterId);
}
