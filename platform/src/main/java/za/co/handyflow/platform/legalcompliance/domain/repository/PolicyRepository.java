package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    @Query("SELECT p FROM Policy p WHERE p.tenantId = :tenantId AND p.id = :id")
    Optional<Policy> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT p FROM Policy p WHERE p.tenantId = :tenantId AND (:includeRetired = TRUE OR p.status = za.co.handyflow.platform.legalcompliance.domain.model.PolicyStatus.ACTIVE)
        ORDER BY p.reference
        """)
    List<Policy> search(TenantId tenantId, boolean includeRetired);

    /** Cross-tenant — daily sweep: active policies whose review is overdue or falls on or before :soon. */
    @Query("SELECT p FROM Policy p WHERE p.status = za.co.handyflow.platform.legalcompliance.domain.model.PolicyStatus.ACTIVE AND p.nextReviewDue IS NOT NULL AND p.nextReviewDue <= :soon")
    List<Policy> findReviewDueAcrossTenants(LocalDate soon);

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}
