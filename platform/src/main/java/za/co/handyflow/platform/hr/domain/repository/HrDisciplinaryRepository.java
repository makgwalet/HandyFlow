package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrDisciplinary;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrDisciplinaryRepository extends JpaRepository<HrDisciplinary, UUID> {

    @Query("SELECT d FROM HrDisciplinary d WHERE d.employeeId = :employeeId ORDER BY d.incidentDate DESC")
    List<HrDisciplinary> findByEmployee(UUID employeeId);

    @Query("SELECT d FROM HrDisciplinary d WHERE d.tenantId = :#{#tenantId.value} AND d.id = :id")
    Optional<HrDisciplinary> findByTenantAndId(TenantId tenantId, UUID id);
}