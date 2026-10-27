package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityInspectionSchedule;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityInspectionScheduleRepository extends JpaRepository<FacilityInspectionSchedule, UUID> {

    @Query("SELECT s FROM FacilityInspectionSchedule s WHERE s.tenantId.value = :#{#tenantId.value} AND s.id = :id AND s.deletedAt IS NULL")
    Optional<FacilityInspectionSchedule> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    /** {@code siteIds} must never be empty (the access policy guarantees a placeholder). */
    @Query("SELECT s FROM FacilityInspectionSchedule s WHERE s.tenantId.value = :#{#tenantId.value} AND s.deletedAt IS NULL " +
            "AND (:siteId IS NULL OR s.siteId = :siteId) AND (:allSites = true OR s.siteId IN :siteIds)")
    Page<FacilityInspectionSchedule> findAllInScope(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId,
                                                    @Param("allSites") boolean allSites,
                                                    @Param("siteIds") Collection<UUID> siteIds, Pageable pageable);

    /** The live schedule for this checklist against one asset. */
    @Query("SELECT s FROM FacilityInspectionSchedule s WHERE s.tenantId.value = :#{#tenantId.value} AND s.deletedAt IS NULL " +
            "AND s.templateId = :templateId AND s.siteId = :siteId AND s.assetId = :assetId")
    Optional<FacilityInspectionSchedule> findForAsset(@Param("tenantId") TenantId tenantId, @Param("templateId") UUID templateId,
                                                      @Param("siteId") UUID siteId, @Param("assetId") UUID assetId);

    /** The live site-wide schedule (no asset) for this checklist. */
    @Query("SELECT s FROM FacilityInspectionSchedule s WHERE s.tenantId.value = :#{#tenantId.value} AND s.deletedAt IS NULL " +
            "AND s.templateId = :templateId AND s.siteId = :siteId AND s.assetId IS NULL")
    Optional<FacilityInspectionSchedule> findForSite(@Param("tenantId") TenantId tenantId, @Param("templateId") UUID templateId,
                                                     @Param("siteId") UUID siteId);

    /** Active schedules in scope that are already past due (action centre). */
    @Query("SELECT s FROM FacilityInspectionSchedule s WHERE s.tenantId.value = :#{#tenantId.value} AND s.deletedAt IS NULL " +
            "AND s.active = true AND s.nextDueDate < :today AND (:allSites = true OR s.siteId IN :siteIds)")
    List<FacilityInspectionSchedule> findOverdueInScope(@Param("tenantId") TenantId tenantId, @Param("today") LocalDate today,
                                                        @Param("allSites") boolean allSites, @Param("siteIds") Collection<UUID> siteIds);

    /** Cross-tenant daily sweep: active schedules due on or before {@code horizon}, whose checklist is still switched on. */
    @Query("SELECT s FROM FacilityInspectionSchedule s WHERE s.active = true AND s.deletedAt IS NULL AND s.nextDueDate <= :horizon " +
            "AND s.templateId IN (SELECT t.id FROM FacilityInspectionTemplate t WHERE t.active = true)")
    List<FacilityInspectionSchedule> findDueAcrossTenants(@Param("horizon") LocalDate horizon);
}
