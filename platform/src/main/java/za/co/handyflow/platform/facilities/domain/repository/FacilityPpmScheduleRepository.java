package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityPpmSchedule;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityPpmScheduleRepository extends JpaRepository<FacilityPpmSchedule, UUID> {

    @Query("SELECT s FROM FacilityPpmSchedule s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id AND s.deletedAt IS NULL")
    Optional<FacilityPpmSchedule> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT s FROM FacilityPpmSchedule s WHERE s.tenantId = :#{#tenantId.value} AND s.deletedAt IS NULL")
    Page<FacilityPpmSchedule> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);

    @Query("SELECT s FROM FacilityPpmSchedule s WHERE s.tenantId = :#{#tenantId.value} AND s.assetId = :assetId AND s.deletedAt IS NULL")
    List<FacilityPpmSchedule> findByAsset(@Param("tenantId") TenantId tenantId, @Param("assetId") UUID assetId);

    /** Cross-tenant sweep for the notification/work-order-generation scheduler: every active schedule due on or before today. */
    @Query("SELECT s FROM FacilityPpmSchedule s WHERE s.active = true AND s.deletedAt IS NULL AND s.nextDueDate <= :asOfDate")
    List<FacilityPpmSchedule> findAllDueAcrossTenants(@Param("asOfDate") LocalDate asOfDate);
}
