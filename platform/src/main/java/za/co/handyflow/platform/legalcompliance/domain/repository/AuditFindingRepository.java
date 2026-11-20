package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Repository("legalComplianceAuditFindingRepository")
public interface AuditFindingRepository extends JpaRepository<AuditFinding, UUID> {

    @Query("SELECT f FROM LcAuditFinding f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<AuditFinding> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT f FROM LcAuditFinding f WHERE f.tenantId = :tenantId
        AND (:auditId IS NULL OR f.auditId = :auditId)
        AND (:status IS NULL OR f.status = :status)
        AND (:includeClosed = TRUE OR f.status <> za.co.handyflow.platform.legalcompliance.domain.model.AuditFindingStatus.CLOSED)
        ORDER BY f.dueDate, f.reference
        """)
    List<AuditFinding> search(TenantId tenantId, UUID auditId, AuditFindingStatus status, boolean includeClosed, Pageable pageable);

    /** Everything, for summaries, coverage and sweeps that must count every row. */
    default List<AuditFinding> search(TenantId tenantId, UUID auditId, AuditFindingStatus status, boolean includeClosed) { return search(tenantId, auditId, status, includeClosed, Pageable.unpaged()); }

    /** In the query's own order, at most RowLimit.MAX_ROWS: for list screens only. */
    default List<AuditFinding> searchCapped(TenantId tenantId, UUID auditId, AuditFindingStatus status, boolean includeClosed) { return search(tenantId, auditId, status, includeClosed, RowLimit.first()); }

    @Query("SELECT f FROM LcAuditFinding f WHERE f.tenantId = :tenantId AND f.auditId = :auditId")
    List<AuditFinding> findByAudit(TenantId tenantId, UUID auditId);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();

    /** Cross-tenant — daily sweep. */
    @Query("SELECT f FROM LcAuditFinding f WHERE f.status <> za.co.handyflow.platform.legalcompliance.domain.model.AuditFindingStatus.CLOSED AND f.dueDate < :today")
    List<AuditFinding> findOverdueAcrossTenants(LocalDate today);
}
