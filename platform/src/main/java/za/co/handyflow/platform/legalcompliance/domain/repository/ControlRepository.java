package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ControlRepository extends JpaRepository<Control, UUID> {

    @Query("SELECT c FROM Control c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<Control> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT c FROM Control c WHERE c.tenantId = :tenantId
        AND (:status IS NULL OR c.status = :status)
        AND (:includeRetired = TRUE OR c.status <> za.co.handyflow.platform.legalcompliance.domain.model.ControlStatus.RETIRED)
        ORDER BY c.reference
        """)
    List<Control> search(TenantId tenantId, ControlStatus status, boolean includeRetired);

    /** Cross-tenant — daily assurance sweep: active controls whose test is overdue, or never tested since activation before the cut-off. */
    @Query("""
        SELECT c FROM Control c WHERE c.status = za.co.handyflow.platform.legalcompliance.domain.model.ControlStatus.ACTIVE
        AND ((c.nextTestDue IS NOT NULL AND c.nextTestDue < :today)
          OR (c.lastTestedAt IS NULL AND c.activatedAt < :activatedBefore))
        """)
    List<Control> findTestDueAcrossTenants(LocalDate today, java.time.Instant activatedBefore);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
