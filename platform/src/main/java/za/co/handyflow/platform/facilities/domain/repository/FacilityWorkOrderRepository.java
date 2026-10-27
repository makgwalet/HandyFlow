package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityWorkOrderRepository extends JpaRepository<FacilityWorkOrder, UUID> {

    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.id = :id AND w.deletedAt IS NULL")
    Optional<FacilityWorkOrder> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.deletedAt IS NULL " +
            "AND (:status IS NULL OR w.status = :status) ORDER BY w.createdAt DESC")
    Page<FacilityWorkOrder> findAll(@Param("tenantId") TenantId tenantId, @Param("status") String status, Pageable pageable);

    /**
     * Scope-aware list: {@code allSites=false} limits to {@code siteIds}; {@code allJobs=false} limits to jobs
     * assigned to {@code technicianIds}; {@code allRequesters=false} limits to requests raised by
     * {@code requesterUserId}. Id collections and the requester id are never null/empty (policy supplies placeholders).
     */
    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.deletedAt IS NULL " +
            "AND (:status IS NULL OR w.status = :status) " +
            "AND (:allSites = true OR w.siteId IN :siteIds) " +
            "AND (:allJobs = true OR w.technicianId IN :technicianIds) " +
            "AND (:allRequesters = true OR w.requestedByUserId = :requesterUserId) ORDER BY w.createdAt DESC")
    Page<FacilityWorkOrder> findAllInScope(@Param("tenantId") TenantId tenantId, @Param("status") String status,
                                           @Param("allSites") boolean allSites, @Param("siteIds") Collection<UUID> siteIds,
                                           @Param("allJobs") boolean allJobs, @Param("technicianIds") Collection<UUID> technicianIds,
                                           @Param("allRequesters") boolean allRequesters, @Param("requesterUserId") UUID requesterUserId,
                                           Pageable pageable);

    @Query("SELECT DISTINCT w.siteId FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} " +
            "AND w.technicianId IN :technicianIds AND w.deletedAt IS NULL")
    List<UUID> findSiteIdsForTechnicians(@Param("tenantId") TenantId tenantId, @Param("technicianIds") Collection<UUID> technicianIds);

    /** Ids (newest first) of open, non-PPM work orders on an asset — used to flag a possible duplicate on intake. */
    @Query("SELECT w.id FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.assetId = :assetId " +
            "AND w.category <> 'PPM' AND w.status NOT IN ('COMPLETED','CANCELLED','REJECTED') AND w.deletedAt IS NULL " +
            "ORDER BY w.createdAt DESC")
    List<UUID> findOpenReactiveIdsForAsset(@Param("tenantId") TenantId tenantId, @Param("assetId") UUID assetId, Pageable pageable);

    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.siteId = :siteId AND w.deletedAt IS NULL ORDER BY w.createdAt DESC")
    Page<FacilityWorkOrder> findBySite(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId, Pageable pageable);

    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.assetId = :assetId AND w.deletedAt IS NULL ORDER BY w.createdAt DESC")
    Page<FacilityWorkOrder> findByAsset(@Param("tenantId") TenantId tenantId, @Param("assetId") UUID assetId, Pageable pageable);

    /** A PPM schedule still has an open work order until that work order is COMPLETED, CANCELLED or REJECTED (PENDING_VERIFICATION counts as open). */
    @Query("SELECT COUNT(w) > 0 FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.ppmScheduleId = :ppmScheduleId " +
            "AND w.status NOT IN ('COMPLETED','CANCELLED','REJECTED') AND w.deletedAt IS NULL")
    boolean existsOpenForPpmSchedule(@Param("tenantId") TenantId tenantId, @Param("ppmScheduleId") UUID ppmScheduleId);

    /** Cross-tenant sweep for the daily overdue alert. Work already awaiting a verifier is not overdue. */
    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.status NOT IN ('COMPLETED','CANCELLED','REJECTED','PENDING_VERIFICATION') " +
            "AND w.deletedAt IS NULL AND w.scheduledDate IS NOT NULL AND w.scheduledDate < :today")
    List<FacilityWorkOrder> findOverdueAcrossTenants(@Param("today") LocalDate today);


    // ── Reporting (same scope parameters as findAllInScope; collections never empty) ──

    /** Everything not yet finished within the caller's scope (dashboard). */
    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.deletedAt IS NULL " +
            "AND w.status NOT IN ('COMPLETED','CANCELLED','REJECTED') " +
            "AND (:allSites = true OR w.siteId IN :siteIds) " +
            "AND (:allJobs = true OR w.technicianId IN :technicianIds) " +
            "AND (:allRequesters = true OR w.requestedByUserId = :requesterUserId)")
    List<FacilityWorkOrder> findOpenInScope(@Param("tenantId") TenantId tenantId,
            @Param("allSites") boolean allSites, @Param("siteIds") Collection<UUID> siteIds,
            @Param("allJobs") boolean allJobs, @Param("technicianIds") Collection<UUID> technicianIds,
            @Param("allRequesters") boolean allRequesters, @Param("requesterUserId") UUID requesterUserId);

    /** Work orders created in the period (REQUESTED/REJECTED triage states excluded) — vendor and asset reports. */
    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.deletedAt IS NULL " +
            "AND w.status NOT IN ('REQUESTED','REJECTED') AND w.createdAt >= :from AND w.createdAt < :toExclusive " +
            "AND (:allSites = true OR w.siteId IN :siteIds) " +
            "AND (:allJobs = true OR w.technicianId IN :technicianIds) " +
            "AND (:allRequesters = true OR w.requestedByUserId = :requesterUserId)")
    List<FacilityWorkOrder> findCreatedBetweenInScope(@Param("tenantId") TenantId tenantId,
            @Param("from") Instant from, @Param("toExclusive") Instant toExclusive,
            @Param("allSites") boolean allSites, @Param("siteIds") Collection<UUID> siteIds,
            @Param("allJobs") boolean allJobs, @Param("technicianIds") Collection<UUID> technicianIds,
            @Param("allRequesters") boolean allRequesters, @Param("requesterUserId") UUID requesterUserId);

    /** Work completed in the period within scope (dashboard throughput). */
    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.deletedAt IS NULL " +
            "AND w.status = 'COMPLETED' AND w.completedAt >= :from AND w.completedAt < :toExclusive " +
            "AND (:allSites = true OR w.siteId IN :siteIds) " +
            "AND (:allJobs = true OR w.technicianId IN :technicianIds) " +
            "AND (:allRequesters = true OR w.requestedByUserId = :requesterUserId)")
    List<FacilityWorkOrder> findCompletedBetweenInScope(@Param("tenantId") TenantId tenantId,
            @Param("from") Instant from, @Param("toExclusive") Instant toExclusive,
            @Param("allSites") boolean allSites, @Param("siteIds") Collection<UUID> siteIds,
            @Param("allJobs") boolean allJobs, @Param("technicianIds") Collection<UUID> technicianIds,
            @Param("allRequesters") boolean allRequesters, @Param("requesterUserId") UUID requesterUserId);
}
