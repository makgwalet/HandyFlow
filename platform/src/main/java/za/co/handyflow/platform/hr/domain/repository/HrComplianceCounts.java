package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-tenant COUNTS for the Legal Compliance cross-module view, kept apart from HrEmployeeRepository so that file is untouched. Current employees only (not terminated, not deleted).
 * Counts people, never names or document numbers.
 */
public interface HrComplianceCounts extends Repository<HrEmployee, UUID> {

    String LIVE = "e.tenantId = :#{#tenantId.value} AND e.deletedAt IS NULL AND e.status <> 'TERMINATED'";

    @Query("SELECT COUNT(e) FROM HrEmployee e WHERE " + LIVE + " AND e.workPermitExpiry < :today")
    long workPermitsExpired(TenantId tenantId, LocalDate today);

    @Query("SELECT COUNT(e) FROM HrEmployee e WHERE " + LIVE + " AND e.workPermitExpiry >= :today AND e.workPermitExpiry <= :limit")
    long workPermitsExpiring(TenantId tenantId, LocalDate today, LocalDate limit);

    @Query("SELECT COUNT(e) FROM HrEmployee e WHERE " + LIVE + " AND e.passportExpiry < :today")
    long passportsExpired(TenantId tenantId, LocalDate today);

    @Query("SELECT COUNT(e) FROM HrEmployee e WHERE " + LIVE + " AND e.passportExpiry >= :today AND e.passportExpiry <= :limit")
    long passportsExpiring(TenantId tenantId, LocalDate today, LocalDate limit);
}
