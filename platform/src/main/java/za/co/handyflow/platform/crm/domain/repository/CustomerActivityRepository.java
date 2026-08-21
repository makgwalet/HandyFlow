package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.CustomerActivity;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * CustomerActivityRepository — read-only access to the activity timeline.
 *
 * WHY separate repository?
 * CustomerActivity is a separate aggregate in JPA terms.
 * Even though it's owned by Customer, we need to query it
 * independently for the timeline endpoint without loading the
 * entire Customer aggregate.
 *
 * We deliberately expose NO save/delete methods here.
 * Activities are written exclusively through the Customer aggregate
 * (Customer.addNote(), Customer.update(), etc.) and cascade-saved
 * when the Customer is saved.
 *
 * This makes it impossible to create an orphaned activity without
 * going through a Customer domain method.
 */
public interface CustomerActivityRepository extends JpaRepository<CustomerActivity, UUID> {

    @Query("""
            SELECT a FROM CustomerActivity a
            WHERE a.customer.id = :customerId
              AND a.tenantId = :#{#tenantId.value}
            ORDER BY a.createdAt DESC
            """)
    Page<CustomerActivity> findByCustomer(@Param("tenantId") TenantId tenantId,
                                          @Param("customerId") UUID customerId,
                                          Pageable pageable);

    @Query("""
            SELECT a FROM CustomerActivity a
            WHERE a.customer.id = :customerId
              AND a.tenantId = :#{#tenantId.value}
            ORDER BY a.createdAt ASC
            """)
    List<CustomerActivity> findAllByCustomer(
            @Param("tenantId") TenantId tenantId,
            @Param("customerId") UUID customerId
    );
    //
    // WHY ASC order for POPIA?
    // The processing history in a POPIA export should be chronological
    // (oldest event first) so it reads like a story — not reverse-chron
    // like the UI timeline.

    /**
     * FIX: backlog 4.3 — backs the funnel/conversion-rate report.
     * Tenant-wide (not customer-scoped, unlike the two methods above) —
     * the report needs every lead's stage history at once to compute
     * aggregate conversion rates, not one customer's timeline. Ordered by
     * customer then chronologically within each customer, matching
     * exactly the grouping CrmReportingService needs to walk each lead's
     * stage journey in order without a second in-memory sort.
     */
    @Query("""
            SELECT a FROM CustomerActivity a
            WHERE a.tenantId = :#{#tenantId.value}
              AND a.activityType = za.co.handyflow.platform.crm.domain.model.ActivityType.STAGE_CHANGED
            ORDER BY a.customer.id ASC, a.createdAt ASC
            """)
    List<CustomerActivity> findStageChangesByTenant(@Param("tenantId") TenantId tenantId);
}