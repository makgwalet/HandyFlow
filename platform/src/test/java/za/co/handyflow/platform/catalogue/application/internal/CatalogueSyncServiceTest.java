package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueSyncServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final Caller WITH_PRICES = new Caller(UUID.randomUUID(), true, false, false);
    private static final Caller NO_PRICES = new Caller(UUID.randomUUID(), false, false, false);

    @Mock CatalogueAuditEventRepository audit;
    @Mock CatalogueItemRepository items;
    CatalogueSyncService service;
    CatalogueItem cement;

    private static CatalogueAuditEvent event(UUID itemId, CatalogueAuditAction action, long seq) {
        var e = CatalogueAuditEvent.of(TENANT, "ITEM", itemId, "x", action, null, null);
        ReflectionTestUtils.setField(e, "seq", seq);
        return e;
    }

    @BeforeEach
    void setUp() {
        service = new CatalogueSyncService(audit, items);
        service.useClock(Clock.fixed(NOW, ZoneOffset.UTC));
        cement = CatalogueItem.create(TENANT, null, new CatalogueItem.Details("Cement", null, "CEM-1", null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal("89.90"), new BigDecimal("50.00"), new BigDecimal("15.00"), false), CatalogueItemStatus.ACTIVE);
        when(items.findLiveByIds(any(), anyCollection())).thenAnswer(inv -> {
            java.util.Collection<UUID> ids = inv.getArgument(1);
            return ids.contains(cement.getId()) ? List.of(cement) : List.of();
        });
    }

    @Test
    void nothingNewKeepsTheCursor() {
        when(audit.itemChangesAfter(eq(TENANT), eq(7L), any(), any())).thenReturn(List.of());
        var out = service.changes(TENANT, 7L, null, WITH_PRICES);
        assertThat(out.cursor()).isEqualTo(7L);
        assertThat(out.hasMore()).isFalse();
        assertThat(out.changes()).isEmpty();
    }

    @Test
    void anItemChangedManyTimesAppearsOnceWithItsLatestState() {
        when(audit.itemChangesAfter(eq(TENANT), eq(0L), any(), any())).thenReturn(List.of(
                event(cement.getId(), CatalogueAuditAction.CREATED, 1), event(cement.getId(), CatalogueAuditAction.PRICE_CHANGED, 2), event(cement.getId(), CatalogueAuditAction.MEDIA_ADDED, 3)));
        var out = service.changes(TENANT, null, null, WITH_PRICES);
        assertThat(out.changes().size()).isEqualTo(1);
        assertThat(out.changes().get(0).change()).isEqualTo("UPSERT");
        assertThat(out.changes().get(0).action()).isEqualTo("MEDIA_ADDED");
        assertThat(out.changes().get(0).item().sku()).isEqualTo("CEM-1");
        assertThat(out.cursor()).isEqualTo(3L);
    }

    @Test
    void anItemThatNoLongerExistsComesBackAsADelete() {
        var gone = UUID.randomUUID();
        when(audit.itemChangesAfter(eq(TENANT), eq(0L), any(), any())).thenReturn(List.of(event(gone, CatalogueAuditAction.DELETED, 9)));
        var out = service.changes(TENANT, 0L, null, WITH_PRICES);
        assertThat(out.changes().get(0).change()).isEqualTo("DELETE");
        assertThat(out.changes().get(0).item()).isNull();
        assertThat(out.cursor()).isEqualTo(9L);
    }

    @Test
    void pricesAreLeftOutForPeopleWhoMayNotSeeThem() {
        when(audit.itemChangesAfter(eq(TENANT), eq(0L), any(), any())).thenReturn(List.of(event(cement.getId(), CatalogueAuditAction.UPDATED, 1)));
        assertThat(service.changes(TENANT, 0L, null, NO_PRICES).changes().get(0).item().price()).isNull();
        assertThat(service.changes(TENANT, 0L, null, WITH_PRICES).changes().get(0).item().price().toPlainString()).isEqualTo("89.90");
    }

    @Test
    void aFullPageSaysThereIsMoreAndTheLimitIsCapped() {
        when(audit.itemChangesAfter(eq(TENANT), anyLong(), any(), any())).thenReturn(List.of(event(cement.getId(), CatalogueAuditAction.UPDATED, 1), event(cement.getId(), CatalogueAuditAction.UPDATED, 2)));
        assertThat(service.changes(TENANT, 0L, 2, WITH_PRICES).hasMore()).isTrue();
        assertThat(service.changes(TENANT, 0L, 3, WITH_PRICES).hasMore()).isFalse();
        service.changes(TENANT, 0L, 100000, WITH_PRICES);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(audit, org.mockito.Mockito.atLeastOnce()).itemChangesAfter(eq(TENANT), anyLong(), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(500);
    }

    @Test
    void recentChangesAreHeldBackForAFewSeconds() {
        service.changes(TENANT, 0L, null, WITH_PRICES);
        var before = ArgumentCaptor.forClass(Instant.class);
        verify(audit).itemChangesAfter(eq(TENANT), eq(0L), before.capture(), any());
        assertThat(before.getValue()).isEqualTo(NOW.minusSeconds(5));
    }

    @Test
    void aBadCursorOrLimitIsRefused() {
        assertThatThrownBy(() -> service.changes(TENANT, -1L, null, WITH_PRICES)).hasMessageContaining("negative");
        assertThatThrownBy(() -> service.changes(TENANT, 0L, 0, WITH_PRICES)).hasMessageContaining("at least 1");
    }
}
