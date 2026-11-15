package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelSiteAccessRule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FuelSiteAccessRuleRepository extends JpaRepository<FuelSiteAccessRule, UUID> {

    @Query("SELECT r FROM FuelSiteAccessRule r WHERE r.tenantId = :tenantId AND r.siteId = :siteId")
    Optional<FuelSiteAccessRule> findForSite(TenantId tenantId, UUID siteId);
}
