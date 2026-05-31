package za.co.handyflow.platform.billing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.billing.domain.model.TenantModule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantModuleRepository extends JpaRepository<TenantModule, UUID> {

    @Query("SELECT m FROM TenantModule m WHERE m.tenantId = :tenantId AND m.status NOT IN ('CANCELLED','SUSPENDED') ORDER BY m.moduleKey")
    List<TenantModule> findActiveByTenant(UUID tenantId);

    @Query("SELECT m FROM TenantModule m WHERE m.tenantId = :tenantId AND m.moduleKey = :moduleKey")
    Optional<TenantModule> findByTenantAndKey(UUID tenantId, String moduleKey);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM TenantModule m WHERE m.tenantId = :tenantId AND m.moduleKey = :moduleKey AND (m.status IN ('TRIAL','ACTIVE') OR (m.status = 'CANCELLED' AND m.accessUntil > CURRENT_TIMESTAMP))")
    boolean hasAccess(UUID tenantId, String moduleKey);
}