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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueBulkServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller ADMIN = new Caller(UUID.randomUUID(), true, true, true, true);
    private static final Caller WRITER = new Caller(UUID.randomUUID(), false, false, false, false);

    @Mock CatalogueItemRepository items;
    @Mock CatalogueCategoryRepository categories;
    @Mock CatalogueService catalogue;
    @Mock CataloguePricingService pricing;
    CatalogueBulkService service;
    CatalogueItem a, b;
    UUID listId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CatalogueBulkService(items, categories, catalogue, pricing);
        a = item("Cement", "A", "100.00");
        b = item("Sand", "B", "50.00");
        when(items.findLiveByIds(eq(TENANT), any())).thenReturn(List.of(a, b));
        when(items.parentIdsWithVariants(TENANT)).thenReturn(List.of());
        when(pricing.defaultListId(TENANT)).thenReturn(listId);
        when(pricing.approvalRequired(TENANT)).thenReturn(false);
    }

    private static CatalogueItem item(String name, String sku, String price) {
        var d = new CatalogueItem.Details(name, "d", sku, null, "Acme", CatalogueItemType.PRODUCT, "each",
                new BigDecimal(price), new BigDecimal("20.00"), new BigDecimal("15.00"), false);
        return CatalogueItem.create(TENANT, null, d, CatalogueItemStatus.ACTIVE);
    }

    private BulkRequest price(String pct, boolean dry) { return new BulkRequest(List.of(a.getId(), b.getId()), "PRICE_PERCENT", null, "Supplier increase", null, new BigDecimal(pct), null, dry); }

    @Test
    void aDryRunShowsTheNewPricesAndChangesNothing() {
        var r = service.run(TENANT, price("10", true), ADMIN);
        assertThat(r.dryRun()).isTrue();
        assertThat(r.ok()).isEqualTo(2);
        assertThat(r.rows().get(0).message()).isEqualTo("100.00 → 110.00");
        verify(pricing, never()).propose(any(), any(), any(), any());
    }

    @Test
    void appliesPriceChangesThroughTheApprovalWorkflow() {
        service.run(TENANT, price("10", false), ADMIN);
        var req = ArgumentCaptor.forClass(PriceChangeRequest.class);
        verify(pricing).propose(eq(TENANT), eq(a.getId()), req.capture(), eq(ADMIN));
        assertThat(req.getValue().newPrice()).isEqualByComparingTo("110.00");
        assertThat(req.getValue().priceListId()).isEqualTo(listId);
        assertThat(req.getValue().reason()).isEqualTo("Supplier increase");
    }

    @Test
    void mentionsWhenApprovalIsNeeded() {
        when(pricing.approvalRequired(TENANT)).thenReturn(true);
        assertThat(service.run(TENANT, price("10", true), ADMIN).rows().get(0).message()).contains("waits for approval");
    }

    @Test
    void oneItemBeingRefusedDoesNotStopTheRest() {
        when(pricing.propose(eq(TENANT), eq(a.getId()), any(), any())).thenThrow(new IllegalArgumentException("A price change for Retail is already waiting."));
        var r = service.run(TENANT, price("10", false), ADMIN);
        assertThat(r.refused()).isEqualTo(1);
        assertThat(r.ok()).isEqualTo(1);
        assertThat(r.rows().get(0).message()).contains("already waiting");
    }

    @Test
    void refusesItemsSoldThroughVariantsAndPricesBelowTheMinimum() {
        when(items.parentIdsWithVariants(TENANT)).thenReturn(List.of(a.getId()));
        b.changeMinPrice(new BigDecimal("48.00"));
        var r = service.run(TENANT, price("-10", true), ADMIN);
        assertThat(r.rows().get(0).message()).contains("variants");
        assertThat(r.rows().get(1).outcome()).isEqualTo("REFUSED");
        assertThat(r.refused()).isEqualTo(2);
    }

    @Test
    void priceChangesNeedThePricePermission() {
        assertThatThrownBy(() -> service.run(TENANT, price("5", true), WRITER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void movesStatusAndSkipsItemsAlreadyThere() {
        var r = service.run(TENANT, new BulkRequest(List.of(a.getId(), b.getId()), "STATUS", CatalogueItemStatus.ACTIVE, null, null, null, null, false), ADMIN);
        assertThat(r.unchanged()).isEqualTo(2);
        verify(catalogue, never()).changeStatus(any(), any(), any(), any());
        var s = service.run(TENANT, new BulkRequest(List.of(a.getId()), "STATUS", CatalogueItemStatus.SUSPENDED, "Out of season", null, null, null, false), ADMIN);
        assertThat(s.ok()).isEqualTo(1);
        verify(catalogue).changeStatus(eq(TENANT), eq(a.getId()), any(ItemStatusRequest.class), eq(ADMIN));
    }

    @Test
    void suspendingNeedsAReasonAndArchivingNeedsPermission() {
        var noReason = service.run(TENANT, new BulkRequest(List.of(a.getId()), "STATUS", CatalogueItemStatus.SUSPENDED, null, null, null, null, true), ADMIN);
        assertThat(noReason.refused()).isEqualTo(1);
        assertThatThrownBy(() -> service.run(TENANT, new BulkRequest(List.of(a.getId()), "STATUS", CatalogueItemStatus.ARCHIVED, "old", null, null, null, true), WRITER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void movesItemsToACategory() {
        var cat = org.mockito.Mockito.mock(CatalogueCategory.class);
        UUID catId = UUID.randomUUID();
        when(cat.getName()).thenReturn("Building");
        when(categories.findActiveById(TENANT, catId)).thenReturn(java.util.Optional.of(cat));
        var r = service.run(TENANT, new BulkRequest(List.of(a.getId()), "CATEGORY", null, null, catId, null, null, false), WRITER);
        assertThat(r.ok()).isEqualTo(1);
        var req = ArgumentCaptor.forClass(CreateItemRequest.class);
        verify(catalogue).updateItem(eq(TENANT), eq(a.getId()), req.capture(), eq(WRITER));
        assertThat(req.getValue().categoryId()).isEqualTo(catId);
        assertThat(req.getValue().name()).isEqualTo("Cement");
    }

    @Test
    void rejectsUnknownActionsAndTooManyItems() {
        assertThatThrownBy(() -> service.run(TENANT, new BulkRequest(List.of(a.getId()), "FLY", null, null, null, null, null, true), ADMIN)).isInstanceOf(IllegalArgumentException.class);
        var many = java.util.stream.IntStream.range(0, 201).mapToObj(i -> UUID.randomUUID()).toList();
        assertThatThrownBy(() -> service.run(TENANT, new BulkRequest(many, "STATUS", CatalogueItemStatus.ACTIVE, null, null, null, null, true), ADMIN)).isInstanceOf(IllegalArgumentException.class);
    }
}
