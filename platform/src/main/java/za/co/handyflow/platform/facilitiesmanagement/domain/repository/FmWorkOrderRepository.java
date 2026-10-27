package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmWorkOrderRepository extends JpaRepository<FmWorkOrder, UUID> {

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.id = :id AND w.deletedAt IS NULL")
    Optional<FmWorkOrder> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.deletedAt IS NULL " +
            "AND (:status IS NULL OR w.status = :status) ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findAll(@Param("tenantId") TenantId tenantId, @Param("status") String status, Pageable pageable);

    /**
     * Scope-aware list across clients: {@code allClients=false} limits to {@code clientIds};
     * {@code allJobs=false} limits to jobs assigned to {@code technicianIds}. Collections are never empty
     * (policy supplies a placeholder).
     */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.deletedAt IS NULL " +
            "AND (:status IS NULL OR w.status = :status) " +
            "AND (:allClients = true OR w.clientId IN :clientIds) " +
            "AND (:allJobs = true OR w.technicianId IN :technicianIds) ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findAllInScope(@Param("tenantId") TenantId tenantId, @Param("status") String status,
                                     @Param("allClients") boolean allClients, @Param("clientIds") Collection<UUID> clientIds,
                                     @Param("allJobs") boolean allJobs, @Param("technicianIds") Collection<UUID> technicianIds,
                                     Pageable pageable);

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId AND w.deletedAt IS NULL " +
            "AND (:status IS NULL OR w.status = :status) ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                       @Param("status") String status, Pageable pageable);

    /** One client's work orders, additionally narrowed to assigned jobs for field technicians. */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId AND w.deletedAt IS NULL " +
            "AND (:status IS NULL OR w.status = :status) " +
            "AND (:allJobs = true OR w.technicianId IN :technicianIds) ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findAllForClientInScope(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                              @Param("status") String status, @Param("allJobs") boolean allJobs,
                                              @Param("technicianIds") Collection<UUID> technicianIds, Pageable pageable);

    /**
     * What a PORTAL user may list: never internal triage states (REQUESTED, REJECTED). {@code statuses}
     * must be non-empty; the service translates the client's filter (PENDING_VERIFICATION is presented to
     * clients as IN_PROGRESS).
     */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId AND w.deletedAt IS NULL " +
            "AND w.status IN :statuses AND (w.status NOT IN ('REQUESTED','REJECTED') OR w.requestedByPortalUserId IS NOT NULL) " +
            "ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findAllForClientPortal(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                             @Param("statuses") Collection<String> statuses, Pageable pageable);

    /** Requests a client raised through the portal since {@code since} — intake throttle. */
    @Query("SELECT COUNT(w) FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId " +
            "AND w.requestedByPortalUserId IS NOT NULL AND w.createdAt >= :since")
    long countPortalRequestsSince(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, @Param("since") Instant since);

    @Query("SELECT DISTINCT w.siteId FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} " +
            "AND w.technicianId IN :technicianIds AND w.deletedAt IS NULL")
    List<UUID> findSiteIdsForTechnicians(@Param("tenantId") TenantId tenantId, @Param("technicianIds") Collection<UUID> technicianIds);

    /** Ids (newest first) of open, non-PPM work orders on an asset — used to flag a possible duplicate on intake. */
    @Query("SELECT w.id FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.assetId = :assetId " +
            "AND w.category <> 'PPM' AND w.status NOT IN ('COMPLETED','CANCELLED','REJECTED') AND w.deletedAt IS NULL " +
            "ORDER BY w.createdAt DESC")
    List<UUID> findOpenReactiveIdsForAsset(@Param("tenantId") TenantId tenantId, @Param("assetId") UUID assetId, Pageable pageable);

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.siteId = :siteId AND w.deletedAt IS NULL ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findBySite(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId, Pageable pageable);

    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.assetId = :assetId AND w.deletedAt IS NULL ORDER BY w.createdAt DESC")
    Page<FmWorkOrder> findByAsset(@Param("tenantId") TenantId tenantId, @Param("assetId") UUID assetId, Pageable pageable);

    /** A PPM schedule still has an open work order until that work order is COMPLETED, CANCELLED or REJECTED (PENDING_VERIFICATION counts as open). */
    @Query("SELECT COUNT(w) > 0 FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.ppmScheduleId = :ppmScheduleId " +
            "AND w.status NOT IN ('COMPLETED','CANCELLED','REJECTED') AND w.deletedAt IS NULL")
    boolean existsOpenForPpmSchedule(@Param("tenantId") TenantId tenantId, @Param("ppmScheduleId") UUID ppmScheduleId);

    /** Cross-tenant sweep for the daily overdue alert. Work already awaiting a verifier is not overdue. */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.status NOT IN ('COMPLETED','CANCELLED','REJECTED','PENDING_VERIFICATION') " +
            "AND w.deletedAt IS NULL AND w.scheduledDate IS NOT NULL AND w.scheduledDate < :today")
    List<FmWorkOrder> findOverdueAcrossTenants(@Param("today") LocalDate today);

    /** Cross-tenant: open work orders whose response target has not been met or breached yet (SLA monitor). */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.responseDueAt IS NOT NULL AND w.respondedAt IS NULL AND w.responseBreachedAt IS NULL " +
            "AND w.status NOT IN ('COMPLETED','CANCELLED','REJECTED') AND w.deletedAt IS NULL")
    List<FmWorkOrder> findOpenForResponseSla();

    /** Cross-tenant: open, running (not paused, not awaiting verification) work orders with an unbreached resolution target. */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.resolutionDueAt IS NOT NULL AND w.completedAt IS NULL AND w.resolutionBreachedAt IS NULL " +
            "AND w.slaPausedAt IS NULL AND w.status NOT IN ('COMPLETED','CANCELLED','REJECTED','PENDING_VERIFICATION') AND w.deletedAt IS NULL")
    List<FmWorkOrder> findOpenForResolutionSla();

    /** A client's work orders created in the period that carry an SLA (cancelled/rejected excluded) — for the SLA report. */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId AND w.deletedAt IS NULL " +
            "AND (w.responseDueAt IS NOT NULL OR w.resolutionDueAt IS NOT NULL) AND w.status NOT IN ('CANCELLED','REJECTED') " +
            "AND w.createdAt >= :from AND w.createdAt < :toExclusive")
    List<FmWorkOrder> findWithSlaForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                           @Param("from") Instant from, @Param("toExclusive") Instant toExclusive);

    /** Work orders billed by a given invoice — released again if that DRAFT invoice is cancelled. */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.invoiceId = :invoiceId AND w.deletedAt IS NULL")
    List<FmWorkOrder> findByInvoiceId(@Param("tenantId") TenantId tenantId, @Param("invoiceId") UUID invoiceId);

    /** Internal cost of work COMPLETED in the period (billed or not). Null when there is none. */
    @Query("SELECT SUM(w.internalCost) FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId " +
            "AND w.status = 'COMPLETED' AND w.completedAt >= :periodStart AND w.completedAt < :periodEndExclusive AND w.deletedAt IS NULL")
    BigDecimal sumInternalCostForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                        @Param("periodStart") Instant periodStart,
                                        @Param("periodEndExclusive") Instant periodEndExclusive);

    @Query("SELECT COUNT(w) FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId " +
            "AND w.status = 'COMPLETED' AND w.completedAt >= :periodStart AND w.completedAt < :periodEndExclusive AND w.deletedAt IS NULL")
    long countCompletedForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                 @Param("periodStart") Instant periodStart, @Param("periodEndExclusive") Instant periodEndExclusive);

    /** Charge on completed work that has not been invoiced yet (all time). Null when there is none. */
    @Query("SELECT SUM(w.cost) FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId " +
            "AND w.status = 'COMPLETED' AND w.invoiced = false AND w.cost IS NOT NULL AND w.cost > 0 " +
            "AND (w.coverage IS NULL OR w.coverage <> 'INCLUDED') AND w.deletedAt IS NULL")
    BigDecimal sumUnbilledChargeForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /**
     * Billing candidates for {@code FmBillingService}'s time-and-materials branch: COMPLETED (so verified,
     * where verification applies — work awaiting verification is never invoiced), not yet invoiced, a real
     * positive cost recorded, and completed within the billing period (inclusive both ends — completedAt is
     * an Instant, so the period bounds are widened to the start of periodStart and the start of the day
     * AFTER periodEnd).
     */
    @Query("SELECT w FROM FmWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.clientId = :clientId " +
            "AND w.status = 'COMPLETED' AND w.invoiced = false AND w.cost IS NOT NULL AND w.cost > 0 " +
            "AND (w.coverage IS NULL OR w.coverage <> 'INCLUDED') " +
            "AND w.completedAt >= :periodStart AND w.completedAt < :periodEndExclusive AND w.deletedAt IS NULL " +
            "ORDER BY w.completedAt ASC")
    List<FmWorkOrder> findBillableForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                            @Param("periodStart") Instant periodStart,
                                            @Param("periodEndExclusive") Instant periodEndExclusive);
}