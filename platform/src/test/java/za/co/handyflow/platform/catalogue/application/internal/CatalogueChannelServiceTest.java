package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.ChannelRequest;
import za.co.handyflow.platform.catalogue.dto.PublishRequest;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueChannelServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller USER = new Caller(UUID.randomUUID(), false, false, false);

    @Mock CatalogueChannelRepository channels;
    @Mock CatalogueItemChannelRepository publications;
    @Mock CatalogueItemRepository items;
    @Mock CatalogueItemMediaRepository media;
    @Mock CataloguePriceListRepository lists;
    @Mock CatalogueAuditEventRepository audit;
    @Mock CataloguePricingService pricing;
    CatalogueChannelService service;
    CatalogueChannel web;
    CatalogueChannel till;
    CatalogueItem cement;
    CatalogueItem draft;

    private static CatalogueItem make(String name, String sku, String desc, CatalogueItemStatus status) {
        return CatalogueItem.create(TENANT, null, new CatalogueItem.Details(name, desc, sku, null, null, CatalogueItemType.PRODUCT, "each",
                new BigDecimal("89.90"), null, new BigDecimal("15.00"), false), status);
    }

    @BeforeEach
    void setUp() {
        service = new CatalogueChannelService(channels, publications, items, media, lists, audit, pricing);
        web = CatalogueChannel.create(TENANT, "web-shop", "Web shop", ChannelKind.WEB, null);
        till = CatalogueChannel.create(TENANT, "till", "Till", ChannelKind.POS, null);
        cement = make("Cement", "CEM-1", "Grey cement", CatalogueItemStatus.ACTIVE);
        draft = make("Sand", "SND-1", "Sand", CatalogueItemStatus.DRAFT);
        for (var c : List.of(web, till)) when(channels.findInTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(channels.findByCode(TENANT, "web-shop")).thenReturn(Optional.of(web));
        when(channels.forTenant(TENANT)).thenReturn(List.of(till, web));
        when(items.findLiveByIds(any(), anyCollection())).thenAnswer(inv -> {
            java.util.Collection<UUID> ids = inv.getArgument(1);
            return List.of(cement, draft).stream().filter(i -> ids.contains(i.getId())).toList();
        });
        when(items.parentIdsWithVariants(TENANT)).thenReturn(List.of());
        when(pricing.pricesOnList(eq(TENANT), any(), anyCollection())).thenAnswer(inv -> {
            java.util.Collection<CatalogueItem> in = inv.getArgument(2);
            return in.stream().collect(java.util.stream.Collectors.toMap(CatalogueItem::getId, CatalogueItem::getDefaultPrice));
        });
        when(media.itemIdsWithMedia(TENANT, MediaRules.Kind.IMAGE)).thenReturn(List.of(cement.getId()));
        when(publications.onChannel(any(), any(), anyCollection())).thenReturn(List.of());
        when(channels.save(any())).thenAnswer(i -> i.getArgument(0));
        when(publications.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void createChecksTheCodeIsFreeAndTheListExists() {
        when(channels.findByCode(TENANT, "pos-2")).thenReturn(Optional.empty());
        var out = service.create(TENANT, new ChannelRequest(" POS-2 ", "Second till", "pos", null, null), USER);
        assertThat(out.code()).isEqualTo("pos-2");
        assertThat(out.active()).isTrue();
        assertThatThrownBy(() -> service.create(TENANT, new ChannelRequest("web-shop", "Again", "web", null, null), USER)).isInstanceOf(ConflictException.class);
        var listId = UUID.randomUUID();
        when(lists.findLiveById(TENANT, listId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(TENANT, new ChannelRequest("shop-3", "Shop 3", "web", listId, null), USER)).hasMessageContaining("price list does not exist");
    }

    @Test
    void createStopsAtTheChannelLimit() {
        when(channels.countForTenant(TENANT)).thenReturn((long) ChannelRules.MAX_CHANNELS);
        assertThatThrownBy(() -> service.create(TENANT, new ChannelRequest("extra", "Extra", "web", null, null), USER)).hasMessageContaining("at most 20");
    }

    @Test
    void theCodeCannotBeChanged() {
        assertThatThrownBy(() -> service.update(TENANT, web.getId(), new ChannelRequest("renamed", "Web shop", "web", null, true), USER)).hasMessageContaining("cannot be changed");
        var out = service.update(TENANT, web.getId(), new ChannelRequest("web-shop", "Online", "web", null, false), USER);
        assertThat(out.name()).isEqualTo("Online");
        assertThat(out.active()).isFalse();
    }

    @Test
    void aChannelWithPublishedItemsCannotBeDeleted() {
        when(publications.countOnChannel(TENANT, web.getId())).thenReturn(3L);
        assertThatThrownBy(() -> service.delete(TENANT, web.getId(), USER)).isInstanceOf(ConflictException.class).hasMessageContaining("3 items");
        verify(channels, never()).delete(any());
        service.delete(TENANT, till.getId(), USER);
        verify(channels).delete(till);
    }

    @Test
    void publishPublishesWhatPassesAndExplainsTheRest() {
        var out = service.publish(TENANT, web.getId(), new PublishRequest(List.of(cement.getId(), draft.getId(), UUID.randomUUID()), false), USER);
        assertThat(out.done()).isEqualTo(1);
        assertThat(out.blocked()).isEqualTo(2);
        assertThat(out.rows().get(0).outcome()).isEqualTo("PUBLISHED");
        assertThat(out.rows().get(1).outcome()).isEqualTo("BLOCKED");
        assertThat(out.rows().get(1).problems()).contains("Only active items can be published.");
        assertThat(out.rows().get(1).problems()).contains("It needs a picture.");
        assertThat(out.rows().get(2).problems()).contains("This item no longer exists.");
        var saved = ArgumentCaptor.forClass(CatalogueItemChannel.class);
        verify(publications).save(saved.capture());
        assertThat(saved.getValue().getItemId()).isEqualTo(cement.getId());
        assertThat(saved.getValue().getChannelId()).isEqualTo(web.getId());
        var event = ArgumentCaptor.forClass(CatalogueAuditEvent.class);
        verify(audit).save(event.capture());
        assertThat(event.getValue().getAction()).isEqualTo(CatalogueAuditAction.PUBLISHED);
        assertThat(event.getValue().getEntityType()).isEqualTo("ITEM");
    }

    @Test
    void aDryRunChangesNothing() {
        var out = service.publish(TENANT, web.getId(), new PublishRequest(List.of(cement.getId()), true), USER);
        assertThat(out.rows().get(0).outcome()).isEqualTo("WOULD_PUBLISH");
        verify(publications, never()).save(any());
        verify(audit, never()).save(any());
    }

    @Test
    void anItemAlreadyThereIsLeftAlone() {
        when(publications.onChannel(eq(TENANT), eq(web.getId()), anyCollection())).thenReturn(List.of(CatalogueItemChannel.of(TENANT, cement.getId(), web.getId(), null)));
        var out = service.publish(TENANT, web.getId(), new PublishRequest(List.of(cement.getId()), false), USER);
        assertThat(out.unchanged()).isEqualTo(1);
        assertThat(out.rows().get(0).outcome()).isEqualTo("ALREADY");
        verify(publications, never()).save(any());
    }

    @Test
    void aSwitchedOffChannelTakesNoNewItems() {
        web.update("Web shop", ChannelKind.WEB, null, false);
        assertThatThrownBy(() -> service.publish(TENANT, web.getId(), new PublishRequest(List.of(cement.getId()), false), USER)).hasMessageContaining("switched off");
    }

    @Test
    void theListOfItemsIsCheckedBeforeAnythingElse() {
        assertThatThrownBy(() -> service.publish(TENANT, web.getId(), new PublishRequest(List.of(), false), USER)).hasMessageContaining("at least one item");
        assertThatThrownBy(() -> service.publish(TENANT, web.getId(), null, USER)).isInstanceOf(IllegalArgumentException.class);
        var many = java.util.stream.IntStream.range(0, 201).mapToObj(i -> UUID.randomUUID()).toList();
        assertThatThrownBy(() -> service.publish(TENANT, web.getId(), new PublishRequest(many, false), USER)).hasMessageContaining("at most 200");
        assertThatThrownBy(() -> service.publish(TENANT, UUID.randomUUID(), new PublishRequest(List.of(cement.getId()), false), USER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unpublishRemovesAndRecords() {
        var pub = CatalogueItemChannel.of(TENANT, cement.getId(), web.getId(), null);
        when(publications.onChannel(eq(TENANT), eq(web.getId()), anyCollection())).thenReturn(List.of(pub));
        var out = service.unpublish(TENANT, web.getId(), new PublishRequest(List.of(cement.getId(), draft.getId()), false), USER);
        assertThat(out.done()).isEqualTo(1);
        assertThat(out.unchanged()).isEqualTo(1);
        assertThat(out.rows().get(1).outcome()).isEqualTo("NOT_PUBLISHED");
        verify(publications).delete(pub);
    }

    @Test
    void anItemShowsEveryChannelWithReasonsWhereItCannotGo() {
        when(items.findActiveById(TENANT, cement.getId())).thenReturn(Optional.of(cement));
        when(publications.forItem(TENANT, cement.getId())).thenReturn(List.of(CatalogueItemChannel.of(TENANT, cement.getId(), web.getId(), null)));
        var out = service.forItem(TENANT, cement.getId());
        assertThat(out.size()).isEqualTo(2);
        var onWeb = out.stream().filter(c -> c.code().equals("web-shop")).findFirst().orElseThrow();
        assertThat(onWeb.published()).isTrue();
        assertThat(onWeb.problems()).isEmpty();
        var onTill = out.stream().filter(c -> c.code().equals("till")).findFirst().orElseThrow();
        assertThat(onTill.published()).isFalse();
        assertThat(onTill.problems()).isEmpty();   // SKU present, active, priced: ready for the till
    }

    @Test
    void theFeedHasPricesAndNoCost() {
        var withCost = CatalogueItem.create(TENANT, null, new CatalogueItem.Details("Cement", "Grey cement", "CEM-1", null, "PPC", CatalogueItemType.PRODUCT, "each",
                new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("15.00"), false), CatalogueItemStatus.ACTIVE);
        when(items.publishedTo(eq(TENANT), eq(web.getId()), any())).thenReturn(List.of(withCost));
        when(items.countPublishedTo(TENANT, web.getId())).thenReturn(1L);
        when(items.variantsOf(eq(TENANT), anyCollection())).thenReturn(List.of());
        when(media.itemIdsWithMedia(TENANT, MediaRules.Kind.IMAGE)).thenReturn(List.of(withCost.getId()));
        var out = service.feed(TENANT, "Web-Shop", 0, 100);
        assertThat(out.total()).isEqualTo(1L);
        var e = out.items().get(0);
        assertThat(e.price().toPlainString()).isEqualTo("100.00");
        assertThat(e.priceInclVat().toPlainString()).isEqualTo("115.00");
        assertThat(e.pictureUrl()).isEqualTo("/api/v1/catalogue/items/" + withCost.getId() + "/media");
        assertThat(e.toString()).doesNotContain("60.00");
    }

    @Test
    void aSwitchedOffOrUnknownChannelHasNoFeed() {
        assertThatThrownBy(() -> service.feed(TENANT, "nope", 0, 10)).isInstanceOf(ResourceNotFoundException.class);
        web.update("Web shop", ChannelKind.WEB, null, false);
        assertThatThrownBy(() -> service.feed(TENANT, "web-shop", 0, 10)).hasMessageContaining("switched off");
    }

    @Test
    void publishedItemsShowTheirStatusAndAMarkerWhenDeleted() {
        var gone = UUID.randomUUID();
        when(publications.pageOnChannel(eq(TENANT), eq(web.getId()), any())).thenReturn(List.of(
                CatalogueItemChannel.of(TENANT, draft.getId(), web.getId(), null), CatalogueItemChannel.of(TENANT, gone, web.getId(), null)));
        var out = service.published(TENANT, web.getId(), 0, 50);
        assertThat(out.get(0).status()).isEqualTo("DRAFT");
        assertThat(out.get(0).name()).isEqualTo("Sand");
        assertThat(out.get(1).status()).isEqualTo("DELETED");
        assertThat(out.get(1).name()).isNull();
    }
}
