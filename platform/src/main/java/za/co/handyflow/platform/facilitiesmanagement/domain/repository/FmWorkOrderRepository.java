package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmWorkOrderRepository extends JpaRepository<FmWorkOrder, UUID> {

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.id = :id AND w.deletedAt IS NULL")
    Optional<FmWorkOrder> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.deletedAt IS NULL " +
           "AND (:status IS NULL OR w.status = :status) ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findAll(@Param("tenantId") TenantId tenantId, @Param("status") String status, Pageable pageable);

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId AND w.deletedAt IS NULL " +
           "AND (:status IS NULL OR w.status = :status) ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                        @Param("status") String status, Pageable pageable);

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.siteId = :siteId AND w.deletedAt IS NULL ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findBySite(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId, Pageable pageable);

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.assetId = :assetId AND w.deletedAt IS NULL ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findByAsset(@Param("tenantId") TenantId tenantId, @Param("assetId") UUID assetId, Pageable pageable);

    @Query("SELECT COUNT(w) > 0 FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.ppmScheduleId = :ppmScheduleId " +
           "AND w.status NOT IN ('COMPLETED','CANCELLED') AND w.deletedAt IS NULL")
    boolean existsOpenForPpmSchedule(@Param("tenantId") TenantId tenantId, @Param("ppmScheduleId") UUID ppmScheduleId);

    /** Cross-tenant sweep for the daily overdue-work-order alert. */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.status NOT IN ('COMPLETED','CANCELLED') AND w.deletedAt IS NULL " +
           "AND w.scheduledDate IS NOT NULL AND w.scheduledDate < :today")
    List<FmWorkOrder> findOverdueAcrossTenants(@Param("today") LocalDate today);

    /**
     * Billing candidates for {@code FmBillingService}'s time-and-materials
     * branch: COMPLETED, not yet invoiced, a real positive cost recorded,
     * and completed within the billing period (inclusive both ends —
     * completedAt is an Instant, so the period bounds are widened to the
     * start of periodStart and the start of the day AFTER periodEnd).
     */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId " +
           "AND w.status = 'COMPLETED' AND w.invoiced = false AND w.cost IS NOT NULL AND w.cost > 0 " +
           "AND w.completedAt >= :periodStart AND w.completedAt < :periodEndExclusive AND w.deletedAt IS NULL " +
           "ORDER BY w.completedAt ASC")
    List<FmWorkOrder> findBillableForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                             @Param("periodStart") Instant periodStart,
                                             @Param("periodEndExclusive") Instant periodEndExclusive);
}
