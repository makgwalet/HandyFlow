package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrEmp201;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmp201Repository extends JpaRepository<HrEmp201, UUID> {

    @Query("SELECT e FROM HrEmp201 e WHERE e.tenantId = :#{#tenantId.value} ORDER BY e.periodStart DESC")
    List<HrEmp201> findAllByTenant(TenantId tenantId);

    @Query("SELECT e FROM HrEmp201 e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id")
    Optional<HrEmp201> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(p) FROM HrPayslip p WHERE p.payRunId = :payRunId")
    int countByPayRunId(UUID payRunId);

    /** The return a pay run is part of. */
    @Query("SELECT e FROM HrEmp201 e WHERE e.tenantId = :#{#tenantId.value} AND e.id IN (SELECT l.emp201Id FROM HrEmp201Run l WHERE l.payRunId = :payRunId)")
    List<HrEmp201> forPayRun(TenantId tenantId, UUID payRunId);

    /** The returns the given pay runs are part of, each once. */
    @Query("SELECT e FROM HrEmp201 e WHERE e.tenantId = :#{#tenantId.value} AND e.id IN (SELECT l.emp201Id FROM HrEmp201Run l WHERE l.payRunId IN :runIds)")
    List<HrEmp201> forPayRuns(TenantId tenantId, java.util.Collection<UUID> runIds);

    /** Which of the given pay runs are part of some return. */
    @Query("SELECT l.payRunId FROM HrEmp201Run l WHERE l.payRunId IN :runIds")
    List<UUID> coveredRuns(java.util.Collection<UUID> runIds);

    /** The return of a tax month that has not been filed yet, if there is one. */
    @Query("SELECT e FROM HrEmp201 e WHERE e.tenantId = :#{#tenantId.value} AND e.taxMonth = :taxMonth AND e.status = 'DRAFT' ORDER BY e.createdAt")
    List<HrEmp201> draftForMonth(TenantId tenantId, java.time.LocalDate taxMonth);

    /** People paid across the given runs, each once. */
    @Query("SELECT COUNT(DISTINCT p.employeeId) FROM HrPayslip p WHERE p.payRunId IN :runIds")
    int employeesInRuns(java.util.Collection<UUID> runIds);
}
