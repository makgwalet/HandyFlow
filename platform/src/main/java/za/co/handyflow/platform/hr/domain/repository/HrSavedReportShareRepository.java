package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrSavedReportShare;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrSavedReportShareRepository extends JpaRepository<HrSavedReportShare, UUID> {

    @Query("SELECT s FROM HrSavedReportShare s WHERE s.reportId = :reportId")
    List<HrSavedReportShare> forReport(UUID reportId);

    @Query("SELECT s FROM HrSavedReportShare s WHERE s.reportId IN :reportIds")
    List<HrSavedReportShare> forReports(Collection<UUID> reportIds);

    @Query("SELECT s FROM HrSavedReportShare s WHERE s.tenantId = :#{#tenantId.value} AND s.userId = :userId")
    List<HrSavedReportShare> forUser(TenantId tenantId, UUID userId);

    @Query("SELECT s FROM HrSavedReportShare s WHERE s.tenantId = :#{#tenantId.value} AND s.userId = :userId AND s.reportId = :reportId")
    Optional<HrSavedReportShare> findForUser(TenantId tenantId, UUID userId, UUID reportId);
}
