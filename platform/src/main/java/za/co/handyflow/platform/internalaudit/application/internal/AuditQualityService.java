package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.QualityMetricsBuilder.*;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.internalaudit.dto.AuditQualityDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Engagement quality review: an independent reviewer answers the methodology checklist and concludes. Every step goes on the audit trail. */
@Service
@RequiredArgsConstructor
public class AuditQualityService {
    public record Actor(UUID userId, String name) {}

    private final AuditQualityReviewRepository reviews;
    private final AuditQualityCheckRepository checks;
    private final AuditEngagementRepository engagements;
    private final EngagementAssignmentRepository assignments;
    private final AuditReportRepository reports;
    private final AuditReviewNoteRepository notes;
    private final AuditFindingRepository findings;
    private final UserRepository users;
    private final AuditTrailFacade trail;

    public List<CheckView> checklist() {
        return QualityChecklist.items().stream().map(i -> new CheckView(i.code(), i.area(), i.question(), null, null, null)).toList();
    }

    @Transactional(readOnly = true)
    public List<Summary> list(TenantId tenant, UUID engagementId) {
        UUID t = tenant.getValue();
        var rs = engagementId == null ? reviews.findByTenantIdOrderByRequestedAtDesc(t) : reviews.findByTenantIdAndEngagementIdOrderByRequestedAtDesc(t, engagementId);
        Map<UUID, List<AuditQualityCheck>> byReview = checks.findByTenantIdAndReviewIdIn(t, rs.stream().map(AuditQualityReview::getId).toList())
                .stream().collect(Collectors.groupingBy(AuditQualityCheck::getReviewId));
        Map<UUID, String> names = names(t);
        return rs.stream().map(r -> summary(r, byReview.getOrDefault(r.getId(), List.of()), names)).toList();
    }

    @Transactional(readOnly = true)
    public View get(TenantId tenant, UUID id) {
        UUID t = tenant.getValue();
        AuditQualityReview r = load(t, id);
        return view(t, r);
    }

    @Transactional
    public View request(TenantId tenant, Actor actor, UUID engagementId, UUID reviewerId) {
        UUID t = tenant.getValue();
        engagements.findByTenantAndId(t, engagementId).orElseThrow(() -> new ResourceNotFoundException("AuditEngagement", String.valueOf(engagementId)));
        var reviewer = users.findByIdAndTenantId(reviewerId, tenant).orElseThrow(() -> bad("That person is not in this company", "QR_REVIEWER"));
        if (assignments.findByEngagement(t, engagementId).stream().anyMatch(a -> reviewerId.equals(a.getUserId())))
            throw conflict("The reviewer must be independent of the engagement: this person is on its team", "QR_NOT_INDEPENDENT");
        if (reviews.findByTenantIdAndEngagementIdOrderByRequestedAtDesc(t, engagementId).stream().anyMatch(AuditQualityReview::isLive))
            throw conflict("This engagement already has a quality review in progress", "QR_IN_PROGRESS");
        String reviewerName = (reviewer.getFirstName() + " " + reviewer.getLastName()).trim();
        AuditQualityReview r = guard(() -> AuditQualityReview.request(t, engagementId, reviewerId, reviewerName, actor.userId(), actor.name()));
        reviews.save(r);
        trail.record(tenant, "AUDIT_QR_REQUESTED", "AUDIT_QUALITY_REVIEW", r.getId(), null, Map.of("engagementId", engagementId.toString(), "reviewer", reviewerName));
        return view(t, r);
    }

    @Transactional
    public View answer(TenantId tenant, Actor actor, UUID id, String code, String result, String note) {
        UUID t = tenant.getValue();
        AuditQualityReview r = load(t, id);
        guard(() -> { r.requireLive(); r.requireReviewer(actor.userId()); });
        var item = QualityChecklist.find(code).orElseThrow(() -> bad("There is no such checklist item", "QR_ITEM"));
        var existing = checks.findByTenantIdAndReviewIdAndItemCode(t, id, item.code());
        final AuditQualityCheck c = existing.isPresent() ? existing.get()
                : guard(() -> AuditQualityCheck.answer(t, id, item.code(), result, note, actor.userId()));
        if (existing.isPresent()) guard(() -> c.set(result, note, actor.userId()));
        checks.save(c);
        r.answered();
        reviews.save(r);
        trail.record(tenant, "AUDIT_QR_ANSWERED", "AUDIT_QUALITY_REVIEW", id, null, Map.of("item", item.code(), "result", c.getResult()));
        return view(t, r);
    }

    @Transactional
    public View complete(TenantId tenant, Actor actor, UUID id, String conclusion, String note) {
        UUID t = tenant.getValue();
        AuditQualityReview r = load(t, id);
        var answers = checks.findByTenantIdAndReviewId(t, id);
        int unanswered = QualityChecklist.items().size() - (int) answers.stream().map(AuditQualityCheck::getItemCode).distinct().count();
        int no = (int) answers.stream().filter(a -> "NO".equals(a.getResult())).count();
        guard(() -> r.complete(actor.userId(), conclusion, note, unanswered, no));
        reviews.save(r);
        trail.record(tenant, "AUDIT_QR_COMPLETED", "AUDIT_QUALITY_REVIEW", id, null, Map.of("conclusion", r.getConclusion(), "noAnswers", no));
        return view(t, r);
    }

    @Transactional
    public View cancel(TenantId tenant, Actor actor, UUID id, String reason) {
        UUID t = tenant.getValue();
        AuditQualityReview r = load(t, id);
        guard(() -> r.cancel(reason));
        reviews.save(r);
        trail.record(tenant, "AUDIT_QR_CANCELLED", "AUDIT_QUALITY_REVIEW", id, null, Map.of("by", actor.name()));
        return view(t, r);
    }

    @Transactional(readOnly = true)
    public Metrics metrics(TenantId tenant) {
        UUID t = tenant.getValue();
        var rs = reviews.findByTenantIdOrderByRequestedAtDesc(t);
        Map<UUID, List<AuditQualityCheck>> byReview = checks.findByTenantIdAndReviewIdIn(t, rs.stream().map(AuditQualityReview::getId).toList())
                .stream().collect(Collectors.groupingBy(AuditQualityCheck::getReviewId));
        List<ReviewIn> in = rs.stream().map(r -> new ReviewIn(r.getEngagementId(), r.getStatus(), r.getConclusion(), r.getRequestedAt(), r.getCompletedAt(),
                byReview.getOrDefault(r.getId(), List.of()).stream().filter(c -> "NO".equals(c.getResult())).map(AuditQualityCheck::getItemCode).toList())).toList();
        Set<UUID> issued = reports.findByTenantIdOrderByCreatedAtDesc(t).stream()
                .filter(x -> "ISSUED".equals(x.getStatus()) || "SUPERSEDED".equals(x.getStatus())).map(AuditReport::getEngagementId).collect(Collectors.toSet());
        return QualityMetricsBuilder.build(in, issued,
                notes.findAllForTenant(t).stream().map(n -> new NoteIn(n.getStatus())).toList(),
                findings.findAllForTenant(t).stream().map(f -> new FindingIn(f.getReopenedAt() != null)).toList());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────

    private AuditQualityReview load(UUID t, UUID id) {
        return reviews.findByTenantIdAndId(t, id).orElseThrow(() -> new ResourceNotFoundException("AuditQualityReview", id.toString()));
    }

    private Map<UUID, String> names(UUID t) {
        Map<UUID, String> m = new HashMap<>();
        engagements.findAllForTenant(t).forEach(e -> m.put(e.getId(), e.getName()));
        return m;
    }

    private Summary summary(AuditQualityReview r, List<AuditQualityCheck> answers, Map<UUID, String> names) {
        return new Summary(r.getId(), r.getEngagementId(), names.getOrDefault(r.getEngagementId(), "Engagement"), r.getReviewerName(), r.getReviewerId(), r.getRequestedByName(),
                r.getRequestedAt(), r.getStatus(), r.getConclusion(), r.getCompletedAt(), answers.size(), QualityChecklist.items().size(),
                (int) answers.stream().filter(a -> "NO".equals(a.getResult())).count());
    }

    private View view(UUID t, AuditQualityReview r) {
        var answers = checks.findByTenantIdAndReviewId(t, r.getId());
        Map<String, AuditQualityCheck> byCode = answers.stream().collect(Collectors.toMap(AuditQualityCheck::getItemCode, c -> c, (a, b) -> a));
        List<CheckView> cv = QualityChecklist.items().stream().map(i -> {
            var a = byCode.get(i.code());
            return new CheckView(i.code(), i.area(), i.question(), a == null ? null : a.getResult(), a == null ? null : a.getNote(), a == null ? null : a.getAnsweredAt());
        }).toList();
        return new View(summary(r, answers, names(t)), r.getConclusionNote(), r.getCancelReason(), cv);
    }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
    private static HandyFlowException conflict(String m, String code) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }

    private static void guard(Runnable r) {
        try { r.run(); }
        catch (IllegalArgumentException e) { throw bad(e.getMessage(), "QR_INVALID"); }
        catch (IllegalStateException e) { throw conflict(e.getMessage(), "QR_STATE"); }
    }

    private static <T> T guard(Supplier<T> s) {
        try { return s.get(); }
        catch (IllegalArgumentException e) { throw bad(e.getMessage(), "QR_INVALID"); }
        catch (IllegalStateException e) { throw conflict(e.getMessage(), "QR_STATE"); }
    }
}
