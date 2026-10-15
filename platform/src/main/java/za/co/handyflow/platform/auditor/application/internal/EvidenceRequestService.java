package za.co.handyflow.platform.auditor.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.auditor.domain.model.EvidenceRequest;
import za.co.handyflow.platform.auditor.domain.model.EvidenceRequestComment;
import za.co.handyflow.platform.auditor.domain.model.EvidenceRequestRound;
import za.co.handyflow.platform.auditor.domain.repository.EvidenceRequestCommentRepository;
import za.co.handyflow.platform.auditor.domain.repository.EvidenceRequestRepository;
import za.co.handyflow.platform.auditor.domain.repository.ExternalAuditEngagementRepository;
import za.co.handyflow.platform.auditor.dto.EvidenceRequestDtos.*;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Evidence requests between the business and its external auditors. The same rules apply whoever calls: the business side answers,
 * the auditor side decides, and an auditor only ever sees requests of the engagement their access belongs to.
 */
@Service
@RequiredArgsConstructor
public class EvidenceRequestService {

    public static final String SOURCE_MODULE = "auditor";
    public static final String ENTITY_TYPE = "AUDIT_EVIDENCE_REQUEST";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final EvidenceRequestRepository repo;
    private final EvidenceRequestCommentRepository commentRepo;
    private final ExternalAuditEngagementRepository engagementRepo;
    private final EvidenceFacade evidenceFacade;
    private final UserRepository userRepo;
    private final za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail;

    /** Who is acting: the business's staff, or an auditor in the portal. */
    public record Actor(String kind, UUID id, String label) {
        public static Actor staff(UUID id, String name) { return new Actor("STAFF", id, name); }
        public static Actor auditor(UUID id, String email) { return new Actor("AUDITOR", id, email); }
        boolean isStaff() { return "STAFF".equals(kind); }
    }

    /** What the caller may see. Staff see everything; an auditor sees only the engagement their access belongs to. */
    public record Scope(boolean restricted, UUID engagementId) {
        public static Scope all() { return new Scope(false, null); }
        public static Scope engagement(UUID engagementId) { return new Scope(true, engagementId); }
        boolean allows(EvidenceRequest r) { return !restricted || Objects.equals(r.getEngagementId(), engagementId); }
    }

    @Transactional
    public RequestView create(TenantId tenantId, Scope scope, Actor actor, CreateRequest req) {
        UUID engagementId = scope.restricted() ? scope.engagementId() : req.engagementId();
        if (actor.isStaff() && engagementId != null) {
            var eng = engagementRepo.findByTenantIdAndId(tenantId.getValue(), engagementId)
                    .orElseThrow(() -> new ResourceNotFoundException("ExternalAuditEngagement", engagementId.toString()));
            if (!eng.acceptsInvites()) throw new HandyFlowException("That engagement has finished", HttpStatus.CONFLICT, "ENGAGEMENT_CLOSED");
        }
        UUID assignee = actor.isStaff() ? req.assignedTo() : null;
        String reference = String.format("ER-%04d", repo.highestNumber(tenantId.getValue()) + 1);
        EvidenceRequest r = guard(() -> EvidenceRequest.create(tenantId.getValue(), engagementId, reference, req.title(), req.description(),
                req.priority(), req.dueDate(), assignee, actor.kind(), actor.id(), actor.label()));
        repo.save(r);
        record(tenantId, actor, "EVIDENCE_REQUESTED", r, null, Map.of("reference", reference, "title", r.getTitle(), "priority", r.getPriority()));
        return view(tenantId, r, false);
    }

    @Transactional(readOnly = true)
    public List<RequestView> list(TenantId tenantId, Scope scope) {
        return repo.findByTenantIdOrderByCreatedAtDesc(tenantId.getValue()).stream().filter(scope::allows)
                .map(r -> view(tenantId, r, false)).toList();
    }

    @Transactional(readOnly = true)
    public RequestView detail(TenantId tenantId, Scope scope, UUID id) {
        return view(tenantId, find(tenantId, scope, id), true);
    }

    @Transactional
    public RequestView assign(TenantId tenantId, Scope scope, Actor actor, UUID id, UUID assignee, LocalDate dueDate) {
        requireStaff(actor, "assign requests");
        EvidenceRequest r = find(tenantId, scope, id);
        guard(() -> { if (assignee != null) r.assign(assignee); if (dueDate != null) r.changeDueDate(dueDate); return r; });
        record(tenantId, actor, "EVIDENCE_REQUEST_ASSIGNED", r, null, Map.of("assignedTo", String.valueOf(assignee), "dueDate", String.valueOf(dueDate)));
        return view(tenantId, repo.save(r), false);
    }

    @Transactional
    public RequestView comment(TenantId tenantId, Scope scope, Actor actor, UUID id, String body) {
        EvidenceRequest r = find(tenantId, scope, id);
        if (r.isFinished()) throw new HandyFlowException("This request is finished", HttpStatus.CONFLICT, "REQUEST_FINISHED");
        guard(() -> commentRepo.save(EvidenceRequestComment.of(tenantId.getValue(), id, actor.kind(), actor.id(), actor.label(), body)));
        return view(tenantId, r, true);
    }

    /** Sends the files attached since the last round. Files are attached through the shared evidence endpoint against this request. */
    @Transactional
    public RequestView submit(TenantId tenantId, Scope scope, Actor actor, UUID id, String note) {
        requireStaff(actor, "send files");
        EvidenceRequest r = find(tenantId, scope, id);
        Set<UUID> sent = r.allSentEvidence();
        Set<UUID> fresh = evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, id).stream()
                .map(EvidenceResponse::id).filter(e -> !sent.contains(e)).collect(Collectors.toCollection(LinkedHashSet::new));
        guard(() -> r.submit(actor.id(), note, fresh));
        record(tenantId, actor, "EVIDENCE_REQUEST_SUBMITTED", r, Map.of("status", "OPEN"), Map.of("status", "SUBMITTED", "files", fresh.size()));
        return view(tenantId, repo.save(r), true);
    }

    @Transactional
    public RequestView notAvailable(TenantId tenantId, Scope scope, Actor actor, UUID id, String why) {
        requireStaff(actor, "answer requests");
        EvidenceRequest r = find(tenantId, scope, id);
        guard(() -> r.markNotAvailable(actor.id(), why));
        record(tenantId, actor, "EVIDENCE_REQUEST_NOT_AVAILABLE", r, Map.of("status", "OPEN"), Map.of("status", "NOT_AVAILABLE", "reason", why.trim()));
        return view(tenantId, repo.save(r), true);
    }

    @Transactional
    public RequestView approve(TenantId tenantId, Scope scope, Actor actor, UUID id, String note) {
        requireAuditor(actor, "approve");
        EvidenceRequest r = find(tenantId, scope, id);
        String before = r.getStatus();
        guard(() -> { r.approve(actor.id(), note); return r; });
        record(tenantId, actor, "EVIDENCE_REQUEST_APPROVED", r, Map.of("status", before), Map.of("status", r.getStatus()));
        return view(tenantId, repo.save(r), true);
    }

    @Transactional
    public RequestView reject(TenantId tenantId, Scope scope, Actor actor, UUID id, String why) {
        requireAuditor(actor, "send back");
        EvidenceRequest r = find(tenantId, scope, id);
        String before = r.getStatus();
        guard(() -> { r.reject(actor.id(), why); return r; });
        record(tenantId, actor, "EVIDENCE_REQUEST_SENT_BACK", r, Map.of("status", before), Map.of("status", "OPEN", "reason", why.trim()));
        return view(tenantId, repo.save(r), true);
    }

    /** Only the side that raised a request can cancel it. */
    @Transactional
    public RequestView cancel(TenantId tenantId, Scope scope, Actor actor, UUID id, String why) {
        EvidenceRequest r = find(tenantId, scope, id);
        if (!r.getRequestedByKind().equals(actor.kind())) {
            throw new HandyFlowException("Only the side that raised a request can cancel it", HttpStatus.FORBIDDEN, "NOT_YOUR_REQUEST");
        }
        String before = r.getStatus();
        guard(() -> { r.cancel(why); return r; });
        record(tenantId, actor, "EVIDENCE_REQUEST_CANCELLED", r, Map.of("status", before), Map.of("status", "CANCELLED", "reason", why.trim()));
        return view(tenantId, repo.save(r), true);
    }

    /** A file may be downloaded through a request only if it was sent in one of that request's rounds. */
    @Transactional(readOnly = true)
    public EvidenceFacade.DownloadedEvidence download(TenantId tenantId, Scope scope, UUID id, UUID evidenceId) {
        EvidenceRequest r = find(tenantId, scope, id);
        if (!r.allSentEvidence().contains(evidenceId)) throw new ResourceNotFoundException("Evidence", evidenceId.toString());
        return evidenceFacade.download(tenantId, evidenceId);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private EvidenceRequest find(TenantId tenantId, Scope scope, UUID id) {
        return repo.findByTenantIdAndId(tenantId.getValue(), id).filter(scope::allows)
                .orElseThrow(() -> new ResourceNotFoundException("EvidenceRequest", id.toString()));
    }

    private static void requireStaff(Actor a, String what) {
        if (!a.isStaff()) throw new HandyFlowException("Only the business can " + what, HttpStatus.FORBIDDEN, "BUSINESS_ONLY");
    }

    private static void requireAuditor(Actor a, String what) {
        if (a.isStaff()) throw new HandyFlowException("Only the auditor can " + what + " an answer", HttpStatus.FORBIDDEN, "AUDITOR_ONLY");
    }

    private void record(TenantId tenantId, Actor actor, String action, EvidenceRequest r, Map<String, ?> before, Map<String, ?> after) {
        if (actor.isStaff()) trail.record(tenantId, action, ENTITY_TYPE, r.getId(), before, after);
        else trail.recordAs(tenantId, "AUDITOR", actor.id(), actor.label(), action, ENTITY_TYPE, r.getId(), before, after);
    }

    private RequestView view(TenantId tenantId, EvidenceRequest r, boolean full) {
        LocalDate today = LocalDate.now(SAST);
        String assignee = r.getAssignedTo() == null ? null
                : userRepo.findById(r.getAssignedTo()).map(u -> (u.getFirstName() + " " + u.getLastName()).trim()).orElse("Unknown");
        List<RoundView> rounds = List.of();
        List<CommentView> comments = List.of();
        if (full) {
            Set<UUID> all = r.allSentEvidence();
            Map<UUID, String> names = all.isEmpty() ? Map.of() : evidenceFacade.listByIds(tenantId, all).stream()
                    .collect(Collectors.toMap(EvidenceResponse::id, EvidenceResponse::fileName, (a, b) -> a));
            rounds = r.getRounds().stream().map(x -> new RoundView(x.getRoundNo(), x.getKind(), x.getNote(), x.getSubmittedAt(), x.getDecision(),
                    x.getDecisionNote(), x.getDecidedAt(),
                    x.getEvidenceIds().stream().map(e -> new FileView(e, names.getOrDefault(e, "File removed"))).toList())).toList();
            comments = commentRepo.findByRequestIdOrderByCreatedAtAsc(r.getId()).stream()
                    .map(c -> new CommentView(c.getId(), c.getAuthorKind(), c.getAuthorLabel(), c.getBody(), c.getCreatedAt())).toList();
        }
        return new RequestView(r.getId(), r.getReference(), r.getEngagementId(), r.getTitle(), r.getDescription(), r.getPriority(), r.getDueDate(),
                r.getAssignedTo(), assignee, r.getRequestedByKind(), r.getRequestedByLabel(), r.getStatus(), r.getTimesRejected(),
                r.overdueOn(today), r.dueSoonOn(today), r.getCancelReason(), r.getCreatedAt(), r.getClosedAt(), rounds, comments);
    }

    private <T> T guard(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "EVIDENCE_REQUEST_INVALID");
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "EVIDENCE_REQUEST_STATE");
        }
    }
}
