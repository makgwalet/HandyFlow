package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrSavedReport;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrSavedReportRepository extends JpaRepository<HrSavedReport, UUID> {

    @Query("SELECT r FROM HrSavedReport r WHERE r.tenantId = :#{#tenantId.value} AND r.ownerId = :ownerId ORDER BY r.createdAt")
    List<HrSavedReport> forOwner(TenantId tenantId, UUID ownerId);

    @Query("SELECT r FROM HrSavedReport r WHERE r.tenantId = :#{#tenantId.value} AND r.ownerId = :ownerId AND r.id = :id")
    Optional<HrSavedReport> findForOwner(TenantId tenantId, UUID ownerId, UUID id);

    @Query("SELECT COUNT(r) FROM HrSavedReport r WHERE r.tenantId = :#{#tenantId.value} AND r.ownerId = :ownerId")
    long countForOwner(TenantId tenantId, UUID ownerId);

    /** Every company's reports that have a reminder; the scheduler decides which are due today. */
    @Query("SELECT r FROM HrSavedReport r WHERE r.schedule <> 'NONE'")
    List<HrSavedReport> scheduled();
}
