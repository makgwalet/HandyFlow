package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrMedicalSchemeMembership;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HrMedicalSchemeMembershipRepository extends JpaRepository<HrMedicalSchemeMembership, UUID> {

    @Query("SELECT m FROM HrMedicalSchemeMembership m WHERE m.tenantId = :#{#tenantId.value} AND m.employeeId = :employeeId ORDER BY m.effectiveFrom DESC")
    List<HrMedicalSchemeMembership> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT m FROM HrMedicalSchemeMembership m WHERE m.tenantId = :#{#tenantId.value} AND m.employeeId IN :employeeIds")
    List<HrMedicalSchemeMembership> forEmployees(TenantId tenantId, Collection<UUID> employeeIds);
}
