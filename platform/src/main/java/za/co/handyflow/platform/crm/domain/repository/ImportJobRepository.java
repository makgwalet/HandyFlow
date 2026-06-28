package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.ImportJob;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * ImportJobRepository — persistence for CSV import job tracking.
 *
 * WHY always filter by tenantId?
 * Same reason as CustomerRepository — defence in depth.
 * A tenant must never be able to see another tenant's import jobs,
 * even if they know the job UUID.
 */
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    /**
     * Find a specific import job scoped to a tenant.
     * Returns empty if the job exists but belongs to a different tenant.
     */
    @Query("""
            SELECT j FROM ImportJob j
            WHERE j.id = :jobId
              AND j.tenantId = :tenantId
            """)
    Optional<ImportJob> findByTenantAndId(
            @Param("tenantId") TenantId tenantId,
            @Param("jobId") UUID jobId
    );
}
