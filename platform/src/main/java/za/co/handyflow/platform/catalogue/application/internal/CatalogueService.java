package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.CatalogueItemSummary;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueCategory;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItem;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueCategoryRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.CategoryResponse;
import za.co.handyflow.platform.catalogue.dto.CreateCategoryRequest;
import za.co.handyflow.platform.catalogue.dto.CreateItemRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogueService {

    private final CatalogueItemRepository itemRepository;
    private final CatalogueCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CatalogueItemSummary> searchItems(TenantId tenantId, String query) {
        var items = (query == null || query.isBlank())
                ? itemRepository.findAllActive(tenantId)
                : itemRepository.searchByName(tenantId, query);
        return items.stream().map(this::toSummary).toList();
    }

    @Transactional
    public CatalogueItemSummary createItem(TenantId tenantId,
                                           CreateItemRequest request) {
        if (itemRepository.existsByTenantIdAndNameAndDeletedAtIsNull(
                tenantId, request.name())) {
            throw new IllegalArgumentException(
                    "An item named '" + request.name() + "' already exists"
            );
        }

        CatalogueCategory category = null;
        if (request.categoryId() != null) {
            category = categoryRepository
                    .findActiveById(tenantId, request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category", request.categoryId().toString()
                    ));
        }

        BigDecimal vatRate = request.vatRate() != null
                ? request.vatRate()
                : new BigDecimal("15.00");

        CatalogueItem item = CatalogueItem.create(
                tenantId, category, request.name(), request.description(),
                request.unit(), request.defaultPrice(), vatRate
        );

        itemRepository.save(item);
        log.info("Created catalogue item={} tenant={}", item.getName(), tenantId);
        return toSummary(item);
    }

    @Transactional
    public void softDeleteItem(TenantId tenantId, UUID itemId) {
        CatalogueItem item = itemRepository
                .findActiveById(tenantId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CatalogueItem", itemId.toString()
                ));
        // WHY null for deletedBy here?
        // We'll wire the current userId from SecurityContext in a later step
        // when we add a CurrentUserService. For now null is acceptable.
        item.softDelete(null);
        itemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(TenantId tenantId) {
        return categoryRepository.findAllActive(tenantId)
                .stream()
                .map(c -> new CategoryResponse(
                        c.getId(), c.getName(),
                        c.getDescription(), c.getSortOrder()
                ))
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(TenantId tenantId,
                                           CreateCategoryRequest request) {
        CatalogueCategory cat = CatalogueCategory.create(
                tenantId, request.name(), request.description()
        );
        categoryRepository.save(cat);
        return new CategoryResponse(
                cat.getId(), cat.getName(),
                cat.getDescription(), cat.getSortOrder()
        );
    }

    private CatalogueItemSummary toSummary(CatalogueItem item) {
        return new CatalogueItemSummary(
                item.getId(), item.getName(), item.getDescription(),
                item.getUnit(), item.getDefaultPrice(), item.getVatRate(),
                item.getCategory() != null ? item.getCategory().getName() : null
        );
    }
}