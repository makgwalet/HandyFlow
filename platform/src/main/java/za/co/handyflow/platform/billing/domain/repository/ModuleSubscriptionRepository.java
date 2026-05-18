package za.co.handyflow.platform.billing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.billing.domain.model.ModuleSubscription;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModuleSubscriptionRepository extends JpaRepository<ModuleSubscription, UUID> {
    List<ModuleSubscription> findAllByTenantId(TenantId tenantId);

    Optional<ModuleSubscription> findByTenantIdAndModuleKey(
            TenantId tenantId, String moduleKey
    );

    boolean existsByTenantIdAndModuleKeyAndStatus(
            TenantId tenantId,
            String moduleKey,
            ModuleSubscription.ModuleStatus status
    );
}
