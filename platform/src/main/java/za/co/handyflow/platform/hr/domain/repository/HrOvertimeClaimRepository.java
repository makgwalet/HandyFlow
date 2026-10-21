package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrOvertimeClaim;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrOvertimeClaimRepository extends JpaRepository<HrOvertimeClaim, UUID> {

    @Query("SELECT c FROM HrOvertimeClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<HrOvertimeClaim> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT c FROM HrOvertimeClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.employeeId = :employeeId AND c.workDate = :date")
    Optional<HrOvertimeClaim> forDay(TenantId tenantId, UUID employeeId, LocalDate date);

    @Query("""
            SELECT c FROM HrOvertimeClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.employeeId = :employeeId
              AND c.workDate BETWEEN :from AND :to""")
    List<HrOvertimeClaim> forEmployee(TenantId tenantId, UUID employeeId, LocalDate from, LocalDate to);

    @Query("SELECT c FROM HrOvertimeClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.status IN :statuses ORDER BY c.workDate DESC")
    List<HrOvertimeClaim> byStatus(TenantId tenantId, Collection<String> statuses);

    /** Approved claims a pay run can pay: worked up to the end of the period and not taken by another run. */
    @Query("""
            SELECT c FROM HrOvertimeClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'APPROVED'
              AND c.workDate <= :periodEnd AND (c.payRunId IS NULL OR c.payRunId = :payRunId)""")
    List<HrOvertimeClaim> payableIn(TenantId tenantId, LocalDate periodEnd, UUID payRunId);

    @Query("SELECT c FROM HrOvertimeClaim c WHERE c.tenantId = :#{#tenantId.value} AND c.payRunId = :payRunId")
    List<HrOvertimeClaim> takenBy(TenantId tenantId, UUID payRunId);
}
