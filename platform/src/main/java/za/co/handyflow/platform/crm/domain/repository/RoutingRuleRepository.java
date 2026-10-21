package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.RoutingRule;
import za.co.handyflow.platform.shared.TenantId;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {
    @Query("SELECT r FROM RoutingRule r WHERE r.tenantId = :tenantId AND r.deletedAt IS NULL ORDER BY r.position, r.id")
    List<RoutingRule> findAllActive(@Param("tenantId") TenantId tenantId);

    @Query("SELECT r FROM RoutingRule r WHERE r.tenantId = :tenantId AND r.deletedAt IS NULL AND r.id = :id")
    Optional<RoutingRule> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);
}
