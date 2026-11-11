// fuel/domain/repository/FuelDeliveryRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelDeliveryRepository extends JpaRepository<FuelDelivery, UUID> {

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL ORDER BY d.scheduledAt DESC")
    Page<FuelDelivery> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.status = :status AND d.deletedAt IS NULL ORDER BY d.scheduledAt ASC")
    Page<FuelDelivery> findByStatus(TenantId tenantId, String status, Pageable pageable);

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.id = :id AND d.deletedAt IS NULL")
    Optional<FuelDelivery> findActiveById(TenantId tenantId, UUID id);

    @jakarta.persistence.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.id = :id AND d.deletedAt IS NULL")
    Optional<FuelDelivery> findActiveByIdForUpdate(TenantId tenantId, UUID id);

    // FIX (fuel cost/margin engine): backs the margin report, same
    // pattern as FuelDispatchRepository.findByTenantAndDispatchedAtBetween
    // (that one already existed for the usage report; this one didn't
    // exist at all for deliveries). Filters on deliveredAt, not
    // scheduledAt — the margin report is about completed transactions
    // within the period, not ones merely scheduled in it.
    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.status = 'DELIVERED' AND d.deletedAt IS NULL AND d.deliveredAt BETWEEN :from AND :to ORDER BY d.deliveredAt ASC")
    List<FuelDelivery> findDeliveredBetween(TenantId tenantId, Instant from, Instant to);

    // ── Notification scheduler (cross-tenant sweeps) ────────────────────────
    // "Not yet delivered" is expressed as status IN ('SCHEDULED','IN_TRANSIT') rather
    // than != 'DELIVERED', so a CANCELLED delivery is correctly excluded from both
    // sweeps without needing its own branch.

    /**
     * Not-yet-delivered deliveries whose scheduled time falls within the
     * reminder window and haven't been reminded yet.
     */
    @Query("""
        SELECT d FROM FuelDelivery d
        WHERE d.status IN ('SCHEDULED','IN_TRANSIT') AND d.deletedAt IS NULL
        AND d.scheduledAt BETWEEN :now AND :windowEnd
        AND d.reminderSentAt IS NULL
        """)
    List<FuelDelivery> findUpcomingNeedingReminder(Instant now, Instant windowEnd);

    /**
     * Not-yet-delivered deliveries whose scheduled time has already passed and
     * haven't been alerted as overdue yet.
     */
    @Query("""
        SELECT d FROM FuelDelivery d
        WHERE d.status IN ('SCHEDULED','IN_TRANSIT') AND d.deletedAt IS NULL
        AND d.scheduledAt < :now
        AND d.overdueAlertSentAt IS NULL
        """)
    List<FuelDelivery> findOverdueNeedingAlert(Instant now);

    // ── Stage 2: orders and invoicing ────────────────────────────────────────

    @Query("SELECT COALESCE(SUM(d.litresDelivered), 0) FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.orderId = :orderId AND d.status = 'DELIVERED' AND d.deletedAt IS NULL")
    java.math.BigDecimal sumDeliveredForOrder(TenantId tenantId, UUID orderId);

    /** Litres of scheduled or in-transit loads that have not yet been delivered against the order. */
    @Query("SELECT COALESCE(SUM(d.litresOrdered), 0) FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.orderId = :orderId AND d.status IN ('SCHEDULED','IN_TRANSIT') AND d.deletedAt IS NULL")
    java.math.BigDecimal sumPendingForOrder(TenantId tenantId, UUID orderId);

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.orderId = :orderId AND d.deletedAt IS NULL ORDER BY d.scheduledAt")
    List<FuelDelivery> findByOrder(TenantId tenantId, UUID orderId);

    /** Delivered fuel with no invoice (or a failed invoice attempt): the "delivered, not invoiced" queue. */
    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.status = 'DELIVERED' AND d.invoiceState IN ('NONE','FAILED') AND d.deletedAt IS NULL ORDER BY d.deliveredAt")
    List<FuelDelivery> findNotInvoiced(TenantId tenantId);

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.id IN :ids AND d.deletedAt IS NULL")
    List<FuelDelivery> findAllByIds(TenantId tenantId, java.util.Collection<UUID> ids);

    /** Uninvoiced delivered value for a customer (credit exposure). */
    @Query("SELECT COALESCE(SUM(d.totalAmount), 0) FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.customerId = :customerId AND d.status = 'DELIVERED' AND d.invoiceState <> 'INVOICED' AND d.deletedAt IS NULL")
    java.math.BigDecimal sumUninvoicedValue(TenantId tenantId, UUID customerId);

    /**
     * Atomically claims deliveries for invoicing. Returns how many were claimed; the caller compares with the
     * number asked for. A claim left over from a crashed attempt (older than {@code stale}) can be taken again.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FuelDelivery d SET d.invoiceState = 'INVOICING', d.invoiceClaimedAt = :now, d.invoiceError = NULL " +
           "WHERE d.tenantId = :tenantId AND d.id IN :ids AND d.status = 'DELIVERED' AND d.deletedAt IS NULL " +
           "AND (d.invoiceState IN ('NONE','FAILED') OR (d.invoiceState = 'INVOICING' AND d.invoiceClaimedAt < :stale))")
    int claimForInvoicing(TenantId tenantId, java.util.Collection<UUID> ids, Instant now, Instant stale);
}
