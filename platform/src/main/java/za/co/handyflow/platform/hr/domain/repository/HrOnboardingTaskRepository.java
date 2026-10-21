package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.hr.domain.model.HrOnboardingTask;

import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrOnboardingTaskRepository extends JpaRepository<HrOnboardingTask, UUID> {

    @Query("SELECT t FROM HrOnboardingTask t WHERE t.onboardingId IN :ids ORDER BY t.sortOrder, t.createdAt")
    List<HrOnboardingTask> forOnboardings(Collection<UUID> ids);

    @Query("SELECT t FROM HrOnboardingTask t WHERE t.tenantId = :#{#tenantId.value} AND t.id = :id")
    Optional<HrOnboardingTask> findByTenantAndId(TenantId tenantId, UUID id);
}
