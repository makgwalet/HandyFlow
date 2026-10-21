package za.co.handyflow.platform.catalogue.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueAuditEvent;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItem;
import za.co.handyflow.platform.catalogue.domain.model.ChannelRules;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueAuditEventRepository;
import za.co.handyflow.platform.catalogue.domain.repository.CatalogueItemRepository;
import za.co.handyflow.platform.catalogue.dto.ChangesResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The change feed (delta API) for systems that keep a copy of the catalogue. Ask with the cursor you were last given
 * ({@code 0} the first time, which returns everything) and get each changed item once, with its latest state, or as a
 * delete. Changes are held back for a few seconds so a save that is still finishing is not skipped. It is a feed of what
 * the catalogue's history recorded: anything that changed an item wrote a history line, so it appears here.
 * A consumer should still do a full pass now and then (cursor 0) to repair any copy that drifted.
 */
@Service
@RequiredArgsConstructor
public class CatalogueSyncService {

    /** How long a history line must be old before the feed shows it. */
    static final Duration SETTLE = Duration.ofSeconds(5);

    private final CatalogueAuditEventRepository auditRepository;
    private final CatalogueItemRepository itemRepository;
    private Clock clock = Clock.systemUTC();

    void useClock(Clock clock) { this.clock = clock; }

    @Transactional(readOnly = true)
    public ChangesResponse changes(TenantId tenantId, Long since, Integer limit, Caller caller) {
        long cursor = ChannelRules.cursor(since);
        int n = ChannelRules.feedLimit(limit);
        List<CatalogueAuditEvent> events = auditRepository.itemChangesAfter(tenantId, cursor, clock.instant().minus(SETTLE), PageRequest.of(0, n));
        if (events.isEmpty()) return new ChangesResponse(cursor, false, List.of());

        long last = cursor;
        Map<UUID, CatalogueAuditEvent> latest = new LinkedHashMap<>();
        for (var e : events) {
            if (e.getSeq() != null) last = Math.max(last, e.getSeq());
            latest.remove(e.getEntityId());          // keep the order of the last change
            latest.put(e.getEntityId(), e);
        }
        Map<UUID, CatalogueItem> items = itemRepository.findLiveByIds(tenantId, latest.keySet()).stream().collect(Collectors.toMap(CatalogueItem::getId, Function.identity()));

        List<ChangesResponse.Change> out = new ArrayList<>();
        for (var e : latest.values()) {
            var i = items.get(e.getEntityId());
            out.add(new ChangesResponse.Change(e.getEntityId(), i == null ? "DELETE" : "UPSERT", e.getAction().name(), e.getCreatedAt(), i == null ? null : toItem(i, caller)));
        }
        return new ChangesResponse(last, events.size() == n, out);
    }

    private static ChangesResponse.Item toItem(CatalogueItem i, Caller caller) {
        return new ChangesResponse.Item(i.getId(), i.getParentId(), i.getSku(), i.getBarcode(), i.getName(), i.getStatus().name(),
                caller.canSeePrices() ? i.getDefaultPrice() : null, i.getUpdatedAt());
    }
}
