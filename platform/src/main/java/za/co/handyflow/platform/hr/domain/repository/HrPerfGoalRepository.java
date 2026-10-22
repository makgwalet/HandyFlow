package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPerfGoalRepository extends JpaRepository<HrPerfGoal, UUID> {

    @Query("SELECT g FROM HrPerfGoal g WHERE g.tenantId = :#{#tenantId.value} AND g.id = :id")
    Optional<HrPerfGoal> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT g FROM HrPerfGoal g WHERE g.tenantId = :#{#tenantId.value} AND g.employeeId = :employeeId ORDER BY g.dueDate, g.createdAt")
    List<HrPerfGoal> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT g FROM HrPerfGoal g WHERE g.tenantId = :#{#tenantId.value} ORDER BY g.dueDate, g.createdAt")
    List<HrPerfGoal> forTenant(TenantId tenantId);
}
