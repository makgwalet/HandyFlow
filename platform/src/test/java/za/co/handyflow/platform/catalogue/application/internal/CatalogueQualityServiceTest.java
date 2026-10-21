package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueQualityServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller PRICE_USER = new Caller(UUID.randomUUID(), true, true, true);
    private static final Caller CLERK = new Caller(UUID.randomUUID(), false, false, false);

    @Mock CatalogueItemRepository items;
    CatalogueQualityService service;

    @BeforeEach
    void setUp() {
        service = new CatalogueQualityService(items);
        service.useClock(Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC));
        when(items.parentIdsWithVariants(TENANT)).thenReturn(List.of());
    }

    private static CatalogueItem item(String name, String sku, String price, String cost) {
        var d = new CatalogueItem.Details(name, "desc", sku, "600" + sku, "Acme", CatalogueItemType.PRODUCT, "each",
                new BigDecimal(price), new BigDecimal(cost), new BigDecimal("15.00"), false);
        return CatalogueItem.create(TENANT, null, d, CatalogueItemStatus.ACTIVE);
    }

    @Test
    void groupsFindingsBiggestFirstAndCountsThem() {
        when(items.allLive(TENANT)).thenReturn(List.of(item("A", "A1", "10", "1"), item("B", "B1", "10", "1")));
        var r = service.report(TENANT, PRICE_USER);
        assertThat(r.scanned()).isEqualTo(2);
        assertThat(r.groups().get(0).kind()).isEqualTo("MISSING_CATEGORY");
        assertThat(r.groups().get(0).count()).isEqualTo(2);
        assertThat(r.total()).isEqualTo(2);
        assertThat(r.groups().get(0).items().get(0).name()).isEqualTo("A");
    }

    @Test
    void costBasedFindingsAreHiddenFromPeopleWhoCannotSeePrices() {
        when(items.allLive(TENANT)).thenReturn(List.of(item("Loss", "L1", "5", "9")));
        var priceUser = service.report(TENANT, PRICE_USER);
        var clerk = service.report(TENANT, CLERK);
        assertThat(priceUser.groups().stream().anyMatch(g -> g.kind().equals("BELOW_COST"))).isTrue();
        assertThat(clerk.groups().stream().anyMatch(g -> g.kind().equals("BELOW_COST"))).isFalse();
    }

    @Test
    void reportsPossibleDuplicates() {
        when(items.allLive(TENANT)).thenReturn(List.of(item("Cement 50kg", "C1", "90", "50"), item("CEMENT 50 KG", "C2", "91", "50")));
        var r = service.report(TENANT, CLERK);
        var g = r.groups().stream().filter(x -> x.kind().equals("POSSIBLE_DUPLICATE")).findFirst().orElseThrow();
        assertThat(g.count()).isEqualTo(2);
        assertThat(g.items().get(0).detail()).contains("Looks like");
    }
}
