package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Channels and publishing. A channel is a place items are sold or listed; publishing an item to it makes it part of the
 * channel's feed. What an item needs first is decided by {@link ChannelRules}. Publishing is item by item: the items that
 * pass are published, the others come back with their reasons, and nothing is half done. A channel with items published to
 * it is switched off rather than deleted. Cost is never part of a feed.
 */
@Service
@RequiredArgsConstructor
public class CatalogueChannelService {

    private final CatalogueChannelRepository channelRepository;
    private final CatalogueItemChannelRepository publicationRepository;
    private final CatalogueItemRepository itemRepository;
    private final CatalogueItemMediaRepository mediaRepository;
    private final CataloguePriceListRepository listRepository;
    private final CatalogueAuditEventRepository auditRepository;
    private final CataloguePricingService pricing;

    // ── Channels ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChannelResponse> channels(TenantId tenantId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : publicationRepository.countsByChannel(tenantId)) counts.put((UUID) row[0], ((Number) row[1]).longValue());
        Map<UUID, String> lists = listNames(tenantId);
        return channelRepository.forTenant(tenantId).stream().map(c -> toResponse(c, lists, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional
    public ChannelResponse create(TenantId tenantId, ChannelRequest r, Caller caller) {
        if (r == null) throw new IllegalArgumentException("Fill in the channel details.");
        String code = ChannelRules.code(r.code());
        String name = ChannelRules.name(r.name());
        ChannelKind kind = ChannelRules.kind(r.kind());
        if (channelRepository.countForTenant(tenantId) >= ChannelRules.MAX_CHANNELS) throw new IllegalArgumentException("You can have at most " + ChannelRules.MAX_CHANNELS + " channels.");
        if (channelRepository.findByCode(tenantId, code).isPresent()) throw new ConflictException("A channel with the code '" + code + "' already exists.");
        checkList(tenantId, r.priceListId());
        var c = channelRepository.save(CatalogueChannel.create(tenantId, code, name, kind, r.priceListId()));
        if (Boolean.FALSE.equals(r.active())) { c.update(name, kind, r.priceListId(), false); channelRepository.save(c); }
        audit(tenantId, c, "Channel created", caller);
        return toResponse(c, listNames(tenantId), 0);
    }

    @Transactional
    public ChannelResponse update(TenantId tenantId, UUID id, ChannelRequest r, Caller caller) {
        var c = load(tenantId, id);
        if (r == null) throw new IllegalArgumentException("Fill in the channel details.");
        if (r.code() != null && !r.code().isBlank() && !ChannelRules.code(r.code()).equals(c.getCode())) {
            throw new IllegalArgumentException("The code cannot be changed, because other systems use it. Create a new channel instead.");
        }
        checkList(tenantId, r.priceListId());
        c.update(ChannelRules.name(r.name()), ChannelRules.kind(r.kind()), r.priceListId(), r.active() == null || r.active());
        channelRepository.save(c);
        audit(tenantId, c, c.isActive() ? "Channel changed" : "Channel switched off", caller);
        return toResponse(c, listNames(tenantId), publicationRepository.countOnChannel(tenantId, id));
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id, Caller caller) {
        var c = load(tenantId, id);
        long n = publicationRepository.countOnChannel(tenantId, id);
        if (n > 0) throw new ConflictException("This channel has " + n + (n == 1 ? " item" : " items") + " published to it. Switch it off instead, or unpublish them first.");
        channelRepository.delete(c);
        audit(tenantId, c, "Channel deleted", caller);
    }

    // ── Publishing ───────────────────────────────────────────────────────────

    /** Publishes the items that pass the channel's rules. A dry run changes nothing and says what would happen. */
    @Transactional
    public PublishResponse publish(TenantId tenantId, UUID channelId, PublishRequest r, Caller caller) {
        var channel = load(tenantId, channelId);
        var ids = ids(r);
        if (!channel.isActive() && !r.dryRun()) throw new IllegalStateException("This channel is switched off. Switch it on before publishing to it.");
        Map<UUID, CatalogueItem> items = itemRepository.findLiveByIds(tenantId, ids).stream().collect(Collectors.toMap(CatalogueItem::getId, Function.identity()));
        Set<UUID> already = publicationRepository.onChannel(tenantId, channelId, ids).stream().map(CatalogueItemChannel::getItemId).collect(Collectors.toSet());
        Map<UUID, List<String>> problems = problems(tenantId, channel, items.values());

        List<PublishResponse.Row> rows = new ArrayList<>();
        int done = 0, unchanged = 0, blocked = 0;
        for (UUID id : ids) {
            var item = items.get(id);
            if (item == null) { rows.add(new PublishResponse.Row(id, null, null, "BLOCKED", List.of("This item no longer exists."))); blocked++; continue; }
            if (already.contains(id)) { rows.add(row(item, "ALREADY", List.of())); unchanged++; continue; }
            var p = problems.getOrDefault(id, List.of());
            if (!p.isEmpty()) { rows.add(row(item, "BLOCKED", p)); blocked++; continue; }
            if (!r.dryRun()) {
                publicationRepository.save(CatalogueItemChannel.of(tenantId, id, channelId, caller.userId()));
                auditRepository.save(CatalogueAuditEvent.of(tenantId, "ITEM", id, item.getName(), CatalogueAuditAction.PUBLISHED, "Published to " + channel.getName(), caller.userId()));
            }
            rows.add(row(item, r.dryRun() ? "WOULD_PUBLISH" : "PUBLISHED", List.of()));
            done++;
        }
        return new PublishResponse(channelId, r.dryRun(), done, unchanged, blocked, rows);
    }

    @Transactional
    public PublishResponse unpublish(TenantId tenantId, UUID channelId, PublishRequest r, Caller caller) {
        var channel = load(tenantId, channelId);
        var ids = ids(r);
        Map<UUID, CatalogueItem> items = itemRepository.findLiveByIds(tenantId, ids).stream().collect(Collectors.toMap(CatalogueItem::getId, Function.identity()));
        Map<UUID, CatalogueItemChannel> there = publicationRepository.onChannel(tenantId, channelId, ids).stream().collect(Collectors.toMap(CatalogueItemChannel::getItemId, Function.identity()));
        List<PublishResponse.Row> rows = new ArrayList<>();
        int done = 0, unchanged = 0;
        for (UUID id : ids) {
            var item = items.get(id);
            var pub = there.get(id);
            if (pub == null) { rows.add(item == null ? new PublishResponse.Row(id, null, null, "NOT_PUBLISHED", List.of()) : row(item, "NOT_PUBLISHED", List.of())); unchanged++; continue; }
            if (!r.dryRun()) {
                publicationRepository.delete(pub);
                auditRepository.save(CatalogueAuditEvent.of(tenantId, "ITEM", id, item == null ? null : item.getName(), CatalogueAuditAction.UNPUBLISHED, "Removed from " + channel.getName(), caller.userId()));
            }
            rows.add(item == null ? new PublishResponse.Row(id, null, null, "UNPUBLISHED", List.of()) : row(item, r.dryRun() ? "WOULD_UNPUBLISH" : "UNPUBLISHED", List.of()));
            done++;
        }
        return new PublishResponse(channelId, r.dryRun(), done, unchanged, 0, rows);
    }

    /** What is published to a channel, newest first, whatever the items' status is now. */
    @Transactional(readOnly = true)
    public List<ChannelItemResponse> published(TenantId tenantId, UUID channelId, int page, int size) {
        load(tenantId, channelId);
        var rows = publicationRepository.pageOnChannel(tenantId, channelId, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200))));
        Map<UUID, CatalogueItem> items = rows.isEmpty() ? Map.of() : itemRepository.findLiveByIds(tenantId, rows.stream().map(CatalogueItemChannel::getItemId).toList())
                .stream().collect(Collectors.toMap(CatalogueItem::getId, Function.identity()));
        return rows.stream().map(x -> {
            var i = items.get(x.getItemId());
            return new ChannelItemResponse(x.getItemId(), i == null ? null : i.getName(), i == null ? null : i.getSku(), i == null ? "DELETED" : i.getStatus().name(), x.getPublishedAt());
        }).toList();
    }

    /** Every channel from one item's point of view. */
    @Transactional(readOnly = true)
    public List<ItemChannelResponse> forItem(TenantId tenantId, UUID itemId) {
        var item = itemRepository.findActiveById(tenantId, itemId).orElseThrow(() -> new ResourceNotFoundException("CatalogueItem", itemId.toString()));
        Map<UUID, CatalogueItemChannel> pubs = publicationRepository.forItem(tenantId, itemId).stream().collect(Collectors.toMap(CatalogueItemChannel::getChannelId, Function.identity()));
        List<ItemChannelResponse> out = new ArrayList<>();
        for (var c : channelRepository.forTenant(tenantId)) {
            var pub = pubs.get(c.getId());
            List<String> p = pub != null ? List.of() : problems(tenantId, c, List.of(item)).getOrDefault(itemId, List.of());
            out.add(new ItemChannelResponse(c.getId(), c.getCode(), c.getName(), c.getKind().name(), c.isActive(), pub != null, pub == null ? null : pub.getPublishedAt(), p));
        }
        return out;
    }

    // ── Feed ─────────────────────────────────────────────────────────────────

    /** The channel's catalogue: active items published to it, with variants, priced from its list. Cost is never shown. */
    @Transactional(readOnly = true)
    public FeedResponse feed(TenantId tenantId, String code, int page, int size) {
        var channel = channelRepository.findByCode(tenantId, code == null ? "" : code.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new ResourceNotFoundException("CatalogueChannel", String.valueOf(code)));
        if (!channel.isActive()) throw new IllegalStateException("This channel is switched off.");
        int s = Math.max(1, Math.min(size, 200));
        int p = Math.max(0, page);
        var items = itemRepository.publishedTo(tenantId, channel.getId(), PageRequest.of(p, s));
        long total = itemRepository.countPublishedTo(tenantId, channel.getId());
        Map<UUID, List<CatalogueItem>> variants = items.isEmpty() ? Map.of() : itemRepository.variantsOf(tenantId, items.stream().map(CatalogueItem::getId).toList())
                .stream().filter(v -> v.getStatus() == CatalogueItemStatus.ACTIVE).collect(Collectors.groupingBy(CatalogueItem::getParentId));
        List<CatalogueItem> all = new ArrayList<>(items);
        variants.values().forEach(all::addAll);
        Map<UUID, BigDecimal> prices = pricing.pricesOnList(tenantId, channel.getPriceListId(), all);
        Set<UUID> pictured = new HashSet<>(mediaRepository.itemIdsWithMedia(tenantId, MediaRules.Kind.IMAGE));

        List<FeedResponse.Entry> out = new ArrayList<>();
        for (var i : items) {
            List<FeedResponse.Variant> vs = variants.getOrDefault(i.getId(), List.of()).stream()
                    .map(v -> new FeedResponse.Variant(v.getId(), v.getSku(), v.getBarcode(), v.getVariantLabel(), prices.get(v.getId()), withVat(prices.get(v.getId()), v)))
                    .toList();
            BigDecimal price = prices.get(i.getId());
            out.add(new FeedResponse.Entry(i.getId(), i.getSku(), i.getBarcode(), i.getName(), i.getDescription(), i.getBrand(),
                    i.getCategory() == null ? null : i.getCategory().getName(), i.getUnit(), i.getVatRate(), i.isVatExempt(),
                    vs.isEmpty() ? price : null, vs.isEmpty() ? withVat(price, i) : null,
                    pictured.contains(i.getId()) ? "/api/v1/catalogue/items/" + i.getId() + "/media" : null, i.getUpdatedAt(), vs));
        }
        String listName = channel.getPriceListId() == null ? null : listNames(tenantId).get(channel.getPriceListId());
        return new FeedResponse(channel.getCode(), channel.getKind().name(), listName, p, s, total, out);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal withVat(BigDecimal price, CatalogueItem i) { return PricingRules.withVat(price, i.getVatRate(), i.isVatExempt()); }

    /** Why each item cannot go to this channel (items that can go are absent from the map). */
    private Map<UUID, List<String>> problems(TenantId tenantId, CatalogueChannel channel, Collection<CatalogueItem> items) {
        if (items.isEmpty()) return Map.of();
        Map<UUID, BigDecimal> prices = pricing.pricesOnList(tenantId, channel.getPriceListId(), items);
        Set<UUID> pictured = new HashSet<>(mediaRepository.itemIdsWithMedia(tenantId, MediaRules.Kind.IMAGE));
        Set<UUID> parents = new HashSet<>(itemRepository.parentIdsWithVariants(tenantId));
        var mine = items.stream().map(CatalogueItem::getId).filter(parents::contains).toList();
        Map<UUID, Long> activeVariants = mine.isEmpty() ? Map.of() : itemRepository.variantsOf(tenantId, mine).stream()
                .filter(v -> v.getStatus() == CatalogueItemStatus.ACTIVE).collect(Collectors.groupingBy(CatalogueItem::getParentId, Collectors.counting()));
        Map<UUID, List<String>> out = new HashMap<>();
        for (var i : items) {
            var f = new ChannelRules.Facts(i.getStatus(), i.isVariant(), activeVariants.getOrDefault(i.getId(), 0L).intValue(), prices.get(i.getId()),
                    i.getName(), i.getDescription(), i.getSku(), i.getBarcode(), pictured.contains(i.getId()));
            var p = ChannelRules.problems(channel.getKind(), f);
            if (!p.isEmpty()) out.put(i.getId(), p);
        }
        return out;
    }

    private static List<UUID> ids(PublishRequest r) {
        if (r == null || r.itemIds() == null || r.itemIds().isEmpty()) throw new IllegalArgumentException("Choose at least one item.");
        var ids = r.itemIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new IllegalArgumentException("Choose at least one item.");
        if (ids.size() > ChannelRules.MAX_ITEMS_PER_CALL) throw new IllegalArgumentException("Choose at most " + ChannelRules.MAX_ITEMS_PER_CALL + " items at a time.");
        return ids;
    }

    private static PublishResponse.Row row(CatalogueItem i, String outcome, List<String> problems) {
        return new PublishResponse.Row(i.getId(), i.getName(), i.getSku(), outcome, problems);
    }

    private void checkList(TenantId tenantId, UUID listId) {
        if (listId != null && listRepository.findLiveById(tenantId, listId).isEmpty()) throw new IllegalArgumentException("That price list does not exist.");
    }

    private Map<UUID, String> listNames(TenantId tenantId) {
        Map<UUID, String> m = new HashMap<>();
        listRepository.findAllLive(tenantId).forEach(l -> m.put(l.getId(), l.getName()));
        return m;
    }

    private CatalogueChannel load(TenantId tenantId, UUID id) {
        return channelRepository.findInTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("CatalogueChannel", id.toString()));
    }

    private void audit(TenantId tenantId, CatalogueChannel c, String detail, Caller caller) {
        auditRepository.save(CatalogueAuditEvent.of(tenantId, "CHANNEL", c.getId(), c.getName(), CatalogueAuditAction.CHANNEL_CHANGED, detail, caller.userId()));
    }

    private static ChannelResponse toResponse(CatalogueChannel c, Map<UUID, String> lists, long published) {
        return new ChannelResponse(c.getId(), c.getCode(), c.getName(), c.getKind().name(), c.getPriceListId(),
                c.getPriceListId() == null ? null : lists.get(c.getPriceListId()), c.isActive(), published);
    }
}
