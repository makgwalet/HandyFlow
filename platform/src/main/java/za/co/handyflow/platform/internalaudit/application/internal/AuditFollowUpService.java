package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.internalaudit.application.internal.FollowUpSummary.Counts;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.internalaudit.dto.AuditFollowUpDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Follow-up audits: an engagement checks what became of the findings of an earlier one. Every step goes on the audit trail. */
@Service
@RequiredArgsConstructor
public class AuditFollowUpService {
    public record Actor(UUID userId, String name) {}

    private final AuditFollowUpLinkRepository links;
    private final AuditFollowUpItemRepository items;
    private final AuditEngagementRepository engagements;
    private final AuditFindingRepository findings;
    private final AuditTrailFacade trail;

    @Transactional(readOnly = true)
    public List<Summary> list(TenantId tenant) {
        UUID t = tenant.getValue();
        Map<UUID, String> names = names(t);
        Map<UUID, List<AuditFollowUpItem>> byEngagement = items.findByTenantId(t).stream().collect(Collectors.groupingBy(AuditFollowUpItem::getEngagementId));
        return links.findByTenantIdOrderByCreatedAtDesc(t).stream().map(l -> summary(l, byEngagement.getOrDefault(l.getEngagementId(), List.of()), names)).toList();
    }

    @Transactional(readOnly = true)
    public View get(TenantId tenant, UUID engagementId) {
        UUID t = tenant.getValue();
        var link = links.findByTenantIdAndEngagementId(t, engagementId).orElseThrow(() -> new ResourceNotFoundException("AuditFollowUp", engagementId.toString()));
        return view(t, link);
    }

    /** One pending item per finding of the earlier engagement. */
    @Transactional
    public View start(TenantId tenant, Actor actor, UUID engagementId, UUID priorEngagementId) {
        UUID t = tenant.getValue();
        if (engagementId == null || priorEngagementId == null) throw bad("Choose both engagements", "FU_INVALID");
        engagements.findByTenantAndId(t, engagementId).orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", engagementId.toString()));
        engagements.findByTenantAndId(t, priorEngagementId).orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", priorEngagementId.toString()));
        if (links.findByTenantIdAndEngagementId(t, engagementId).isPresent()) throw conflict("This engagement is already a follow-up", "FU_EXISTS");
        var prior = findings.findByEngagement(t, priorEngagementId);
        if (prior.isEmpty()) throw conflict("The earlier engagement has no findings to follow up", "FU_NOTHING");
        AuditFollowUpLink link = guard(() -> AuditFollowUpLink.of(t, engagementId, priorEngagementId, actor.userId()));
        links.save(link);
        prior.forEach(f -> items.save(AuditFollowUpItem.pending(t, engagementId, f.getId())));
        trail.record(tenant, "AUDIT_FOLLOWUP_STARTED", "AUDIT_ENGAGEMENT", engagementId, null, Map.of("priorEngagementId", priorEngagementId.toString(), "findings", prior.size()));
        return view(t, link);
    }

    @Transactional
    public View assess(TenantId tenant, Actor actor, UUID engagementId, UUID findingId, String outcome, String note) {
        UUID t = tenant.getValue();
        var link = links.findByTenantIdAndEngagementId(t, engagementId).orElseThrow(() -> new ResourceNotFoundException("AuditFollowUp", engagementId.toString()));
        var item = items.findByTenantIdAndEngagementIdAndFindingId(t, engagementId, findingId).orElseThrow(() -> new ResourceNotFoundException("AuditFollowUpItem", findingId.toString()));
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("outcome", item.getOutcome());
        guard(() -> item.assess(outcome, note, actor.userId(), actor.name()));
        items.save(item);
        trail.record(tenant, "AUDIT_FOLLOWUP_ASSESSED", "AUDIT_FINDING", findingId, before, Map.of("outcome", item.getOutcome(), "engagementId", engagementId.toString()));
        return view(t, link);
    }

    /** The follow-up lines for a report, or an empty list when the engagement is not a follow-up. */
    @Transactional(readOnly = true)
    public List<FollowUpSummary.Line> reportLines(UUID tenantId, UUID engagementId) {
        if (links.findByTenantIdAndEngagementId(tenantId, engagementId).isEmpty()) return List.of();
        Map<UUID, AuditFinding> byId = findingsById(tenantId, engagementId);
        return items.findByTenantIdAndEngagementId(tenantId, engagementId).stream()
                .map(i -> byId.get(i.getFindingId()) == null ? null : new FollowUpSummary.Line(byId.get(i.getFindingId()).getTitle(), byId.get(i.getFindingId()).getSeverity(), i.getOutcome(), i.getNote()))
                .filter(Objects::nonNull).toList();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────

    private Map<UUID, AuditFinding> findingsById(UUID t, UUID followUpEngagementId) {
        var link = links.findByTenantIdAndEngagementId(t, followUpEngagementId).orElse(null);
        if (link == null) return Map.of();
        return findings.findByEngagement(t, link.getPriorEngagementId()).stream().collect(Collectors.toMap(AuditFinding::getId, f -> f, (a, b) -> a));
    }

    private Map<UUID, String> names(UUID t) {
        Map<UUID, String> m = new HashMap<>();
        engagements.findAllForTenant(t).forEach(e -> m.put(e.getId(), e.getName()));
        return m;
    }

    private Summary summary(AuditFollowUpLink l, List<AuditFollowUpItem> its, Map<UUID, String> names) {
        Counts c = FollowUpSummary.count(its.stream().map(AuditFollowUpItem::getOutcome).toList());
        return new Summary(l.getEngagementId(), names.getOrDefault(l.getEngagementId(), "Engagement"), l.getPriorEngagementId(),
                names.getOrDefault(l.getPriorEngagementId(), "Engagement"),
                new CountsView(c.total(), c.assessed(), c.implemented(), c.partial(), c.notImplemented(), c.noLongerApplicable(), c.implementedPct(), c.complete()));
    }

    private View view(UUID t, AuditFollowUpLink l) {
        var its = items.findByTenantIdAndEngagementId(t, l.getEngagementId());
        Map<UUID, AuditFinding> byId = findingsById(t, l.getEngagementId());
        List<ItemView> iv = its.stream().map(i -> {
            AuditFinding f = byId.get(i.getFindingId());
            return new ItemView(i.getFindingId(), f == null ? "(finding removed)" : f.getTitle(), f == null ? null : f.getSeverity(), f == null ? null : f.getStatus(),
                    i.getOutcome(), i.getNote(), i.getAssessedByName(), i.getAssessedAt());
        }).sorted(Comparator.comparing(ItemView::title, String.CASE_INSENSITIVE_ORDER)).toList();
        return new View(summary(l, its, names(t)), iv);
    }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
    private static HandyFlowException conflict(String m, String code) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }
    private static void guard(Runnable r) {
        try { r.run(); } catch (IllegalArgumentException e) { throw bad(e.getMessage(), "FU_INVALID"); } catch (IllegalStateException e) { throw conflict(e.getMessage(), "FU_STATE"); }
    }
    private static <T> T guard(Supplier<T> s) {
        try { return s.get(); } catch (IllegalArgumentException e) { throw bad(e.getMessage(), "FU_INVALID"); } catch (IllegalStateException e) { throw conflict(e.getMessage(), "FU_STATE"); }
    }
}
