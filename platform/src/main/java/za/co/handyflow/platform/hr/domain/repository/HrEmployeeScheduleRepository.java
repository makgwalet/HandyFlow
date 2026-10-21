package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeSchedule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeScheduleRepository extends JpaRepository<HrEmployeeSchedule, UUID> {

    @Query("SELECT a FROM HrEmployeeSchedule a WHERE a.tenantId = :#{#tenantId.value} AND a.employeeId = :employeeId")
    Optional<HrEmployeeSchedule> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT a FROM HrEmployeeSchedule a WHERE a.tenantId = :#{#tenantId.value}")
    List<HrEmployeeSchedule> forTenant(TenantId tenantId);
}
