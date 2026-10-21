package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.CatalogueFacade;
import za.co.handyflow.platform.catalogue.CatalogueItemSummary;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItem;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogueFacadeImpl implements CatalogueFacade {
    private final CatalogueItemRepository itemRepository;

    /** New selections only see sellable (ACTIVE) items. */
    @Override
    @Transactional(readOnly = true)
    public List<CatalogueItemSummary> searchItems(TenantId tenantId, String query) {
        List<CatalogueItem> items = (query == null || query.isBlank())
                ? itemRepository.findAllSellable(tenantId)
                : itemRepository.searchSellable(tenantId, query.trim());
        return items.stream().map(CatalogueFacadeImpl::toSummary).toList();
    }

    /** By id: any live item, in any status, so documents that already use it keep reading it. */
    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogueItemSummary> findItemById(TenantId tenantId, UUID itemId) {
        return itemRepository.findActiveById(tenantId, itemId).map(CatalogueFacadeImpl::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogueItemSummary> findItemByBarcode(TenantId tenantId, String barcode) {
        if (barcode == null || barcode.isBlank()) return Optional.empty();
        return itemRepository.findSellableByBarcode(tenantId, barcode.trim()).map(CatalogueFacadeImpl::toSummary);
    }

    static CatalogueItemSummary toSummary(CatalogueItem item) {
        return new CatalogueItemSummary(
                item.getId(), item.getName(), item.getDescription(), item.getUnit(),
                item.getDefaultPrice(), item.getVatRate(),
                item.getCategory() != null ? item.getCategory().getName() : null,
                item.getSku(), item.getBarcode(), item.getItemType().name(), item.getStatus().name(), item.isVatExempt());
    }
}
