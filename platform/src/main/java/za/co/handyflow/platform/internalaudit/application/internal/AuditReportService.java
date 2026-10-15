package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.ReportTemplates.FindingText;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.internalaudit.dto.AuditReportDtos.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Engagement reports as documents, their factual-accuracy review, and who may read them once issued. Sign-off stays with the approval
 * ({@code ENGAGEMENT_REPORT}); issuing is a separate step that needs it. Every step goes on the audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditReportService {

    /** The caller. {@code manager} is true for AUDIT_MANAGE / AUDIT_ADMIN holders. */
    public record Actor(UUID userId, String name, boolean manager) {}

    private final AuditReportRepository reports;
    private final AuditReportSectionRepository sections;
    private final AuditReportFactItemRepository items;
    private final AuditReportPersonRepository people;
    private final AuditFindingRepository findings;
    private final AuditEngagementRepository engagements;
    private final ApprovalFacade approvals;
    private final AuditTrailFacade trail;
    private final UserRepository users;
    private final UserRecipientResolver recipients;
    private final NotificationService notifications;
    private final AuditFollowUpService followUps;

    // ── reading ─────────────────────────────────────────────────────────────────────────────────────

    public List<TemplateView> templates() {
        return ReportTemplates.all().stream().map(t -> new TemplateView(t.id(), t.label(), t.sections().stream().map(ReportTemplates.SectionSpec::title).toList())).toList();
    }

    @Transactional(readOnly = true)
    public List<Summary> list(TenantId tenant, Actor actor, UUID engagementId) {
        UUID t = tenant.getValue();
        List<AuditReport> rs;
        if (actor.manager()) {
            rs = engagementId == null ? reports.findByTenantIdOrderByCreatedAtDesc(t) : reports.findByTenantIdAndEngagementIdOrderByVersionDesc(t, engagementId);
        } else {
            Map<UUID, List<AuditReportPerson>> mine = new HashMap<>();
            people.findByTenantIdAndUserId(t, actor.userId()).forEach(p -> mine.computeIfAbsent(p.getReportId(), k -> new ArrayList<>()).add(p));
            rs = reports.findByTenantIdAndIdIn(t, mine.keySet()).stream()
                    .filter(r -> mine.get(r.getId()).stream().anyMatch(p -> visibleTo(p.getKind(), r)))
                    .filter(r -> engagementId == null || engagementId.equals(r.getEngagementId())).toList();
        }
        Map<UUID, String> names = engagementNames(t);
        return rs.stream().map(r -> summary(t, r, names)).toList();
    }

    /** Reading an issued report as a named recipient is counted and put on the audit trail. */
    @Transactional
    public View get(TenantId tenant, Actor actor, UUID id) {
        UUID t = tenant.getValue();
        AuditReport r = load(t, id);
        List<AuditReportPerson> all = people.findByTenantIdAndReportIdOrderByAddedAt(t, id);
        AuditReportPerson me = all.stream().filter(p -> p.getUserId().equals(actor.userId()) && visibleTo(p.getKind(), r)).findFirst().orElse(null);
        if (!actor.manager() && me == null) throw new ResourceNotFoundException("AuditReport", id.toString());
        if (!actor.manager() && "RECIPIENT".equals(me.getKind()) && "ISSUED".equals(r.getStatus())) {
            me.recordView(); people.save(me);
            trail.record(tenant, "AUDIT_REPORT_VIEWED", "AUDIT_REPORT", r.getId(), null, Map.of("reader", actor.name(), "version", r.getVersion()));
        }
        boolean reviewer = all.stream().anyMatch(p -> "REVIEWER".equals(p.getKind()) && p.getUserId().equals(actor.userId()));
        List<ItemView> itemViews = (actor.manager() || reviewer)
                ? items.findByTenantIdAndReportIdOrderByRaisedAt(t, id).stream().map(this::itemView).toList() : List.of();
        List<PersonView> peopleViews = actor.manager() ? all.stream().map(this::personView).toList() : List.of();
        return new View(summary(t, r, engagementNames(t)), r.getContentHash(), actor.manager(), actor.manager() && r.contentEditable(),
                sections.findByTenantIdAndReportIdOrderByPosition(t, id).stream().map(s -> new SectionView(s.getPosition(), s.getTitle(), s.getBody(), s.isAutoFindings())).toList(),
                itemViews, peopleViews);
    }

    // ── drafting ────────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public View create(TenantId tenant, Actor actor, UUID engagementId, String templateId, String title) {
        UUID t = tenant.getValue();
        engagements.findByTenantAndId(t, engagementId).orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", String.valueOf(engagementId)));
        var template = ReportTemplates.find(templateId).orElseThrow(() -> bad("Choose one of the report templates", "REPORT_TEMPLATE"));
        List<AuditReport> existing = reports.findByTenantIdAndEngagementIdOrderByVersionDesc(t, engagementId);
        if (existing.stream().anyMatch(r -> !r.isFinal())) throw conflict("This engagement already has a report in progress", "REPORT_IN_PROGRESS");
        if (existing.stream().anyMatch(r -> "ISSUED".equals(r.getStatus()))) throw conflict("The report has been issued. Start a new version instead", "REPORT_ISSUED");
        return open(tenant, actor, engagementId, template, title, existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1, null);
    }

    /** A new draft that starts from the issued report's text. The issued one stays as it is until the new version is issued. */
    @Transactional
    public View newVersion(TenantId tenant, Actor actor, UUID id) {
        UUID t = tenant.getValue();
        AuditReport issued = load(t, id);
        if (!"ISSUED".equals(issued.getStatus())) throw conflict("Only an issued report can be revised", "REPORT_STATE");
        List<AuditReport> all = reports.findByTenantIdAndEngagementIdOrderByVersionDesc(t, issued.getEngagementId());
        if (all.stream().anyMatch(r -> !r.isFinal())) throw conflict("A newer version is already in progress", "REPORT_IN_PROGRESS");
        var template = ReportTemplates.find(issued.getTemplate()).orElse(ReportTemplates.find("STANDARD").orElseThrow());
        return open(tenant, actor, issued.getEngagementId(), template, issued.getTitle(), all.get(0).getVersion() + 1, id);
    }

    private View open(TenantId tenant, Actor actor, UUID engagementId, ReportTemplates.Template template, String title, int version, UUID copyFrom) {
        UUID t = tenant.getValue();
        String name = engagements.findByTenantAndId(t, engagementId).map(AuditEngagement::getName).orElse("Engagement");
        AuditReport r = guard(() -> AuditReport.draft(t, engagementId, version, template.id(),
                title == null || title.isBlank() ? name + " – internal audit report" : title, actor.userId()));
        reports.save(r);
        if (copyFrom != null) {
            int n = 1;
            for (AuditReportSection s : sections.findByTenantIdAndReportIdOrderByPosition(t, copyFrom))
                sections.save(AuditReportSection.of(t, r.getId(), n++, s.getTitle(), s.getBody(), s.isAutoFindings()));
        } else {
            List<FindingText> ft = findings.findByEngagement(t, engagementId).stream()
                    .map(f -> new FindingText(f.getTitle(), f.getSeverity(), f.getCriteria(), f.getCondition(), f.getConsequence(), f.getRecommendation(), f.getManagementResponse())).toList();
            var followUpLines = followUps.reportLines(t, engagementId);
            String autoText = "FOLLOW_UP".equals(template.id()) && !followUpLines.isEmpty() ? FollowUpSummary.reportText(followUpLines) : ReportTemplates.findingsText(ft);
            int n = 1;
            for (var spec : template.sections())
                sections.save(AuditReportSection.of(t, r.getId(), n++, spec.title(), spec.autoFindings() ? autoText : "", spec.autoFindings()));
        }
        trail.record(tenant, "AUDIT_REPORT_CREATED", "AUDIT_REPORT", r.getId(), null, Map.of("engagementId", engagementId.toString(), "version", version, "template", template.id()));
        return get(tenant, actor, r.getId());
    }

    @Transactional
    public View editSection(TenantId tenant, Actor actor, UUID id, int position, String title, String body) {
        UUID t = tenant.getValue();
        AuditReport r = load(t, id);
        guard(r::requireEditable);
        AuditReportSection s = sections.findByTenantIdAndReportIdAndPosition(t, id, position).orElseThrow(() -> new ResourceNotFoundException("AuditReportSection", String.valueOf(position)));
        guard(() -> s.edit(title, body));
        sections.save(s);
        trail.record(tenant, "AUDIT_REPORT_EDITED", "AUDIT_REPORT", id, null, Map.of("section", position, "status", r.getStatus()));
        return get(tenant, actor, id);
    }

    // ── people ──────────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public View addPerson(TenantId tenant, Actor actor, UUID id, String kind, UUID userId) {
        UUID t = tenant.getValue();
        AuditReport r = load(t, id);
        String k = kind == null ? "" : kind.toUpperCase();
        if ("REVIEWER".equals(k) && !r.contentEditable()) throw conflict("Reviewers can be named only while the report is a draft or in review", "REPORT_STATE");
        if ("RECIPIENT".equals(k) && "SUPERSEDED".equals(r.getStatus())) throw conflict("This version has been replaced; name recipients on the current version", "REPORT_STATE");
        var user = users.findByIdAndTenantId(userId, tenant).orElseThrow(() -> bad("That person is not in this company", "REPORT_PERSON"));
        if (people.findByTenantIdAndReportIdAndKindAndUserId(t, id, k, userId).isPresent()) throw conflict("They are already on the list", "REPORT_PERSON");
        String name = (user.getFirstName() + " " + user.getLastName()).trim();
        AuditReportPerson p = guard(() -> AuditReportPerson.of(t, id, k, userId, name, actor.userId()));
        people.save(p);
        trail.record(tenant, "AUDIT_REPORT_PERSON_ADDED", "AUDIT_REPORT", id, null, Map.of("kind", k, "person", name));
        if ("RECIPIENT".equals(k) && "ISSUED".equals(r.getStatus())) notify(tenant, List.of(userId), NotificationType.AUDIT_REPORT_ISSUED, "Audit report available", r.getTitle() + " has been shared with you", id);
        return get(tenant, actor, id);
    }

    @Transactional
    public View removePerson(TenantId tenant, Actor actor, UUID id, UUID personId) {
        UUID t = tenant.getValue();
        AuditReport r = load(t, id);
        AuditReportPerson p = people.findByTenantIdAndId(t, personId).filter(x -> x.getReportId().equals(id)).orElseThrow(() -> new ResourceNotFoundException("AuditReportPerson", personId.toString()));
        if ("REVIEWER".equals(p.getKind()) && !"DRAFT".equals(r.getStatus())) throw conflict("A reviewer can be removed only while the report is a draft", "REPORT_STATE");
        people.delete(p);
        trail.record(tenant, "AUDIT_REPORT_PERSON_REMOVED", "AUDIT_REPORT", id, Map.of("kind", p.getKind(), "person", p.getUserName(), "views", p.getViewCount()), null);
        return get(tenant, actor, id);
    }

    // ── factual-accuracy review ─────────────────────────────────────────────────────────────────────

    @Transactional
    public View startFactualReview(TenantId tenant, Actor actor, UUID id) {
        UUID t = tenant.getValue();
        AuditReport r = load(t, id);
        List<AuditReportPerson> reviewers = people.findByTenantIdAndReportIdOrderByAddedAt(t, id).stream().filter(p -> "REVIEWER".equals(p.getKind())).toList();
        guard(() -> r.startFactualReview(reviewers.size()));
        reports.save(r);
        trail.record(tenant, "AUDIT_REPORT_REVIEW_STARTED", "AUDIT_REPORT", id, null, Map.of("reviewers", reviewers.size()));
        notify(tenant, reviewers.stream().map(AuditReportPerson::getUserId).toList(), NotificationType.AUDIT_REPORT_REVIEW_REQUESTED,
                "Factual-accuracy review requested", "Please check the facts in the draft report \"" + r.getTitle() + "\" and raise anything that is wrong", id);
        return get(tenant, actor, id);
    }

    @Transactional
    public View raiseItem(TenantId tenant, Actor actor, UUID id, Integer sectionPosition, String comment) {
        UUID t = tenant.getValue();
        AuditReport r = load(t, id);
        boolean reviewer = people.findByTenantIdAndReportIdAndKindAndUserId(t, id, "REVIEWER", actor.userId()).isPresent();
        if (!reviewer && !actor.manager()) throw new ResourceNotFoundException("AuditReport", id.toString());
        if (!"FACTUAL_REVIEW".equals(r.getStatus())) throw conflict("The report is not open for factual-accuracy comments", "REPORT_STATE");
        if (sectionPosition != null && sections.findByTenantIdAndReportIdAndPosition(t, id, sectionPosition).isEmpty()) throw bad("There is no such section", "REPORT_SECTION");
        AuditReportFactItem i = guard(() -> AuditReportFactItem.raise(t, id, sectionPosition, comment, actor.userId(), actor.name()));
        items.save(i);
        trail.record(tenant, "AUDIT_REPORT_FACT_RAISED", "AUDIT_REPORT", id, null, Map.of("item", i.getId().toString(), "by", actor.name()));
        return get(tenant, actor, id);
    }

    @Transactional
    public View respondItem(TenantId tenant, Actor actor, UUID id, UUID itemId, String decision, String response) {
        UUID t = tenant.getValue();
        AuditReport r = load(t, id);
        if (!"FACTUAL_REVIEW".equals(r.getStatus())) throw conflict("The report is not in factual-accuracy review", "REPORT_STATE");
        AuditReportFactItem i = items.findByTenantIdAndId(t, itemId).filter(x -> x.getReportId().equals(id)).orElseThrow(() -> new ResourceNotFoundException("AuditReportFactItem", itemId.toString()));
        guard(() -> i.respond(decision, response, actor.userId(), actor.name()));
        items.save(i);
        trail.record(tenant, "AUDIT_REPORT_FACT_ANSWERED", "AUDIT_REPORT", id, null, Map.of("item", itemId.toString(), "decision", i.getStatus()));
        notify(tenant, List.of(i.getRaisedBy()), NotificationType.AUDIT_REPORT_REVIEW_REQUESTED, "Your comment on the draft report was answered",
                "Your comment was " + i.getStatus().toLowerCase() + ": " + i.getResponse(), id);
        return get(tenant, actor, id);
    }

    @Transactional
    public View closeFactualReview(TenantId tenant, Actor actor, UUID id) {
        UUID t = tenant.getValue();
        AuditReport r = load(t, id);
        long open = items.countByTenantIdAndReportIdAndStatus(t, id, "OPEN");
        guard(() -> r.closeFactualReview((int) open));
        reports.save(r);
        trail.record(tenant, "AUDIT_REPORT_REVIEW_CLOSED", "AUDIT_REPORT", id, null, Map.of());
        return get(tenant, actor, id);
    }

    @Transactional
    public View reopen(TenantId tenant, Actor actor, UUID id) {
        AuditReport r = load(tenant.getValue(), id);
        guard(r::reopen);
        reports.save(r);
        trail.record(tenant, "AUDIT_REPORT_REOPENED", "AUDIT_REPORT", id, null, Map.of());
        return get(tenant, actor, id);
    }

    // ── issuing ─────────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public View issue(TenantId tenant, Actor actor, UUID id) {
        UUID t = tenant.getValue();
        AuditReport r = load(t, id);
        var latest = approvals.getLatestRequestForEntity(tenant, "internalaudit", "ENGAGEMENT_REPORT", r.getEngagementId()).orElse(null);
        boolean signedOff = latest != null && "APPROVED".equals(latest.status())
                && (r.getVersion() == 1 || (latest.completedAt() != null && latest.completedAt().isAfter(r.getCreatedAt())));
        List<String[]> content = sections.findByTenantIdAndReportIdOrderByPosition(t, id).stream().map(s -> new String[]{s.getTitle(), s.getBody()}).toList();
        String hash = ReportTemplates.contentHash(r.getTitle(), content);
        guard(() -> r.issue(actor.userId(), hash, signedOff));
        reports.findByTenantIdAndEngagementIdOrderByVersionDesc(t, r.getEngagementId()).stream()
                .filter(o -> !o.getId().equals(id) && "ISSUED".equals(o.getStatus())).forEach(o -> { o.supersede(); reports.save(o); });
        reports.save(r);
        trail.record(tenant, "AUDIT_REPORT_ISSUED", "AUDIT_REPORT", id, null, Map.of("version", r.getVersion(), "contentHash", hash));
        notify(tenant, people.findByTenantIdAndReportIdOrderByAddedAt(t, id).stream().filter(p -> "RECIPIENT".equals(p.getKind())).map(AuditReportPerson::getUserId).toList(),
                NotificationType.AUDIT_REPORT_ISSUED, "Audit report issued", "\"" + r.getTitle() + "\" has been issued to you", id);
        return get(tenant, actor, id);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────

    private static boolean visibleTo(String kind, AuditReport r) {
        return "RECIPIENT".equals(kind) ? "ISSUED".equals(r.getStatus()) : !"DRAFT".equals(r.getStatus());
    }

    private AuditReport load(UUID tenantId, UUID id) {
        return reports.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("AuditReport", id.toString()));
    }

    private Map<UUID, String> engagementNames(UUID t) {
        Map<UUID, String> m = new HashMap<>();
        engagements.findAllForTenant(t).forEach(e -> m.put(e.getId(), e.getName()));
        return m;
    }

    private Summary summary(UUID t, AuditReport r, Map<UUID, String> names) {
        return new Summary(r.getId(), r.getEngagementId(), names.getOrDefault(r.getEngagementId(), "Engagement"), r.getVersion(), r.getTitle(), r.getTemplate(),
                r.getStatus(), r.getCreatedAt(), r.getIssuedAt(), (int) items.countByTenantIdAndReportIdAndStatus(t, r.getId(), "OPEN"));
    }

    private ItemView itemView(AuditReportFactItem i) {
        return new ItemView(i.getId(), i.getSectionPosition(), i.getComment(), i.getRaisedByName(), i.getRaisedAt(), i.getStatus(), i.getResponse(), i.getRespondedByName(), i.getRespondedAt());
    }

    private PersonView personView(AuditReportPerson p) {
        return new PersonView(p.getId(), p.getKind(), p.getUserId(), p.getUserName(), p.getAddedAt(), p.getFirstViewedAt(), p.getLastViewedAt(), p.getViewCount());
    }

    /** A notification must never undo the step that caused it. */
    private void notify(TenantId tenant, Collection<UUID> userIds, NotificationType type, String title, String message, UUID reportId) {
        try {
            List<za.co.handyflow.platform.notifications.application.Recipient> to = new ArrayList<>();
            for (UUID u : new LinkedHashSet<>(userIds)) recipients.resolveUser(tenant, u).ifPresent(to::add);
            if (to.isEmpty()) return;
            notifications.send(NotificationRequest.builder().tenantId(tenant).type(type).title(title).message(message)
                    .actionUrl("/internal-audit/reports").sourceModule("internalaudit").sourceEntityId(reportId.toString()).recipients(to).build());
        } catch (Exception e) {
            log.warn("[InternalAudit] Could not send report notification {}: {}", type, e.getMessage());
        }
    }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
    private static HandyFlowException conflict(String m, String code) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }

    private static void guard(Runnable r) {
        try { r.run(); }
        catch (IllegalArgumentException e) { throw bad(e.getMessage(), "REPORT_INVALID"); }
        catch (IllegalStateException e) { throw conflict(e.getMessage(), "REPORT_STATE"); }
    }

    private static <T> T guard(java.util.function.Supplier<T> s) {
        try { return s.get(); }
        catch (IllegalArgumentException e) { throw bad(e.getMessage(), "REPORT_INVALID"); }
        catch (IllegalStateException e) { throw conflict(e.getMessage(), "REPORT_STATE"); }
    }
}
