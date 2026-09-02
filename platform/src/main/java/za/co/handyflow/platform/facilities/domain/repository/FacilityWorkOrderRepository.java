package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityWorkOrderRepository extends JpaRepository<FacilityWorkOrder, UUID> {

    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.id = :id AND w.deletedAt IS NULL")
    Optional<FacilityWorkOrder> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.deletedAt IS NULL " +
           "AND (:status IS NULL OR w.status = :status) ORDER BY w.createdAt DESC")
    Page<FacilityWorkOrder> findAll(@Param("tenantId") TenantId tenantId, @Param("status") String status, Pageable pageable);

    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.siteId = :siteId AND w.deletedAt IS NULL ORDER BY w.createdAt DESC")
    Page<FacilityWorkOrder> findBySite(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId, Pageable pageable);

    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.assetId = :assetId AND w.deletedAt IS NULL ORDER BY w.createdAt DESC")
    Page<FacilityWorkOrder> findByAsset(@Param("tenantId") TenantId tenantId, @Param("assetId") UUID assetId, Pageable pageable);

    @Query("SELECT COUNT(w) > 0 FROM FacilityWorkOrder w WHERE w.tenantId = :#{#tenantId.value} AND w.ppmScheduleId = :ppmScheduleId " +
           "AND w.status NOT IN ('COMPLETED','CANCELLED') AND w.deletedAt IS NULL")
    boolean existsOpenForPpmSchedule(@Param("tenantId") TenantId tenantId, @Param("ppmScheduleId") UUID ppmScheduleId);

    /** Cross-tenant sweep for the daily overdue-work-order alert. */
    @Query("SELECT w FROM FacilityWorkOrder w WHERE w.status NOT IN ('COMPLETED','CANCELLED') AND w.deletedAt IS NULL " +
           "AND w.scheduledDate IS NOT NULL AND w.scheduledDate < :today")
    List<FacilityWorkOrder> findOverdueAcrossTenants(@Param("today") LocalDate today);
}
