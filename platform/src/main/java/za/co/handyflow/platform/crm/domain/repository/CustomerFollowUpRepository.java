package za.co.handyflow.platform.crm.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.CustomerFollowUp;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerFollowUpRepository extends JpaRepository<CustomerFollowUp, UUID> {

    @Query("""
            SELECT f FROM CustomerFollowUp f
            WHERE f.tenantId   = :tenantId
              AND f.customerId = :customerId
            ORDER BY f.dueDate ASC
            """)
    List<CustomerFollowUp> findByCustomer(
            @Param("tenantId")   TenantId tenantId,
            @Param("customerId") UUID customerId
    );

    /** Tenant-scoped single lookup — same defence-in-depth convention as CustomerRepository/ImportJobRepository. */
    @Query("""
            SELECT f FROM CustomerFollowUp f
            WHERE f.id = :id
              AND f.tenantId = :tenantId
            """)
    Optional<CustomerFollowUp> findByIdAndTenant(
            @Param("tenantId") TenantId tenantId,
            @Param("id")       UUID id
    );

    /**
     * FIX: backs the reminder scheduler. dueDate <= :date catches both
     * "due today" and "overdue and never reminded" in one query —
     * deliberate, since a follow-up that was due yesterday and never
     * reminded still needs its one reminder, not a missed one just
     * because today isn't its exact due date.
     */
    @Query("""
            SELECT f FROM CustomerFollowUp f
            WHERE f.tenantId        = :tenantId
              AND f.completedAt     IS NULL
              AND f.dueDate         <= :date
              AND f.reminderSentAt  IS NULL
            """)
    List<CustomerFollowUp> findDueForReminder(
            @Param("tenantId") TenantId tenantId,
            @Param("date")     LocalDate date
    );

    /** Every unfinished task of the tenant, soonest first. Pass a Pageable to cap the size. */
    @Query("""
            SELECT f FROM CustomerFollowUp f
            WHERE f.tenantId = :tenantId
              AND f.completedAt IS NULL
            ORDER BY f.dueDate ASC, f.createdAt ASC
            """)
    List<CustomerFollowUp> findOpen(@Param("tenantId") TenantId tenantId, Pageable pageable);

    /** Tasks finished since the given moment, most recent first. */
    @Query("""
            SELECT f FROM CustomerFollowUp f
            WHERE f.tenantId = :tenantId
              AND f.completedAt IS NOT NULL
              AND f.completedAt >= :since
            ORDER BY f.completedAt DESC
            """)
    List<CustomerFollowUp> findCompletedSince(@Param("tenantId") TenantId tenantId,
                                              @Param("since") java.time.Instant since,
                                              Pageable pageable);
}
