package za.co.handyflow.platform.desk.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.desk.domain.model.DeskSlaPolicy;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeskSlaPolicyRepository extends JpaRepository<DeskSlaPolicy, UUID> {
    List<DeskSlaPolicy> findByTenantId(TenantId tenantId);
    Optional<DeskSlaPolicy> findByTenantIdAndPriority(TenantId tenantId, String priority);
}
