package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueBundleComponent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface CatalogueBundleComponentRepository extends JpaRepository<CatalogueBundleComponent, UUID> {

    @Query("SELECT c FROM CatalogueBundleComponent c WHERE c.tenantId = :tenantId AND c.bundleItemId = :bundleId ORDER BY c.sortOrder")
    List<CatalogueBundleComponent> forBundle(TenantId tenantId, UUID bundleId);

    /** Packages that use this item, so it cannot be archived or deleted without a warning. */
    @Query("SELECT c FROM CatalogueBundleComponent c WHERE c.tenantId = :tenantId AND c.componentItemId = :itemId")
    List<CatalogueBundleComponent> usedBy(TenantId tenantId, UUID itemId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CatalogueBundleComponent c WHERE c.tenantId = :tenantId AND c.bundleItemId = :bundleId")
    void deleteForBundle(TenantId tenantId, UUID bundleId);

    @Query("SELECT c FROM CatalogueBundleComponent c WHERE c.tenantId = :tenantId ORDER BY c.bundleItemId, c.sortOrder")
    List<CatalogueBundleComponent> allForTenant(TenantId tenantId);
}
