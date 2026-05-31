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
}