package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelSiteApprovedTanker;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelSiteApprovedTankerRepository extends JpaRepository<FuelSiteApprovedTanker, UUID> {

    @Query("SELECT a FROM FuelSiteApprovedTanker a WHERE a.tenantId = :tenantId AND a.siteId = :siteId")
    List<FuelSiteApprovedTanker> findForSite(TenantId tenantId, UUID siteId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM FuelSiteApprovedTanker a WHERE a.tenantId = :tenantId AND a.siteId = :siteId")
    void deleteForSite(TenantId tenantId, UUID siteId);
}
