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

    @Override
    @Transactional(readOnly = true)
    public List<CatalogueItemSummary> searchItems(TenantId tenantId, String query) {
        List<CatalogueItem> items = (query == null || query.isBlank())
                ? itemRepository.findAllActive(tenantId)
                : itemRepository.searchByName(tenantId, query);
        return items.stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogueItemSummary> findItemById(TenantId tenantId, UUID itemId) {
        return itemRepository.findActiveById(tenantId, itemId)
                .map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatalogueItemSummary> findItemByBarcode(TenantId tenantId, String barcode) {
        return itemRepository.findByTenantIdAndBarcode(tenantId.getValue(), barcode)
                .map(this::toSummary);
    }

    private CatalogueItemSummary toSummary(CatalogueItem item) {
        return new CatalogueItemSummary(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getUnit(),
                item.getDefaultPrice(),
                item.getVatRate(),
                item.getCategory() != null ? item.getCategory().getName() : null
        );
    }
}