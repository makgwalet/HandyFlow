package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import za.co.handyflow.platform.catalogue.dto.BundleLineRequest;
import za.co.handyflow.platform.catalogue.dto.BundleRequest;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueBundleServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller SEES_PRICES = new Caller(UUID.randomUUID(), true, false, false);
    private static final Caller NO_PRICES = new Caller(UUID.randomUUID(), false, false, false);

    @Mock CatalogueBundleComponentRepository components;
    @Mock CatalogueItemRepository items;
    @Mock CatalogueAuditEventRepository audit;
    CatalogueBundleService service;
    CatalogueItem pack;
    CatalogueItem tap;
    CatalogueItem pipe;

    private static CatalogueItem make(String name, CatalogueItemType type, String price, String cost) {
        return CatalogueItem.create(TENANT, null, new CatalogueItem.Details(name, null, name.substring(0, 3).toUpperCase(), null, null, type, "each",
                price == null ? null : new BigDecimal(price), cost == null ? null : new BigDecimal(cost), new BigDecimal("15.00"), false), CatalogueItemStatus.ACTIVE);
    }

    @BeforeEach
    void setUp() {
        service = new CatalogueBundleService(components, items, audit);
        pack = make("Bathroom kit", CatalogueItemType.PACKAGE, "200.00", null);
        tap = make("Tap", CatalogueItemType.PRODUCT, "100.00", "60.00");
        pipe = make("Pipe", CatalogueItemType.PRODUCT, "25.50", "10.00");
        for (var i : List.of(pack, tap, pipe)) when(items.findActiveById(TENANT, i.getId())).thenReturn(Optional.of(i));
        when(items.findLiveByIds(any(), any())).thenAnswer(inv -> {
            java.util.Collection<UUID> ids = inv.getArgument(1);
            return List.of(pack, tap, pipe).stream().filter(i -> ids.contains(i.getId())).toList();
        });
        when(items.parentIdsWithVariants(TENANT)).thenReturn(List.of());
        when(components.save(any())).thenAnswer(i -> i.getArgument(0));
        when(components.forBundle(TENANT, pack.getId())).thenReturn(List.of(
                CatalogueBundleComponent.of(TENANT, pack.getId(), tap.getId(), new BigDecimal("2.000"), 0),
                CatalogueBundleComponent.of(TENANT, pack.getId(), pipe.getId(), new BigDecimal("1.000"), 1)));
    }

    @Test
    void replaceStoresTheListAndShowsWhatThePartsAreWorth() {
        var out = service.replace(TENANT, pack.getId(), new BundleRequest(List.of(
                new BundleLineRequest(tap.getId(), new BigDecimal("2")), new BundleLineRequest(pipe.getId(), BigDecimal.ONE))), SEES_PRICES);
        verify(components).deleteForBundle(TENANT, pack.getId());
        verify(audit).save(any());
        assertThat(out.partsTotal().toPlainString()).isEqualTo("225.50");
        assertThat(out.partsCost().toPlainString()).isEqualTo("130.00");
        assertThat(out.saving().toPlainString()).isEqualTo("25.50");
        assertThat(out.components().size()).isEqualTo(2);
    }

    @Test
    void peopleWhoCannotSeePricesGetNoMoney() {
        var out = service.view(TENANT, pack.getId(), NO_PRICES);
        assertThat(out.price()).isNull();
        assertThat(out.partsTotal()).isNull();
        assertThat(out.partsCost()).isNull();
        assertThat(out.saving()).isNull();
        assertThat(out.components().get(0).unitPrice()).isNull();
        assertThat(out.components().get(0).name()).isEqualTo("Tap");
    }

    @Test
    void aNonPackageCannotHaveParts() {
        assertThatThrownBy(() -> service.replace(TENANT, tap.getId(), new BundleRequest(List.of(new BundleLineRequest(pipe.getId(), BigDecimal.ONE))), SEES_PRICES))
                .hasMessageContaining("Package");
        verify(components, never()).deleteForBundle(any(), any());
    }

    @Test
    void aBadLineRefusesTheWholeChangeBeforeAnythingIsDeleted() {
        assertThatThrownBy(() -> service.replace(TENANT, pack.getId(), new BundleRequest(List.of(
                new BundleLineRequest(tap.getId(), BigDecimal.ONE), new BundleLineRequest(pipe.getId(), BigDecimal.ZERO))), SEES_PRICES))
                .hasMessageContaining("more than zero");
        assertThatThrownBy(() -> service.replace(TENANT, pack.getId(), new BundleRequest(List.of(new BundleLineRequest(UUID.randomUUID(), BigDecimal.ONE))), SEES_PRICES))
                .hasMessageContaining("no longer exists");
        verify(components, never()).deleteForBundle(any(), any());
    }

    @Test
    void anItemWithVariantsCannotBeAPart() {
        when(items.parentIdsWithVariants(TENANT)).thenReturn(List.of(tap.getId()));
        assertThatThrownBy(() -> service.replace(TENANT, pack.getId(), new BundleRequest(List.of(new BundleLineRequest(tap.getId(), BigDecimal.ONE))), SEES_PRICES))
                .hasMessageContaining("variant");
    }

    @Test
    void usedInListsThePackagesThatHoldAnItem() {
        when(components.usedBy(TENANT, tap.getId())).thenReturn(List.of(CatalogueBundleComponent.of(TENANT, pack.getId(), tap.getId(), new BigDecimal("2.000"), 0)));
        var out = service.usedIn(TENANT, tap.getId());
        assertThat(out.size()).isEqualTo(1);
        assertThat(out.get(0).name()).isEqualTo("Bathroom kit");
        assertThat(out.get(0).quantity().toPlainString()).isEqualTo("2.000");
    }
}
