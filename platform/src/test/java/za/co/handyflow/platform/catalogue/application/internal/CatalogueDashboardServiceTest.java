package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueDashboardServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller PRICE_USER = new Caller(UUID.randomUUID(), true, true, true, true);
    private static final Caller CLERK = new Caller(UUID.randomUUID(), false, false, false, false);

    @Mock CatalogueItemRepository items;
    @Mock CatalogueCategoryRepository categories;
    @Mock CatalogueAuditEventRepository audit;
    @Mock CataloguePriceChangeRepository changes;
    @Mock CatalogueStructureService structure;
    @Mock CatalogueService catalogue;
    CatalogueDashboardService service;

    @BeforeEach
    void setUp() {
        service = new CatalogueDashboardService(items, categories, audit, changes, structure, catalogue);
        service.useClock(Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC));
        when(items.countsByTypeAndStatus(TENANT)).thenReturn(List.of(
                new Object[] {CatalogueItemType.PRODUCT, CatalogueItemStatus.ACTIVE, 10L},
                new Object[] {CatalogueItemType.PRODUCT, CatalogueItemStatus.DRAFT, 2L},
                new Object[] {CatalogueItemType.SERVICE, CatalogueItemStatus.ACTIVE, 3L},
                new Object[] {CatalogueItemType.SERVICE, CatalogueItemStatus.DISCONTINUED, 1L},
                new Object[] {CatalogueItemType.PRODUCT, CatalogueItemStatus.ARCHIVED, 50L}));
        when(items.countLiveVariants(TENANT)).thenReturn(7L);
        when(items.countSellable(TENANT)).thenReturn(12L);
        when(items.countIncomplete(TENANT)).thenReturn(4L);
        when(items.countDiscontinuedSince(any(), any())).thenReturn(1L);
        when(items.countsByCategory(TENANT)).thenReturn(List.<Object[]>of(new Object[] {null, 5L}));
        when(items.createdSince(any(), any())).thenReturn(List.of());
        when(items.countCreatedBefore(any(), any())).thenReturn(0L);
        when(items.recentlyChanged(any(), any())).thenReturn(List.of());
        when(categories.findAllActive(TENANT)).thenReturn(List.of());
        when(audit.recent(any(), any())).thenReturn(List.of());
        when(changes.countByStatus(TENANT, PriceChangeStatus.PENDING)).thenReturn(3L);
        when(changes.countByStatus(TENANT, PriceChangeStatus.SCHEDULED)).thenReturn(2L);
    }

    @Test
    void archivedItemsAreLeftOutOfTheCounts() {
        var counts = service.dashboard(TENANT, PRICE_USER).counts();
        assertThat(counts.items()).isEqualTo(16L);
        assertThat(counts.products()).isEqualTo(12L);
        assertThat(counts.services()).isEqualTo(4L);
        assertThat(counts.drafts()).isEqualTo(2L);
        assertThat(counts.discontinued()).isEqualTo(1L);
        assertThat(counts.variants()).isEqualTo(7L);
        assertThat(counts.activeSkus()).isEqualTo(12L);
        assertThat(counts.incomplete()).isEqualTo(4L);
    }

    @Test
    void priceQueueIsOnlyForUsersWhoDealWithPrices() {
        assertThat(service.dashboard(TENANT, PRICE_USER).prices().pending()).isEqualTo(3L);
        assertThat(service.dashboard(TENANT, PRICE_USER).prices().scheduled()).isEqualTo(2L);
        assertThat(service.dashboard(TENANT, CLERK).prices()).isNull();
    }

    @Test
    void growthCoversTwelveMonthsAndUncategorisedItemsAreShown() {
        var d = service.dashboard(TENANT, CLERK);
        assertThat(d.growth().size()).isEqualTo(12);
        assertThat(d.byCategory().size()).isEqualTo(1);
        assertThat(d.byCategory().get(0).name()).isEqualTo("No category");
    }

    @Test
    void activityHidesCostAndPriceWorkflowFromUsersWithoutPriceAccess() {
        var id = UUID.randomUUID();
        when(audit.recent(any(), any())).thenReturn(List.of(
                CatalogueAuditEvent.of(TENANT, "ITEM", id, "Widget", CatalogueAuditAction.PRICE_PROPOSED, "R 10 to R 12", null),
                CatalogueAuditEvent.of(TENANT, "ITEM", id, "Widget", CatalogueAuditAction.PRICE_CHANGED, "price: 10 -> 12; cost: 5 -> 6", null)));
        var clerk = service.dashboard(TENANT, CLERK).activity();
        assertThat(clerk.get(0).detail()).isNull();
        assertThat(clerk.get(1).detail()).isEqualTo("price: 10 -> 12");
        var admin = service.dashboard(TENANT, PRICE_USER).activity();
        assertThat(admin.get(1).detail()).isEqualTo("price: 10 -> 12; cost: 5 -> 6");
    }

    @Test
    void overviewScoresTheItemAndListsMissingAttributes() {
        var d = new CatalogueItem.Details("Widget", "A widget", "WID", null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal("10.00"), new BigDecimal("4.00"), new BigDecimal("15.00"), false);
        var item = CatalogueItem.create(TENANT, null, d, CatalogueItemStatus.ACTIVE);
        when(items.findActiveById(TENANT, item.getId())).thenReturn(Optional.of(item));
        when(structure.missingRequired(TENANT, null, item.getId())).thenReturn(List.of("Colour"));
        when(changes.openForItem(TENANT, item.getId())).thenReturn(List.of());

        var o = service.overview(TENANT, item.getId(), CLERK);
        assertThat(o.completeness().score()).isLessThan(100);
        assertThat(o.completeness().checks().stream().filter(c -> !c.passed()).map(c -> c.code()).toList()).isNotEmpty();
        assertThat(o.missingAttributes()).containsExactly("Colour");
        assertThat(o.approvalState()).isEqualTo("NONE");
    }

    @Test
    void overviewOfAMissingItemIsNotFound() {
        var id = UUID.randomUUID();
        when(items.findActiveById(TENANT, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.overview(TENANT, id, CLERK)).isInstanceOf(ResourceNotFoundException.class);
    }
}
