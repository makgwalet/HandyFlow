package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeRelative;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeRelativeRepository extends JpaRepository<HrEmployeeRelative, UUID> {

    @Query("SELECT r FROM HrEmployeeRelative r WHERE r.tenantId = :#{#tenantId.value} AND r.employeeId = :employeeId AND r.removedAt IS NULL ORDER BY r.kind, r.createdAt")
    List<HrEmployeeRelative> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT r FROM HrEmployeeRelative r WHERE r.tenantId = :#{#tenantId.value} AND r.employeeId = :employeeId AND r.id = :id AND r.removedAt IS NULL")
    Optional<HrEmployeeRelative> findOne(TenantId tenantId, UUID employeeId, UUID id);
}
