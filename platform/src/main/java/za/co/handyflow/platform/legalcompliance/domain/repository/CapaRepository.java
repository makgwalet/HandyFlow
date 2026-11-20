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

public interface CapaRepository extends JpaRepository<Capa, UUID> {

    @Query("SELECT c FROM Capa c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<Capa> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT c FROM Capa c WHERE c.tenantId = :tenantId
        AND (:status IS NULL OR c.status = :status)
        AND (:includeFinished = TRUE OR c.status NOT IN (za.co.handyflow.platform.legalcompliance.domain.model.CapaStatus.VERIFIED, za.co.handyflow.platform.legalcompliance.domain.model.CapaStatus.CANCELLED))
        ORDER BY c.dueDate, c.reference
        """)
    List<Capa> search(TenantId tenantId, CapaStatus status, boolean includeFinished, Pageable pageable);

    /** Everything, for summaries, coverage and sweeps that must count every row. */
    default List<Capa> search(TenantId tenantId, CapaStatus status, boolean includeFinished) { return search(tenantId, status, includeFinished, Pageable.unpaged()); }

    /** In the query's own order, at most RowLimit.MAX_ROWS: for list screens only. */
    default List<Capa> searchCapped(TenantId tenantId, CapaStatus status, boolean includeFinished) { return search(tenantId, status, includeFinished, RowLimit.first()); }

    @Query("SELECT c FROM Capa c WHERE c.tenantId = :tenantId AND c.findingId = :findingId")
    List<Capa> findByFinding(TenantId tenantId, UUID findingId);

    @Query("SELECT c FROM Capa c WHERE c.tenantId = :tenantId AND c.auditFindingId = :auditFindingId")
    List<Capa> findByAuditFinding(TenantId tenantId, UUID auditFindingId);

    @Query("SELECT c FROM Capa c WHERE c.tenantId = :tenantId AND c.incidentId = :incidentId")
    List<Capa> findByIncident(TenantId tenantId, UUID incidentId);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();

    /** Cross-tenant — daily sweep. */
    @Query("SELECT c FROM Capa c WHERE c.status NOT IN (za.co.handyflow.platform.legalcompliance.domain.model.CapaStatus.VERIFIED, za.co.handyflow.platform.legalcompliance.domain.model.CapaStatus.CANCELLED) AND c.dueDate < :today")
    List<Capa> findOverdueAcrossTenants(LocalDate today);
}
