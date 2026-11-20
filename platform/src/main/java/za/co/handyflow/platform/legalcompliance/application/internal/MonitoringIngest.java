package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.WatchItem;
import za.co.handyflow.platform.legalcompliance.domain.model.WatchSource;
import za.co.handyflow.platform.legalcompliance.domain.repository.WatchItemRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.WatchSourceRepository;
import za.co.handyflow.platform.legalcompliance.dto.MonitoringDtos.FetchOutcome;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The short database step after a fetch. A separate bean so the transaction is real (a call from inside MonitoringService would bypass the proxy) and so no connection is held during the network call. */
@Service
@RequiredArgsConstructor
public class MonitoringIngest {

    private final WatchSourceRepository sources;
    private final WatchItemRepository items;

    /** Keeps each guid once per source. Items the feed republishes, and duplicates within one feed, are ignored. */
    @Transactional
    public FetchOutcome stored(TenantId t, UUID sourceId, List<FeedRules.Item> parsed) {
        WatchSource s = sources.findForTenant(t, sourceId).orElseThrow(() -> new ResourceNotFoundException("Watch source", sourceId.toString()));
        Set<String> known = parsed.isEmpty() ? Set.of() : Set.copyOf(items.existingGuids(t, sourceId, parsed.stream().map(FeedRules.Item::guid).toList()));
        Instant now = Instant.now();
        int added = 0;
        for (FeedRules.Item i : parsed) {
            if (known.contains(i.guid())) continue;
            items.save(WatchItem.create(t, sourceId, s.getName(), i.guid(), i.title(), i.link(), i.summary(), i.publishedOn(), now));
            added++;
        }
        s.recordSuccess(added, now);
        sources.save(s);
        return new FetchOutcome(parsed.size(), added, false, null, false);
    }

    @Transactional
    public FetchOutcome failed(TenantId t, UUID sourceId, String error) {
        WatchSource s = sources.findForTenant(t, sourceId).orElseThrow(() -> new ResourceNotFoundException("Watch source", sourceId.toString()));
        s.recordFailure(error, Instant.now());
        sources.save(s);
        return new FetchOutcome(0, 0, true, s.getLastError(), s.justBecameAlertable());
    }
}
