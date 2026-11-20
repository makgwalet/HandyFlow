package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.application.internal.MatchRules.Candidate;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.MonitoringDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.util.*;

/**
 * Regulatory monitoring: follow public feeds, keep what they publish once, and let a person decide what matters.
 *
 * Nothing here changes a register. A RELEVANT item is a prompt to go and record a regulatory change or review an obligation; it does not create one. The suggestions are
 * keyword matches against the tenant's own obligations, controls and policies (see {@link MatchRules}); they are computed on request, never stored, and carry no authority.
 * The network call is deliberately made outside any database transaction; storing the result is a separate short transaction in {@link MonitoringIngest}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    public static final int MAX_SOURCES = 25, CORPUS_LIMIT = 500, SUGGESTION_LIMIT = 10;

    private final WatchSourceRepository sources;
    private final WatchItemRepository items;
    private final RegulatoryObligationRepository obligations;
    private final ControlRepository controls;
    private final PolicyRepository policies;
    private final FeedFetcher fetcher;
    private final MonitoringIngest ingest;
    private final RegulatoryAuditService trail;

    // ---------------------------------------------------------------- sources

    @Transactional(readOnly = true)
    public MonitoringSummary summary(TenantId t) {
        List<WatchSource> all = sources.findAllForTenant(t);
        return new MonitoringSummary(all.stream().filter(WatchSource::isActive).count(), all.stream().filter(s -> s.isActive() && s.getLastStatus() == FetchStatus.FAILED).count(), items.countNew(t));
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> listSources(TenantId t) { return sources.findAllForTenant(t).stream().map(MonitoringService::response).toList(); }

    @Transactional
    public SourceResponse addSource(TenantId t, SourceRequest r, UUID uid, String uname) {
        String url = guarded(() -> UrlRules.validate(r.feedUrl()));
        if (sources.findAllForTenant(t).size() >= MAX_SOURCES) throw new HandyFlowException("A tenant can follow at most " + MAX_SOURCES + " sources", HttpStatus.CONFLICT, "INVALID_STATE");
        if (sources.existsByUrl(t, url)) throw new HandyFlowException("That feed is already being followed", HttpStatus.CONFLICT, "INVALID_STATE");
        WatchSource s = sources.save(guarded(() -> WatchSource.create(t, r.name(), r.authority(), url, uid, uname)));
        trail.record(t.getValue(), "WATCH_SOURCE", s.getId(), "SOURCE_ADDED", s.getName() + " (" + url + ")", uid, uname);
        return response(s);
    }

    @Transactional
    public SourceResponse updateSource(TenantId t, UUID id, SourceUpdateRequest r, UUID uid, String uname) {
        WatchSource s = load(t, id);
        boolean wasActive = s.isActive();
        guarded(() -> { s.rename(r.name(), r.authority()); return null; });
        if (r.active() != null) s.setActive(r.active());
        sources.save(s);
        trail.record(t.getValue(), "WATCH_SOURCE", s.getId(), wasActive != s.isActive() ? (s.isActive() ? "SOURCE_RESUMED" : "SOURCE_PAUSED") : "SOURCE_UPDATED", s.getName(), uid, uname);
        return response(s);
    }

    // ---------------------------------------------------------------- fetching

    /** Manual fetch. Does not need the source to be active, so a paused source can be tried once. */
    public FetchOutcome fetchNow(TenantId t, UUID id, UUID uid, String uname) {
        WatchSource s = load(t, id);
        FetchOutcome out = fetch(t, s);
        trail.record(t.getValue(), "WATCH_SOURCE", id, "SOURCE_FETCHED", out.failed() ? "Failed: " + out.error() : out.added() + " new of " + out.fetched(), uid, uname);
        return out;
    }

    /** Used by the daily job. Never throws: a failure is recorded on the source. */
    public FetchOutcome fetchSource(TenantId t, UUID id) {
        return fetch(t, load(t, id));
    }

    private FetchOutcome fetch(TenantId t, WatchSource s) {
        List<FeedRules.Item> parsed;
        try {
            parsed = FeedRules.parse(fetcher.fetch(s.getFeedUrl()));
        } catch (IOException | IllegalArgumentException e) {
            return ingest.failed(t, s.getId(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("[Legal/Compliance] Unexpected feed error for source={}: {}", s.getId(), e.getMessage());
            return ingest.failed(t, s.getId(), "The feed could not be processed");
        }
        try {
            return ingest.stored(t, s.getId(), parsed);
        } catch (DataIntegrityViolationException e) {
            return new FetchOutcome(parsed.size(), 0, true, "Another fetch of this source ran at the same time; try again", false);
        }
    }

    // ---------------------------------------------------------------- inbox

    @Transactional(readOnly = true)
    public List<ItemResponse> listItems(TenantId t, WatchItemStatus status, UUID sourceId, int limit) {
        return items.search(t, status, sourceId, PageRequest.of(0, Math.max(1, Math.min(limit, 200)))).stream().map(MonitoringService::response).toList();
    }

    @Transactional
    public ItemResponse decide(TenantId t, UUID id, DecisionRequest r, UUID uid, String uname) {
        WatchItem i = loadItem(t, id);
        guarded(() -> { i.decide(r.outcome(), r.note(), uid, uname); return null; });
        items.save(i);
        trail.record(t.getValue(), "WATCH_ITEM", i.getId(), "ITEM_" + i.getStatus().name(), clip(i.getTitle()) + (i.getDecisionNote() == null ? "" : " — " + i.getDecisionNote()), uid, uname);
        return response(i);
    }

    @Transactional
    public ItemResponse reopen(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        WatchItem i = loadItem(t, id);
        String before = i.getStatus().name() + (i.getDecisionNote() == null ? "" : " (" + i.getDecisionNote() + ")");
        guarded(() -> { i.reopen(r.reason()); return null; });
        items.save(i);
        trail.record(t.getValue(), "WATCH_ITEM", i.getId(), "ITEM_REOPENED", "Was " + before + ". Reason: " + r.reason().trim(), uid, uname);
        return response(i);
    }

    // ---------------------------------------------------------------- assisted impact assessment

    @Transactional(readOnly = true)
    public List<SuggestionResponse> suggestionsFor(TenantId t, UUID itemId) {
        WatchItem i = loadItem(t, itemId);
        return assist(t, i.getTitle() + " " + (i.getSummary() == null ? "" : i.getSummary()));
    }

    /** Which of this tenant's obligations, controls and policies does the text seem to touch? Advisory only; nothing is stored. */
    @Transactional(readOnly = true)
    public List<SuggestionResponse> assist(TenantId t, String text) {
        return MatchRules.suggest(FeedRules.plain(text), corpus(t), SUGGESTION_LIMIT).stream()
                .map(s -> new SuggestionResponse(s.type(), s.id(), s.reference(), s.label(), s.strength().name(), s.score(), s.referenceMentioned(), s.matchedTerms())).toList();
    }

    List<Candidate> corpus(TenantId t) {
        List<Candidate> out = new ArrayList<>();
        for (RegulatoryObligation o : obligations.findAllActive(t, null, PageRequest.of(0, CORPUS_LIMIT)).getContent())
            out.add(new Candidate("OBLIGATION", o.getId(), o.getRegulationReference(), o.getTitle(),
                    String.join(" ", nz(o.getDescription()), o.getCategory() == null ? "" : o.getCategory().name(), nz(o.getDepartmentName()))));
        for (Control c : controls.search(t, null, false)) out.add(new Candidate("CONTROL", c.getId(), c.getReference(), c.getTitle(), ""));
        for (Policy p : policies.search(t, false)) out.add(new Candidate("POLICY", p.getId(), p.getReference(), p.getTitle(), ""));
        return out;
    }

    // ---------------------------------------------------------------- helpers

    private WatchSource load(TenantId t, UUID id) { return sources.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Watch source", id.toString())); }

    private WatchItem loadItem(TenantId t, UUID id) { return items.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Watch item", id.toString())); }

    private static SourceResponse response(WatchSource s) {
        return new SourceResponse(s.getId(), s.getName(), s.getAuthority(), s.getFeedUrl(), s.isActive(), s.getLastFetchedAt(), s.getLastStatus(), s.getLastError(), s.getLastNewItems(),
                s.getConsecutiveFailures(), s.isActive() && s.getConsecutiveFailures() >= WatchSource.FAILURES_BEFORE_ALERT);
    }

    private static ItemResponse response(WatchItem i) {
        return new ItemResponse(i.getId(), i.getSourceId(), i.getSourceName(), i.getTitle(), i.getLink(), i.getSummary(), i.getPublishedOn(), i.getFetchedAt(), i.getStatus(), i.getDecisionNote(),
                i.getDecidedByName(), i.getDecidedAt());
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String clip(String s) { return s.length() > 120 ? s.substring(0, 120) + "…" : s; }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
