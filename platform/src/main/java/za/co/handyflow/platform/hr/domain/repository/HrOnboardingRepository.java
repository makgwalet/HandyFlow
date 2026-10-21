package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.hr.domain.model.HrOnboarding;

import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrOnboardingRepository extends JpaRepository<HrOnboarding, UUID> {

    @Query("SELECT o FROM HrOnboarding o WHERE o.tenantId = :#{#tenantId.value} ORDER BY o.startDate DESC")
    List<HrOnboarding> forTenant(TenantId tenantId);

    @Query("SELECT o FROM HrOnboarding o WHERE o.tenantId = :#{#tenantId.value} AND o.id = :id")
    Optional<HrOnboarding> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT o FROM HrOnboarding o WHERE o.tenantId = :#{#tenantId.value} AND o.employeeId = :employeeId")
    Optional<HrOnboarding> findByEmployee(TenantId tenantId, UUID employeeId);
}
