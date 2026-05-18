package za.co.handyflow.platform.catalogue;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueFacade {
    List<CatalogueItemSummary> searchItems(TenantId tenantId, String query);
    Optional<CatalogueItemSummary> findItemById(TenantId tenantId, UUID itemId);
}
