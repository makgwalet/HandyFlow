package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPerfPlanRepository extends JpaRepository<HrPerfPlan, UUID> {

    @Query("SELECT p FROM HrPerfPlan p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<HrPerfPlan> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT p FROM HrPerfPlan p WHERE p.tenantId = :#{#tenantId.value} ORDER BY p.createdAt DESC")
    List<HrPerfPlan> forTenant(TenantId tenantId);

    @Query("SELECT p FROM HrPerfPlan p WHERE p.tenantId = :#{#tenantId.value} AND p.employeeId = :employeeId ORDER BY p.createdAt DESC")
    List<HrPerfPlan> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT p FROM HrPerfPlan p WHERE p.tenantId = :#{#tenantId.value} AND p.caseId = :caseId ORDER BY p.createdAt DESC")
    List<HrPerfPlan> forCase(TenantId tenantId, UUID caseId);
}
