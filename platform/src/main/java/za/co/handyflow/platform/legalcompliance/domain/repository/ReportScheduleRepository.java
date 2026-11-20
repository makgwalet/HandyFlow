package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ReportSchedule;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Repository("legalComplianceReportScheduleRepository")
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {

    @Query("SELECT x FROM LcReportSchedule x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<ReportSchedule> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM LcReportSchedule x WHERE x.tenantId = :tenantId ORDER BY x.name")
    List<ReportSchedule> findAllForTenant(TenantId tenantId);

    /** Cross-tenant — the daily sweep. */
    @Query("SELECT x.id FROM LcReportSchedule x WHERE x.active = TRUE AND x.nextRunOn <= :today")
    List<UUID> findDueIdsAcrossTenants(LocalDate today);
}
