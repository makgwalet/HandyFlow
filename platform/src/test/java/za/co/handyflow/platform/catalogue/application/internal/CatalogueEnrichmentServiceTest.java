package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueCategoryRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.ApplySuggestionRequest;
import za.co.handyflow.platform.catalogue.dto.CreateItemRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueEnrichmentServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller WRITER = new Caller(UUID.randomUUID(), false, false, false);
    private static final Caller PRICES = new Caller(UUID.randomUUID(), true, false, false);

    @Mock CatalogueItemRepository items;
    @Mock CatalogueCategoryRepository categories;
    @Mock CatalogueService catalogue;
    CatalogueEnrichmentService service;
    CatalogueCategory building;
    CatalogueItem c1, c2, c3, target;

    private CatalogueItem make(String name, String sku, CatalogueCategory cat, String brand, String price, String cost) {
        return CatalogueItem.create(TENANT, cat, new CatalogueItem.Details(name, null, sku, null, brand, CatalogueItemType.PRODUCT, "each",
                price == null ? null : new BigDecimal(price), cost == null ? null : new BigDecimal(cost), new BigDecimal("15.00"), false), CatalogueItemStatus.ACTIVE);
    }

    @BeforeEach
    void setUp() {
        service = new CatalogueEnrichmentService(items, categories, catalogue);
        building = CatalogueCategory.create(TENANT, "Building materials", null);
        c1 = make("Cement 25kg", "CEM-001", building, "PPC", "60", "40");
        c2 = make("Cement 50kg", "CEM-002", building, "PPC", "110", "75");
        c3 = make("Cement Grey 40kg", "CEM-003", building, "PPC", "90", "60");
        target = make("Cement 10kg", null, null, null, "40", "25");
        when(items.allLive(TENANT)).thenReturn(List.of(c1, c2, c3, target));
        when(items.findActiveById(TENANT, target.getId())).thenReturn(Optional.of(target));
        when(categories.findActiveById(TENANT, building.getId())).thenReturn(Optional.of(building));
    }

    @Test
    void theOverviewListsOnlyItemsWithSomethingToSuggest() {
        var out = service.overview(TENANT, WRITER);
        assertThat(out.scanned()).isEqualTo(4);
        assertThat(out.itemsWithSuggestions()).isEqualTo(1);
        var entry = out.items().get(0);
        assertThat(entry.name()).isEqualTo("Cement 10kg");
        assertThat(entry.suggestions().stream().map(s -> s.kind()).toList()).containsExactly("CATEGORY", "BRAND", "SKU");
    }

    @Test
    void variantsAndArchivedItemsAreSkipped() {
        var variant = make("Cement 10kg — Bag", null, null, null, "40", "25");
        variant.asVariant(target.getId(), "bag", "Bag", "Bag");
        var archived = make("Cement 5kg", null, null, null, "20", "10");
        archived.changeStatus(CatalogueItemStatus.ARCHIVED, "old");
        when(items.allLive(TENANT)).thenReturn(List.of(c1, c2, c3, variant, archived));
        assertThat(service.overview(TENANT, WRITER).itemsWithSuggestions()).isEqualTo(0);
    }

    @Test
    void aPriceHintIsGivenOnlyToPeopleWhoMaySeePrices() {
        var noPrice = make("Cement 5kg", "CEM-005", building, "PPC", null, "30");
        when(items.allLive(TENANT)).thenReturn(List.of(c1, c2, c3, noPrice));
        assertThat(service.overview(TENANT, WRITER).itemsWithSuggestions()).isEqualTo(0);
        var withPrices = service.overview(TENANT, PRICES);
        assertThat(withPrices.items().get(0).suggestions().get(0).kind()).isEqualTo("PRICE");
        assertThat(withPrices.items().get(0).suggestions().get(0).applicable()).isFalse();
    }

    @Test
    void oneItemsSuggestionsAreNotFoundForAnUnknownItem() {
        assertThat(service.forItem(TENANT, target.getId(), WRITER).suggestions().size()).isEqualTo(3);
        assertThatThrownBy(() -> service.forItem(TENANT, UUID.randomUUID(), WRITER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void applyingACategoryEditsThroughTheOrdinaryItemUpdateKeepingEverythingElse() {
        service.apply(TENANT, target.getId(), new ApplySuggestionRequest("category", building.getId().toString()), WRITER);
        var sent = ArgumentCaptor.forClass(CreateItemRequest.class);
        verify(catalogue).updateItem(eq(TENANT), eq(target.getId()), sent.capture(), eq(WRITER));
        assertThat(sent.getValue().categoryId()).isEqualTo(building.getId());
        assertThat(sent.getValue().name()).isEqualTo("Cement 10kg");
        assertThat(sent.getValue().defaultPrice().toPlainString()).isEqualTo("40");
        assertThat(sent.getValue().sku()).isNull();
        assertThat(sent.getValue().attributes()).isNull();
    }

    @Test
    void applyingABrandOrSkuFillsOnlyThatField() {
        service.apply(TENANT, target.getId(), new ApplySuggestionRequest("BRAND", "PPC"), WRITER);
        service.apply(TENANT, target.getId(), new ApplySuggestionRequest("SKU", "CEM-004"), WRITER);
        var sent = ArgumentCaptor.forClass(CreateItemRequest.class);
        verify(catalogue, org.mockito.Mockito.times(2)).updateItem(eq(TENANT), eq(target.getId()), sent.capture(), eq(WRITER));
        assertThat(sent.getAllValues().get(0).brand()).isEqualTo("PPC");
        assertThat(sent.getAllValues().get(0).sku()).isNull();
        assertThat(sent.getAllValues().get(1).sku()).isEqualTo("CEM-004");
        assertThat(sent.getAllValues().get(1).brand()).isNull();
    }

    @Test
    void aFieldThatIsAlreadyFilledIsNeverOverwritten() {
        assertThatThrownBy(() -> service.apply(TENANT, c1.getId(), new ApplySuggestionRequest("BRAND", "X"), WRITER)).isInstanceOf(ResourceNotFoundException.class);
        when(items.findActiveById(TENANT, c1.getId())).thenReturn(Optional.of(c1));
        assertThatThrownBy(() -> service.apply(TENANT, c1.getId(), new ApplySuggestionRequest("BRAND", "X"), WRITER)).hasMessageContaining("already has a brand");
        assertThatThrownBy(() -> service.apply(TENANT, c1.getId(), new ApplySuggestionRequest("SKU", "X-1"), WRITER)).hasMessageContaining("already has a SKU");
        assertThatThrownBy(() -> service.apply(TENANT, c1.getId(), new ApplySuggestionRequest("CATEGORY", building.getId().toString()), WRITER)).hasMessageContaining("already has a category");
        verify(catalogue, never()).updateItem(any(), any(), any(), any());
    }

    @Test
    void badRequestsAreRefused() {
        assertThatThrownBy(() -> service.apply(TENANT, target.getId(), null, WRITER)).hasMessageContaining("Choose a suggestion");
        assertThatThrownBy(() -> service.apply(TENANT, target.getId(), new ApplySuggestionRequest("PRICE", "60.00"), WRITER)).hasMessageContaining("cannot be applied");
        assertThatThrownBy(() -> service.apply(TENANT, target.getId(), new ApplySuggestionRequest("CATEGORY", "not-a-uuid"), WRITER)).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> service.apply(TENANT, target.getId(), new ApplySuggestionRequest("CATEGORY", UUID.randomUUID().toString()), WRITER)).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> service.apply(TENANT, target.getId(), new ApplySuggestionRequest("SKU", " "), WRITER)).hasMessageContaining("Choose a suggestion");
        verify(catalogue, never()).updateItem(any(), any(), any(), any());
    }

    @Test
    void aVariantFollowsItsMainItem() {
        var variant = make("Cement 10kg — Bag", null, null, null, "40", "25");
        variant.asVariant(target.getId(), "bag", "Bag", "Bag");
        when(items.findActiveById(TENANT, variant.getId())).thenReturn(Optional.of(variant));
        assertThatThrownBy(() -> service.apply(TENANT, variant.getId(), new ApplySuggestionRequest("BRAND", "PPC"), WRITER)).hasMessageContaining("main item");
        assertThat(service.forItem(TENANT, variant.getId(), WRITER).suggestions()).isEmpty();
    }
}
