package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.RegulatoryDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Regulatory change monitoring. A change is flagged, linked to the catalogue obligations it touches, and confirmed or dismissed by a
 * reviewer. Confirming raises one impact per affected client, and each impact is dealt with by a person who leaves a note. This service
 * never edits the obligation catalogue, a client's obligations or a finished assessment, so past work stays as it was.
 */
@Service
@RequiredArgsConstructor
public class ComplianceRegulatoryService {

    private final ComplianceRegSourceRepository sources;
    private final ComplianceRegChangeRepository changes;
    private final ComplianceRegChangeObligationRepository links;
    private final ComplianceRegImpactRepository impacts;
    private final ComplianceObligationRepository obligations;
    private final ComplianceObligationVersionRepository versions;
    private final ComplianceClientObligationRepository assignments;
    private final ComplianceClientRepository clients;

    // ---- sources ----

    @Transactional(readOnly = true)
    public List<SourceView> sources(TenantId tenantId) {
        return sources.findAllForTenant(tenantId).stream().map(ComplianceRegulatoryService::view).toList();
    }

    @Transactional
    public SourceView createSource(TenantId tenantId, SourceRequest r, UUID by) {
        if (sources.findByName(tenantId, r.name() == null ? "" : r.name().trim()).isPresent()) {
            throw new HandyFlowException("There is already a source with that name", HttpStatus.CONFLICT, "REG_SOURCE_EXISTS");
        }
        return view(sources.save(guard(() -> ComplianceRegSource.create(tenantId, r.name(), r.authority(), r.url(), r.kind(), by))));
    }

    @Transactional
    public SourceView editSource(TenantId tenantId, UUID id, SourceRequest r) {
        ComplianceRegSource s = source(tenantId, id);
        sources.findByName(tenantId, r.name() == null ? "" : r.name().trim()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw new HandyFlowException("There is already a source with that name", HttpStatus.CONFLICT, "REG_SOURCE_EXISTS");
        });
        guard(() -> { s.edit(r.name(), r.authority(), r.url(), r.kind()); return null; });
        return view(sources.save(s));
    }

    @Transactional
    public SourceView setSourceActive(TenantId tenantId, UUID id, boolean active) {
        ComplianceRegSource s = source(tenantId, id);
        s.setActive(active);
        return view(sources.save(s));
    }

    // ---- changes ----

    @Transactional(readOnly = true)
    public List<ChangeSummary> list(TenantId tenantId, String status) {
        List<ComplianceRegChange> found = status == null || status.isBlank() ? changes.findAllForTenant(tenantId) : changes.findByStatus(tenantId, status.trim().toUpperCase());
        return summaries(tenantId, found);
    }

    @Transactional(readOnly = true)
    public ChangeDetail get(TenantId tenantId, UUID id) {
        return detail(tenantId, change(tenantId, id));
    }

    @Transactional
    public ChangeDetail raise(TenantId tenantId, ChangeRequest r, UUID by, LocalDate today) {
        ComplianceRegSource src = source(tenantId, r.sourceId());
        if (!src.isActive()) throw new HandyFlowException("That source is switched off. Switch it on first", HttpStatus.CONFLICT, "REG_SOURCE_INACTIVE");
        List<UUID> obIds = checkedObligations(tenantId, r.obligationIds());
        ComplianceRegChange c = guard(() -> ComplianceRegChange.raise(tenantId, src.getId(), r.title(), r.summary(), r.changeType(), r.publicationDate(),
                r.effectiveDate(), r.referenceUrl(), by, today));
        changes.save(c);
        obIds.forEach(o -> links.save(ComplianceRegChangeObligation.link(tenantId, c.getId(), o)));
        return detail(tenantId, c);
    }

    @Transactional
    public ChangeDetail edit(TenantId tenantId, UUID id, ChangeRequest r, LocalDate today) {
        ComplianceRegChange c = change(tenantId, id);
        source(tenantId, r.sourceId());
        List<UUID> obIds = checkedObligations(tenantId, r.obligationIds());
        guard(() -> { c.edit(r.sourceId(), r.title(), r.summary(), r.changeType(), r.publicationDate(), r.effectiveDate(), r.referenceUrl(), today); return null; });
        changes.save(c);
        Set<UUID> wanted = new LinkedHashSet<>(obIds);
        for (ComplianceRegChangeObligation l : links.findForChange(tenantId, id)) {
            if (!wanted.remove(l.getObligationId())) links.delete(l);
        }
        wanted.forEach(o -> links.save(ComplianceRegChangeObligation.link(tenantId, id, o)));
        return detail(tenantId, c);
    }

    @Transactional
    public ChangeDetail startReview(TenantId tenantId, UUID id, UUID by) {
        ComplianceRegChange c = change(tenantId, id);
        guard(() -> { c.startReview(by); return null; });
        return detail(tenantId, changes.save(c));
    }

    /** Confirms the change and raises an impact for every client it touches. A person other than the one who raised it, or the same person with a reason. */
    @Transactional
    public ChangeDetail confirm(TenantId tenantId, UUID id, ReviewRequest r, UUID by) {
        ComplianceRegChange c = change(tenantId, id);
        guard(() -> { c.confirm(by, r.note(), r.exceptionReason()); return null; });
        generateImpacts(tenantId, c);
        c.impactsRaised();
        return detail(tenantId, changes.save(c));
    }

    @Transactional
    public ChangeDetail dismiss(TenantId tenantId, UUID id, ReviewRequest r, UUID by) {
        ComplianceRegChange c = change(tenantId, id);
        guard(() -> { c.dismiss(by, r.note(), r.exceptionReason()); return null; });
        return detail(tenantId, changes.save(c));
    }

    /** Adds impacts for clients who have become affected since the change was confirmed. Existing impacts are left exactly as they are. */
    @Transactional
    public Generated refreshImpacts(TenantId tenantId, UUID id) {
        ComplianceRegChange c = change(tenantId, id);
        if (!"CONFIRMED".equals(c.getStatus())) throw new HandyFlowException("Only a confirmed change has impacts", HttpStatus.CONFLICT, "REG_NOT_CONFIRMED");
        int added = generateImpacts(tenantId, c);
        return new Generated(added, impacts.findForChange(tenantId, id).size());
    }

    // ---- impacts ----

    @Transactional(readOnly = true)
    public List<ImpactView> forClient(TenantId tenantId, UUID clientId) {
        clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
        return impactViews(tenantId, impacts.findForClient(tenantId, clientId));
    }

    @Transactional
    public ImpactView act(TenantId tenantId, UUID impactId, ImpactActionRequest r, UUID by) {
        ComplianceRegImpact i = impacts.findOne(tenantId, impactId).orElseThrow(() -> new ResourceNotFoundException("ComplianceRegImpact", impactId.toString()));
        guard(() -> { i.act(r.action(), r.note(), by); return null; });
        impacts.save(i);
        return impactViews(tenantId, List.of(i)).get(0);
    }

    // ---- internals ----

    private int generateImpacts(TenantId tenantId, ComplianceRegChange c) {
        Set<UUID> obIds = links.findForChange(tenantId, c.getId()).stream().map(ComplianceRegChangeObligation::getObligationId).collect(Collectors.toSet());
        if (obIds.isEmpty()) return 0;
        Set<String> have = impacts.findForChange(tenantId, c.getId()).stream().map(i -> i.getClientId() + "|" + i.getObligationId()).collect(Collectors.toSet());
        Set<UUID> active = activeClients(tenantId).keySet();
        int added = 0;
        for (ComplianceClientObligation a : assignments.findForObligations(tenantId, obIds)) {
            if (!active.contains(a.getClientId()) || ApplicabilityRules.NOT_APPLICABLE.equals(a.effectiveState())) continue;
            if (!have.add(a.getClientId() + "|" + a.getObligationId())) continue;
            impacts.save(ComplianceRegImpact.open(tenantId, c.getId(), a.getClientId(), a.getObligationId(), c.getEffectiveDate()));
            added++;
        }
        return added;
    }

    private List<AffectedClient> preview(TenantId tenantId, ComplianceRegChange c, Map<UUID, ComplianceObligation> byId) {
        Set<UUID> obIds = links.findForChange(tenantId, c.getId()).stream().map(ComplianceRegChangeObligation::getObligationId).collect(Collectors.toSet());
        if (obIds.isEmpty()) return List.of();
        Map<UUID, String> names = activeClients(tenantId);
        List<AffectedClient> out = new ArrayList<>();
        for (ComplianceClientObligation a : assignments.findForObligations(tenantId, obIds)) {
            if (!names.containsKey(a.getClientId()) || ApplicabilityRules.NOT_APPLICABLE.equals(a.effectiveState())) continue;
            ComplianceObligation o = byId.get(a.getObligationId());
            out.add(new AffectedClient(a.getClientId(), names.get(a.getClientId()), o == null ? "?" : o.getCode(), a.effectiveState()));
        }
        out.sort(Comparator.comparing(AffectedClient::clientName, String.CASE_INSENSITIVE_ORDER).thenComparing(AffectedClient::obligationCode));
        return out;
    }

    private Map<UUID, String> activeClients(TenantId tenantId) {
        return clients.findAll(tenantId, "ACTIVE", Pageable.unpaged()).getContent().stream().collect(Collectors.toMap(ComplianceClient::getId, ComplianceClient::getName));
    }

    private ChangeDetail detail(TenantId tenantId, ComplianceRegChange c) {
        Map<UUID, ComplianceObligation> byId = obligations.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceObligation::getId, o -> o));
        Map<UUID, String> nameOf = latestNames(tenantId);
        List<ObligationRef> refs = links.findForChange(tenantId, c.getId()).stream().map(l -> {
            ComplianceObligation o = byId.get(l.getObligationId());
            return new ObligationRef(l.getObligationId(), o == null ? "?" : o.getCode(), nameOf.get(l.getObligationId()));
        }).sorted(Comparator.comparing(ObligationRef::code)).toList();
        boolean decided = "CONFIRMED".equals(c.getStatus()) || "DISMISSED".equals(c.getStatus());
        List<AffectedClient> affected = decided ? List.of() : preview(tenantId, c, byId);
        return new ChangeDetail(summaries(tenantId, List.of(c)).get(0), c.getSummary(), c.getReferenceUrl(), refs, c.getReviewedAt(), c.getReviewNote(),
                c.getReviewExceptionReason(), affected, impactViews(tenantId, impacts.findForChange(tenantId, c.getId())));
    }

    private List<ChangeSummary> summaries(TenantId tenantId, List<ComplianceRegChange> found) {
        Map<UUID, String> sourceNames = sources.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceRegSource::getId, ComplianceRegSource::getName));
        Map<UUID, Long> linked = links.findAllForTenant(tenantId).stream().collect(Collectors.groupingBy(ComplianceRegChangeObligation::getChangeId, Collectors.counting()));
        Map<UUID, List<ComplianceRegImpact>> byChange = impacts.findAllForTenant(tenantId).stream().collect(Collectors.groupingBy(ComplianceRegImpact::getChangeId));
        return found.stream().map(c -> {
            List<ComplianceRegImpact> mine = byChange.getOrDefault(c.getId(), List.of());
            int open = (int) mine.stream().filter(i -> RegulatoryRules.impactLive(i.getStatus())).count();
            return new ChangeSummary(c.getId(), c.getSourceId(), sourceNames.getOrDefault(c.getSourceId(), "?"), c.getTitle(), c.getChangeType(), c.getStatus(), c.getOrigin(),
                    c.getPublicationDate(), c.getEffectiveDate(), c.getRaisedAt(), linked.getOrDefault(c.getId(), 0L).intValue(), open, mine.size(), RegulatoryRules.changeActions(c.getStatus()));
        }).toList();
    }

    private List<ImpactView> impactViews(TenantId tenantId, List<ComplianceRegImpact> found) {
        if (found.isEmpty()) return List.of();
        Map<UUID, ComplianceObligation> byId = obligations.findAllForTenant(tenantId).stream().collect(Collectors.toMap(ComplianceObligation::getId, o -> o));
        Map<UUID, String> clientNames = clients.findAll(tenantId, null, Pageable.unpaged()).getContent().stream().collect(Collectors.toMap(ComplianceClient::getId, ComplianceClient::getName));
        Map<UUID, String> titles = changes.findByIds(tenantId, found.stream().map(ComplianceRegImpact::getChangeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ComplianceRegChange::getId, ComplianceRegChange::getTitle));
        return found.stream().map(i -> {
            ComplianceObligation o = byId.get(i.getObligationId());
            return new ImpactView(i.getId(), i.getChangeId(), titles.getOrDefault(i.getChangeId(), "?"), i.getClientId(), clientNames.getOrDefault(i.getClientId(), "?"),
                    o == null ? "?" : o.getCode(), i.getStatus(), i.getDueDate(), i.getNote(), i.getHandledAt(), i.getNoticeGivenAt(), RegulatoryRules.impactActions(i.getStatus()));
        }).toList();
    }

    private Map<UUID, String> latestNames(TenantId tenantId) {
        Map<UUID, ComplianceObligationVersion> latest = new HashMap<>();
        for (ComplianceObligationVersion v : versions.findAllForTenant(tenantId)) latest.merge(v.getObligationId(), v, (a, b) -> a.getVersionNumber() >= b.getVersionNumber() ? a : b);
        return latest.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName() == null ? "" : e.getValue().getName()));
    }

    private List<UUID> checkedObligations(TenantId tenantId, List<UUID> ids) {
        if (ids == null) return List.of();
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        for (UUID id : distinct) obligations.findByIdForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceObligation", id.toString()));
        return distinct;
    }

    private ComplianceRegSource source(TenantId tenantId, UUID id) {
        return sources.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceRegSource", id.toString()));
    }

    private ComplianceRegChange change(TenantId tenantId, UUID id) {
        return changes.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceRegChange", id.toString()));
    }

    private static SourceView view(ComplianceRegSource s) {
        return new SourceView(s.getId(), s.getName(), s.getAuthority(), s.getUrl(), s.getKind(), s.isActive());
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "REG_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "REG_INVALID");
        }
    }
}
