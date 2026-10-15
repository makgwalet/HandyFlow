package za.co.handyflow.platform.auditor.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Actor;
import za.co.handyflow.platform.auditor.application.internal.EvidenceRequestService.Scope;
import za.co.handyflow.platform.auditor.domain.model.AuditQuery;
import za.co.handyflow.platform.auditor.domain.repository.AuditQueryRepository;
import za.co.handyflow.platform.auditor.dto.AuditQueryDtos.*;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Audit queries and external review notes. The auditor raises and closes them; the business answers. They are kept apart from the
 * internal audit findings: linking one to a finding is the business's explicit act, and the auditor is never shown the finding.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    public static final String ENTITY_TYPE = "AUDIT_QUERY";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final AuditQueryRepository repo;
    private final AuditFindingRepository findingRepo;
    private final za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail;

    @Transactional
    public QueryView raise(TenantId tenantId, Scope scope, Actor actor, RaiseRequest req) {
        requireAuditor(actor, "raise a query or review note");
        String kind = req.kind() == null ? "QUERY" : req.kind().toUpperCase();
        String prefix = "QUERY".equals(kind) ? "Q-" : "RN-";
        String reference = prefix + String.format("%04d", repo.highestNumber(tenantId.getValue(), kind, prefix.length() + 1) + 1);
        AuditQuery q = guard(() -> AuditQuery.raise(tenantId.getValue(), scope.engagementId(), kind, reference, req.title(), req.text(),
                req.subjectType(), req.subjectId(), req.subjectLabel(), req.dueDate(), actor.id(), actor.label()));
        repo.save(q);
        record(tenantId, actor, "AUDIT_QUERY_RAISED", q, null, Map.of("reference", reference, "kind", kind, "title", q.getTitle()));
        return view(tenantId, q, actor);
    }

    @Transactional(readOnly = true)
    public List<QueryView> list(TenantId tenantId, Scope scope, Actor actor) {
        return repo.findByTenantIdOrderByCreatedAtDesc(tenantId.getValue()).stream().filter(q -> allows(scope, q))
                .map(q -> view(tenantId, q, actor)).toList();
    }

    @Transactional(readOnly = true)
    public QueryView detail(TenantId tenantId, Scope scope, Actor actor, UUID id) {
        return view(tenantId, find(tenantId, scope, id), actor);
    }

    @Transactional
    public QueryView answer(TenantId tenantId, Scope scope, Actor actor, UUID id, String text) {
        requireStaff(actor, "answer");
        AuditQuery q = find(tenantId, scope, id);
        guard(() -> { q.answer(actor.id(), actor.label(), text); return q; });
        record(tenantId, actor, "AUDIT_QUERY_ANSWERED", q, Map.of("status", "OPEN"), Map.of("status", "ANSWERED"));
        return view(tenantId, repo.save(q), actor);
    }

    @Transactional
    public QueryView followUp(TenantId tenantId, Scope scope, Actor actor, UUID id, String text) {
        requireAuditor(actor, "follow up");
        AuditQuery q = find(tenantId, scope, id);
        guard(() -> { q.followUp(actor.id(), actor.label(), text); return q; });
        record(tenantId, actor, "AUDIT_QUERY_FOLLOWED_UP", q, Map.of("status", "ANSWERED"), Map.of("status", "OPEN"));
        return view(tenantId, repo.save(q), actor);
    }

    @Transactional
    public QueryView resolve(TenantId tenantId, Scope scope, Actor actor, UUID id, String note) {
        requireAuditor(actor, "resolve");
        AuditQuery q = find(tenantId, scope, id);
        guard(() -> { q.resolve(actor.id(), actor.label(), note); return q; });
        record(tenantId, actor, "AUDIT_QUERY_RESOLVED", q, Map.of("status", "ANSWERED"), Map.of("status", "RESOLVED"));
        return view(tenantId, repo.save(q), actor);
    }

    @Transactional
    public QueryView withdraw(TenantId tenantId, Scope scope, Actor actor, UUID id, String why) {
        requireAuditor(actor, "withdraw");
        AuditQuery q = find(tenantId, scope, id);
        String before = q.getStatus();
        guard(() -> { q.withdraw(actor.id(), actor.label(), why); return q; });
        record(tenantId, actor, "AUDIT_QUERY_WITHDRAWN", q, Map.of("status", before), Map.of("status", "WITHDRAWN"));
        return view(tenantId, repo.save(q), actor);
    }

    /** The business connects a query to one of its own internal findings, or removes the connection (findingId null). */
    @Transactional
    public QueryView link(TenantId tenantId, Actor actor, UUID id, UUID findingId) {
        requireStaff(actor, "link a query to a finding");
        AuditQuery q = find(tenantId, Scope.all(), id);
        if (findingId == null) {
            q.unlinkFinding();
            record(tenantId, actor, "AUDIT_QUERY_UNLINKED", q, null, null);
        } else {
            findingRepo.findByTenantAndId(tenantId.getValue(), findingId)
                    .orElseThrow(() -> new ResourceNotFoundException("AuditFinding", findingId.toString()));
            guard(() -> { q.linkToFinding(findingId, actor.id()); return q; });
            record(tenantId, actor, "AUDIT_QUERY_LINKED", q, null, Map.of("findingId", findingId.toString()));
        }
        return view(tenantId, repo.save(q), actor);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static boolean allows(Scope scope, AuditQuery q) { return !scope.restricted() || java.util.Objects.equals(q.getEngagementId(), scope.engagementId()); }

    private AuditQuery find(TenantId tenantId, Scope scope, UUID id) {
        return repo.findByTenantIdAndId(tenantId.getValue(), id).filter(q -> allows(scope, q))
                .orElseThrow(() -> new ResourceNotFoundException("AuditQuery", id.toString()));
    }

    private static void requireStaff(Actor a, String what) {
        if (!"STAFF".equals(a.kind())) throw new HandyFlowException("Only the business can " + what, HttpStatus.FORBIDDEN, "BUSINESS_ONLY");
    }

    private static void requireAuditor(Actor a, String what) {
        if ("STAFF".equals(a.kind())) throw new HandyFlowException("Only the auditor can " + what, HttpStatus.FORBIDDEN, "AUDITOR_ONLY");
    }

    private void record(TenantId tenantId, Actor actor, String action, AuditQuery q, Map<String, ?> before, Map<String, ?> after) {
        if ("STAFF".equals(actor.kind())) trail.record(tenantId, action, ENTITY_TYPE, q.getId(), before, after);
        else trail.recordAs(tenantId, "AUDITOR", actor.id(), actor.label(), action, ENTITY_TYPE, q.getId(), before, after);
    }

    private QueryView view(TenantId tenantId, AuditQuery q, Actor viewer) {
        boolean business = "STAFF".equals(viewer.kind());
        UUID finding = business ? q.getLinkedFindingId() : null;
        String findingTitle = finding == null ? null : findingRepo.findByTenantAndId(tenantId.getValue(), finding).map(f -> f.getTitle()).orElse("Finding removed");
        return new QueryView(q.getId(), q.getKind(), q.getReference(), q.getEngagementId(), q.getTitle(), q.getSubjectType(), q.getSubjectId(),
                q.getSubjectLabel(), q.getDueDate(), q.getRaisedByLabel(), q.getStatus(), q.getTimesReopened(), q.overdueOn(LocalDate.now(SAST)),
                finding, findingTitle, q.getCreatedAt(), q.getClosedAt(),
                q.getMessages().stream().map(m -> new MessageView(m.getId(), m.getAuthorKind(), m.getAuthorLabel(), m.getMessageKind(), m.getBody(), m.getCreatedAt())).toList());
    }

    private <T> T guard(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "AUDIT_QUERY_INVALID");
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "AUDIT_QUERY_STATE");
        }
    }
}
