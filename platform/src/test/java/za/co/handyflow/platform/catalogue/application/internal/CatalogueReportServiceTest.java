package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueReportServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final Caller PRICES = new Caller(UUID.randomUUID(), true, false, false);
    private static final Caller NO_PRICES = new Caller(UUID.randomUUID(), false, false, false);

    @Mock CatalogueItemRepository items;
    @Mock CatalogueItemMediaRepository media;
    @Mock CatalogueItemChannelRepository publications;
    @Mock CatalogueAuditEventRepository audit;
    @Mock CataloguePriceChangeRepository changes;
    @Mock CataloguePriceListRepository lists;
    @Mock CatalogueBundleComponentRepository bundles;
    @Mock CataloguePricingService pricing;
    CatalogueReportService service;
    CatalogueCategory building;
    CatalogueItem cement, sand, brick, kit;

    private CatalogueItem make(String name, String sku, CatalogueItemType type, CatalogueCategory cat, String price, String cost, String barcode, CatalogueItemStatus status) {
        return CatalogueItem.create(TENANT, cat, new CatalogueItem.Details(name, null, sku, barcode, null, type, "each",
                price == null ? null : new BigDecimal(price), cost == null ? null : new BigDecimal(cost), new BigDecimal("15.00"), false), status);
    }

    @BeforeEach
    void setUp() {
        service = new CatalogueReportService(items, media, publications, audit, changes, lists, bundles, pricing);
        service.useClock(Clock.fixed(NOW, ZoneOffset.UTC));
        building = CatalogueCategory.create(TENANT, "Building", null);
        cement = make("Cement", "CEM", CatalogueItemType.PRODUCT, building, "100.00", "90.00", "6001", CatalogueItemStatus.ACTIVE);   // margin 10%
        sand = make("Sand", "SND", CatalogueItemType.PRODUCT, building, "50.00", "20.00", null, CatalogueItemStatus.ACTIVE);          // margin 60%, no barcode
        brick = make("Brick", "BRK", CatalogueItemType.PRODUCT, null, "5.00", null, "6002", CatalogueItemStatus.ACTIVE);              // no cost, no category
        kit = make("Starter kit", "KIT", CatalogueItemType.PACKAGE, null, "130.00", null, null, CatalogueItemStatus.ACTIVE);
        when(items.allLive(TENANT)).thenReturn(List.of(cement, sand, brick, kit));
        when(items.parentIdsWithVariants(TENANT)).thenReturn(List.of());
        when(pricing.pricesOnList(eq(TENANT), any(), anyCollection())).thenAnswer(inv -> {
            java.util.Collection<CatalogueItem> in = inv.getArgument(2);
            return in.stream().filter(i -> i.getDefaultPrice() != null).collect(java.util.stream.Collectors.toMap(CatalogueItem::getId, CatalogueItem::getDefaultPrice));
        });
    }

    private static List<String> column(za.co.handyflow.platform.catalogue.dto.ReportResponse r, String key) {
        int idx = -1;
        for (int i = 0; i < r.columns().size(); i++) if (r.columns().get(i).key().equals(key)) idx = i;
        final int at = idx;
        return r.rows().stream().map(row -> row.get(at)).toList();
    }

    @Test
    void theListMarksWhichReportsTheCallerMayRun() {
        var all = service.reports(NO_PRICES);
        assertThat(all.size()).isEqualTo(12);
        assertThat(all.stream().filter(d -> !d.available()).map(d -> d.code()).toList().contains("MARGIN")).isTrue();
        assertThat(all.stream().filter(d -> d.code().equals("NO_PICTURE")).findFirst().orElseThrow().available()).isTrue();
        assertThat(service.reports(PRICES).stream().allMatch(d -> d.available())).isTrue();
    }

    @Test
    void costReportsAreRefusedWithoutPriceAccessAndUnknownOnesAreNotFound() {
        assertThatThrownBy(() -> service.run(TENANT, "MARGIN", null, NO_PRICES)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.csv(TENANT, "PACKAGES", null, NO_PRICES)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.run(TENANT, "NOPE", null, PRICES)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void marginListsLowestFirstAndLowMarginKeepsOnlyThoseBelowTheLimit() {
        var r = service.run(TENANT, "margin", null, PRICES);
        assertThat(column(r, "name")).containsExactly("Cement", "Sand");   // brick and kit have no cost
        assertThat(column(r, "margin")).containsExactly("10.0", "60.0");
        var low = service.run(TENANT, "LOW_MARGIN", new CatalogueReportService.Params(null, 30, null), PRICES);
        assertThat(column(low, "name")).containsExactly("Cement");
        assertThat(low.note()).contains("below 30%");
        assertThat(service.run(TENANT, "LOW_MARGIN", null, PRICES).rows().size()).isEqualTo(1);   // default 20%
        assertThatThrownBy(() -> service.run(TENANT, "LOW_MARGIN", new CatalogueReportService.Params(null, 100, null), PRICES)).hasMessageContaining("between 0 and 99");
    }

    @Test
    void itemsWithAPriceButNoCostAreListed() {
        var r = service.run(TENANT, "NO_COST", null, PRICES);
        assertThat(column(r, "name")).containsExactly("Brick", "Starter kit");
    }

    @Test
    void priceListShowsActiveItemsWithAndWithoutVat() {
        var r = service.run(TENANT, "PRICE_LIST", null, NO_PRICES);
        assertThat(r.rows().size()).isEqualTo(4);
        var cementRow = r.rows().stream().filter(row -> row.get(0).equals("Cement")).findFirst().orElseThrow();
        assertThat(cementRow.get(4)).isEqualTo("100.00");
        assertThat(cementRow.get(5)).isEqualTo("115.00");
        assertThat(cementRow.get(2)).isEqualTo("Building");
    }

    @Test
    void anUnknownPriceListIsRefused() {
        var id = UUID.randomUUID();
        when(lists.findLiveById(TENANT, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.run(TENANT, "PRICE_LIST", new CatalogueReportService.Params(id, null, null), NO_PRICES)).hasMessageContaining("does not exist");
    }

    @Test
    void parentsOfVariantsAreLeftOutAndTheirVariantsListed() {
        var parent = make("Shirt", "SH", CatalogueItemType.PRODUCT, null, "100.00", null, null, CatalogueItemStatus.ACTIVE);
        var variant = make("Shirt — Large", "SH-L", CatalogueItemType.PRODUCT, null, "100.00", null, "6003", CatalogueItemStatus.ACTIVE);
        variant.asVariant(parent.getId(), "large", "Large", "Large");
        when(items.allLive(TENANT)).thenReturn(List.of(parent, variant));
        when(items.parentIdsWithVariants(TENANT)).thenReturn(List.of(parent.getId()));
        var r = service.run(TENANT, "PRICE_LIST", null, NO_PRICES);
        assertThat(column(r, "name")).containsExactly("Shirt — Large");
    }

    @Test
    void gapReportsFindWhatIsMissing() {
        when(media.itemIdsWithMedia(TENANT, MediaRules.Kind.IMAGE)).thenReturn(List.of(cement.getId()));
        assertThat(column(service.run(TENANT, "NO_PICTURE", null, NO_PRICES), "name")).containsExactly("Brick", "Sand", "Starter kit");
        assertThat(column(service.run(TENANT, "NO_BARCODE", null, NO_PRICES), "name")).containsExactly("Sand");   // goods only; the package is not goods
        when(publications.publishedItemIds(TENANT)).thenReturn(List.of(cement.getId(), sand.getId()));
        assertThat(column(service.run(TENANT, "NOT_PUBLISHED", null, NO_PRICES), "name")).containsExactly("Brick", "Starter kit");
    }

    @Test
    void categoriesAreCountedWithAnAveragePrice() {
        var r = service.run(TENANT, "BY_CATEGORY", null, NO_PRICES);
        assertThat(column(r, "category")).containsExactly("Building", "No category");
        assertThat(column(r, "items")).containsExactly("2", "2");
        assertThat(column(r, "avg")).containsExactly("75.00", "67.50");
    }

    @Test
    void statusesAreAllListedEvenWhenEmpty() {
        var r = service.run(TENANT, "BY_STATUS", null, NO_PRICES);
        assertThat(column(r, "status")).containsExactly("Draft", "Active", "Suspended", "Discontinued", "Archived");
        assertThat(column(r, "items")).containsExactly("0", "4", "0", "0", "0");
    }

    @Test
    void packagesShowWhatThePartsAreWorthAndTheSaving() {
        when(bundles.allForTenant(TENANT)).thenReturn(List.of(
                CatalogueBundleComponent.of(TENANT, kit.getId(), cement.getId(), new BigDecimal("1.000"), 0),
                CatalogueBundleComponent.of(TENANT, kit.getId(), sand.getId(), new BigDecimal("1.000"), 1)));
        var r = service.run(TENANT, "PACKAGES", null, PRICES);
        assertThat(column(r, "name")).containsExactly("Starter kit");
        assertThat(column(r, "partsTotal")).containsExactly("150.00");
        assertThat(column(r, "saving")).containsExactly("20.00");
        assertThat(column(r, "unpriced")).containsExactly("0");
    }

    @Test
    void priceChangesInTheWindowAreListedWithTheirPercentage() {
        var listId = UUID.randomUUID();
        var l = CataloguePriceList.create(TENANT, "RETAIL", "Retail", true, 0);
        ReflectionTestUtils.setField(l, "id", listId);
        when(lists.findAllLive(TENANT)).thenReturn(List.of(l));
        var ch = CataloguePriceChange.propose(TENANT, cement.getId(), listId, new BigDecimal("80.00"), new BigDecimal("100.00"), LocalDate.of(2026, 10, 1), "Supplier increase", null);
        when(changes.appliedSince(eq(TENANT), eq(LocalDate.of(2026, 7, 23)), any())).thenReturn(List.of(ch));
        var r = service.run(TENANT, "PRICE_CHANGES", null, PRICES);
        assertThat(column(r, "name")).containsExactly("Cement");
        assertThat(column(r, "change")).containsExactly("25.0");
        assertThat(column(r, "list")).containsExactly("Retail");
        assertThat(column(r, "date")).containsExactly("2026-10-01");
    }

    @Test
    void recentChangesShowEachItemOnceWithItsLatestChange() {
        var newer = CatalogueAuditEvent.of(TENANT, "ITEM", cement.getId(), "Cement", CatalogueAuditAction.PRICE_CHANGED, "R 80 to R 100", null);
        var older = CatalogueAuditEvent.of(TENANT, "ITEM", cement.getId(), "Cement", CatalogueAuditAction.CREATED, null, null);
        var gone = CatalogueAuditEvent.of(TENANT, "ITEM", UUID.randomUUID(), "Old item", CatalogueAuditAction.DELETED, null, null);
        when(audit.itemLinesSince(eq(TENANT), eq(NOW.minusSeconds(30L * 86400)), any())).thenReturn(List.of(newer, gone, older));
        var r = service.run(TENANT, "RECENT_CHANGES", null, NO_PRICES);
        assertThat(column(r, "name")).containsExactly("Cement", "Old item (deleted)");
        assertThat(column(r, "what")).containsExactly("Price changed", "Deleted");
        assertThat(r.rows().toString()).doesNotContain("R 80");   // no price detail for people without price access
    }

    @Test
    void csvHasAHeaderAndGuardsTextButNotNumbers() {
        var risky = make("=HYPERLINK(\"x\")", "SKU,1", CatalogueItemType.PRODUCT, null, "10.00", "-5.00", "6009", CatalogueItemStatus.ACTIVE);
        when(items.allLive(TENANT)).thenReturn(List.of(risky));
        String csv = service.csv(TENANT, "MARGIN", null, PRICES);
        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo("Item,SKU,Category,Price,Cost,Margin %");
        assertThat(lines[1]).startsWith("\"'=HYPERLINK");   // guarded with an apostrophe, then quoted because of its quotes
        assertThat(lines[1]).contains("\"SKU,1\"");
        assertThat(lines[1]).endsWith(",10.00,-5.00,150.0");
    }
}
