package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<Audit, UUID> {

    @Query("SELECT a FROM Audit a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<Audit> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT a FROM Audit a WHERE a.tenantId = :tenantId AND (:status IS NULL OR a.status = :status)
        ORDER BY a.plannedStart DESC, a.reference
        """)
    List<Audit> search(TenantId tenantId, AuditStatus status);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();

    /** Cross-tenant — daily sweep: planned end has passed and the audit is not finished. */
    @Query("SELECT a FROM Audit a WHERE a.plannedEnd < :today AND a.status IN (za.co.handyflow.platform.legalcompliance.domain.model.AuditStatus.PLANNED, za.co.handyflow.platform.legalcompliance.domain.model.AuditStatus.FIELDWORK, za.co.handyflow.platform.legalcompliance.domain.model.AuditStatus.REPORTING)")
    List<Audit> findOverdueAcrossTenants(LocalDate today);
}
