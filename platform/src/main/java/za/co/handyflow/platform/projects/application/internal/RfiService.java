package za.co.handyflow.platform.projects.application.internal;

import za.co.handyflow.platform.projects.application.internal.notify.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.projects.application.internal.rfi.RfiRules;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.enums.RfiStatus;
import za.co.handyflow.platform.projects.domain.model.ChangeOrder;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectRfi;
import za.co.handyflow.platform.projects.domain.model.ProjectRfiEvent;
import za.co.handyflow.platform.projects.domain.repository.ChangeOrderRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRfiEventRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRfiRepository;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * RFI workflow: raise, assign, answer, clarify, close, reopen — with a permanent history.
 *
 * CHUNK 7 — WHAT CHANGED
 *  - Lifecycle follows RfiStatus (domain methods, no free-form status setting).
 *  - Assignment: once assigned, only the assignee (or a PM_ADMIN) can answer.
 *  - Clarification loop: an answer can be sent back (RESPONDED → SUBMITTED). Each answer,
 *    clarification, reassignment, closure and reopening is appended to project_rfi_events, so a
 *    later answer never erases an earlier one; `response` always shows the latest.
 *  - Impact: an RFI can be flagged as affecting schedule and/or cost (optionally naming the task).
 *    RfiRules.closureBlocker() then refuses to close it until a LIVE change order handles it or a
 *    reason for "no change needed" is recorded — closing can't quietly hide a downstream impact.
 *  - Reopening a CLOSED RFI (with a reason), due-date validation on create, per-project summary
 *    with ageing, and overdue escalation (RfiEscalationScheduler).
 *  - Project state: creating an RFI needs an editable project; everything else is allowed until the
 *    project is CLOSED.
 *
 * NOT IN THIS CHUNK: external consultant/client access to RFIs, and submittals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfiService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ProjectRfiRepository      rfiRepo;
    private final ProjectRfiEventRepository eventRepo;
    private final ProjectRepository         projectRepo;
    private final ChangeOrderRepository     changeOrderRepo;
    private final SequenceService           sequenceService;
    private final PmNotificationService     notificationService;
    private final RfiNotificationService    rfiNotifications;
    private final EvidenceFacade            evidenceFacade;
    private final ProjectReferenceValidator referenceValidator;
    private final ProjectNotifier           notifier;

    @Value("${app.projects.attachments.max-bytes:20971520}")
    private long maxAttachmentBytes;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RfiResponse> getRfis(UUID projectId, String status, boolean assignedToMe, boolean overdueOnly) {
        UUID tenantId = tenantId();
        verifyProject(projectId, tenantId);

        List<ProjectRfi> rfis;
        if (status != null && !status.isBlank()) {
            RfiStatus s;
            try { s = RfiStatus.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException e) { throw bad("Unknown RFI status: " + status, "BAD_REQUEST"); }
            rfis = rfiRepo.findByProjectAndStatus(projectId, tenantId, s.name());
        } else {
            rfis = rfiRepo.findByProject(projectId, tenantId);
        }
        UUID me = TenantContext.getCurrentUserId();
        return rfis.stream()
                .filter(r -> !assignedToMe || (me != null && me.equals(r.getAssignedToId())))
                .filter(r -> !overdueOnly || r.isOverdue())
                .map(r -> toResponse(r, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public RfiResponse getRfi(UUID rfiId) {
        UUID tenantId = tenantId();
        ProjectRfi r = getVerified(rfiId, tenantId);
        return toResponse(r, blockerOf(tenantId, r));
    }

    @Transactional(readOnly = true)
    public List<RfiEventResponse> getEvents(UUID rfiId) {
        UUID tenantId = tenantId();
        ProjectRfi r = getVerified(rfiId, tenantId);
        return eventRepo.findByRfi(tenantId, r.getId()).stream().map(RfiEventResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public RfiSummaryResponse summary(UUID projectId) {
        UUID tenantId = tenantId();
        verifyProject(projectId, tenantId);
        List<ProjectRfi> rfis = rfiRepo.findByProject(projectId, tenantId);
        LocalDate today = LocalDate.now(SAST);

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (RfiStatus s : RfiStatus.values()) byStatus.put(s.name(), 0);
        Map<String, Integer> ageing = new LinkedHashMap<>();
        for (String b : List.of("0-7", "8-14", "15-30", "30+")) ageing.put(b, 0);

        int overdue = 0, unresolvedImpact = 0;
        long responseDaysTotal = 0; int answered = 0;
        for (ProjectRfi r : rfis) {
            byStatus.merge(r.getStatus(), 1, Integer::sum);
            if (r.isOverdue()) overdue++;
            if (r.getRespondedDate() != null) {
                responseDaysTotal += ChronoUnit.DAYS.between(r.getRequestedDate(), r.getRespondedDate());
                answered++;
            }
            if (r.statusEnum().isAwaitingResponse()) {
                ageing.merge(RfiRules.ageBucket(ChronoUnit.DAYS.between(r.getRequestedDate(), today)), 1, Integer::sum);
            }
            if (r.statusEnum() == RfiStatus.RESPONDED && blockerOf(tenantId, r) != null) unresolvedImpact++;
        }
        return new RfiSummaryResponse(projectId, rfis.size(), byStatus,
                byStatus.get("SUBMITTED"), byStatus.get("RESPONDED"), overdue, unresolvedImpact,
                answered == 0 ? null : Math.round(responseDaysTotal * 10.0 / answered) / 10.0, ageing);
    }

    // ── Create / submit ───────────────────────────────────────────────────────

    @Transactional
    public RfiResponse createRfi(UUID projectId, CreateRfiRequest req) {
        UUID tenantId = tenantId();
        Project project = verifyProject(projectId, tenantId);
        if (!project.getStatus().isEditable()) {
            throw new HandyFlowException("Project is " + project.getStatus() + " and can no longer take new RFIs",
                    HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE");
        }
        LocalDate requested = req.requestedDate() != null ? req.requestedDate() : LocalDate.now(SAST);
        if (req.dueDate() != null && req.dueDate().isBefore(requested)) {
            throw bad("The due date cannot be before the date the RFI was requested", "INVALID_DATES");
        }

        int seq = sequenceService.next(tenantId, "RFI:" + projectId);
        String rfiNumber = "RFI-" + String.format("%03d", seq);
        ProjectRfi rfi = ProjectRfi.create(tenantId, projectId, rfiNumber, req.title(), req.description(),
                req.category(),
                req.requestedBy() != null ? req.requestedBy() : TenantContext.getCurrentUserName(),
                req.requestedById(), requested, req.dueDate());
        if (req.submitImmediately()) rfi.submit();

        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "CREATED", null);
        if (req.submitImmediately()) {
            record(saved, "SUBMITTED", null);
            notificationService.notifyRfiSubmitted(tenantId, project.getName(), rfiNumber, saved.getTitle());
        }
        log.info("Created RFI={} number={} project={} status={}", saved.getId(), rfiNumber, projectId, saved.getStatus());
        return toResponse(saved, null);
    }

    @Transactional
    public RfiResponse submit(UUID rfiId) {
        UUID tenantId = tenantId();
        ProjectRfi rfi = getForUpdate(rfiId, tenantId);
        Project project = verifyOpenProject(rfi.getProjectId(), tenantId);

        rfi.submit();
        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "SUBMITTED", null);
        notificationService.notifyRfiSubmitted(tenantId, project.getName(), rfi.getRfiNumber(), rfi.getTitle());
        return toResponse(saved, null);
    }

    // ── Assign / respond / clarify ────────────────────────────────────────────

    @Transactional
    public RfiResponse assign(UUID rfiId, AssignRfiRequest req) {
        UUID tenantId = tenantId();
        ProjectRfi rfi = getForUpdate(rfiId, tenantId);
        Project project = verifyOpenProject(rfi.getProjectId(), tenantId);

        String previous = rfi.getAssignedToName();
        rfi.assign(req.assigneeId(), blankToNull(req.assigneeName()));
        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "ASSIGNED", "Assigned to " + displayName(req.assigneeName(), req.assigneeId())
                + (previous != null ? " (previously " + previous + ")" : ""));

        if (saved.statusEnum() == RfiStatus.SUBMITTED) {       // only when an answer is actually being asked for
            rfiNotifications.notifyAssigned(tenantId, project.getName(), saved.getRfiNumber(), saved.getTitle(),
                    req.assigneeId(), TenantContext.getCurrentUserName());
        }
        return toResponse(saved, null);
    }

    @Transactional
    public RfiResponse respond(UUID rfiId, RespondRfiRequest req) {
        UUID tenantId = tenantId();
        ProjectRfi rfi = getForUpdate(rfiId, tenantId);
        Project project = verifyOpenProject(rfi.getProjectId(), tenantId);

        UUID userId = TenantContext.getCurrentUserId();
        String userName = TenantContext.getCurrentUserName();
        if (rfi.getAssignedToId() != null && !rfi.getAssignedToId().equals(userId) && !isAdmin()) {
            throw new HandyFlowException(
                    "This RFI is assigned to " + (rfi.getAssignedToName() != null ? rfi.getAssignedToName() : "someone else")
                            + " — only they (or an administrator) can respond",
                    HttpStatus.FORBIDDEN, "NOT_ASSIGNEE");
        }

        rfi.respond(userName, userId, req.response());
        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "RESPONDED", req.response());              // full text kept in the history
        notificationService.notifyRfiResponded(tenantId, project.getName(), rfi.getRfiNumber(), rfi.getTitle(), userName);
        notifier.publish(ProjectEvent.to(tenantId, rfi.getProjectId(), PmEvent.RFI_ANSWERED,
                rfi.getRfiNumber() + " was answered — " + project.getName(),
                rfi.getTitle() + ". Answered by " + userName + ". Close it if the answer is enough, or ask for clarification.",
                rfi.getId(), rfi.getRequestedById(), userId));
        log.info("Responded to RFI={} by={}", rfiId, userName);
        return toResponse(saved, blockerOf(tenantId, saved));
    }

    /** RESPONDED → SUBMITTED: the answer wasn't enough. The earlier answer stays in the history. */
    @Transactional
    public RfiResponse requestClarification(UUID rfiId, String comment) {
        UUID tenantId = tenantId();
        ProjectRfi rfi = getForUpdate(rfiId, tenantId);
        Project project = verifyOpenProject(rfi.getProjectId(), tenantId);

        UUID responder = rfi.getAssignedToId() != null ? rfi.getAssignedToId() : rfi.getRespondedById();
        rfi.requestClarification();
        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "CLARIFICATION_REQUESTED", comment);
        rfiNotifications.notifyClarification(tenantId, project.getName(), saved.getRfiNumber(), saved.getTitle(),
                comment, responder);
        return toResponse(saved, null);
    }

    // ── Close / reopen / cancel ───────────────────────────────────────────────

    @Transactional
    public RfiResponse close(UUID rfiId, String noChangeNote) {
        UUID tenantId = tenantId();
        ProjectRfi rfi = getForUpdate(rfiId, tenantId);
        verifyOpenProject(rfi.getProjectId(), tenantId);

        String note = blankToNull(noChangeNote) != null ? noChangeNote.trim() : rfi.getNoChangeNote();
        Optional<String> blocker = RfiRules.closureBlocker(
                rfi.isImpactsSchedule(), rfi.isImpactsCost(), changeOrderStatus(tenantId, rfi), note);
        if (blocker.isPresent()) {
            throw new HandyFlowException(blocker.get(), HttpStatus.CONFLICT, "UNRESOLVED_IMPACT");
        }

        rfi.close(noChangeNote);
        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "CLOSED", blankToNull(noChangeNote));
        log.info("Closed RFI={}", rfiId);
        return toResponse(saved, null);
    }

    @Transactional
    public RfiResponse reopen(UUID rfiId, String reason) {
        UUID tenantId = tenantId();
        ProjectRfi rfi = getForUpdate(rfiId, tenantId);
        verifyOpenProject(rfi.getProjectId(), tenantId);
        rfi.reopen();
        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "REOPENED", reason);
        log.info("Reopened RFI={}", rfiId);
        return toResponse(saved, blockerOf(tenantId, saved));
    }

    @Transactional
    public RfiResponse cancel(UUID rfiId, String reason) {
        UUID tenantId = tenantId();
        ProjectRfi rfi = getForUpdate(rfiId, tenantId);
        verifyOpenProject(rfi.getProjectId(), tenantId);
        rfi.cancel(reason);
        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "CANCELLED", reason);
        log.info("Cancelled RFI={} reason={}", rfiId, reason);
        return toResponse(saved, null);
    }

    // ── Impact / change order ─────────────────────────────────────────────────

    @Transactional
    public RfiResponse updateImpact(UUID rfiId, RfiImpactRequest req) {
        UUID tenantId = tenantId();
        ProjectRfi rfi = getForUpdate(rfiId, tenantId);
        verifyOpenProject(rfi.getProjectId(), tenantId);
        referenceValidator.requireTaskInProject(TenantId.of(tenantId), rfi.getProjectId(), req.taskId());

        rfi.updateImpact(req.taskId(), req.impactsSchedule(), req.impactsCost(), blankToNull(req.notes()));
        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "IMPACT_UPDATED", "schedule=" + req.impactsSchedule() + ", cost=" + req.impactsCost()
                + (blankToNull(req.notes()) != null ? " — " + req.notes().trim() : ""));
        return toResponse(saved, blockerOf(tenantId, saved));
    }

    /** Same tenant AND same project; another tenant's change order looks identical to a missing one. */
    @Transactional
    public RfiResponse linkChangeOrder(UUID rfiId, UUID changeOrderId) {
        UUID tenantId = tenantId();
        ProjectRfi rfi = getForUpdate(rfiId, tenantId);
        verifyOpenProject(rfi.getProjectId(), tenantId);

        ChangeOrder co = changeOrderRepo.findByTenantAndId(tenantId, changeOrderId)
                .orElseThrow(() -> notFound("Change Order"));
        if (!rfi.getProjectId().equals(co.getProjectId())) {
            throw new HandyFlowException("Change Order belongs to a different project than this RFI",
                    HttpStatus.BAD_REQUEST, "PROJECT_MISMATCH");
        }
        rfi.linkChangeOrder(changeOrderId);
        ProjectRfi saved = rfiRepo.save(rfi);
        record(saved, "CHANGE_ORDER_LINKED", co.getChangeNumber());
        return toResponse(saved, blockerOf(tenantId, saved));
    }

    // ── Evidence ──────────────────────────────────────────────────────────────

    @Transactional
    public EvidenceResponse attachEvidence(UUID rfiId, MultipartFile file, UUID uploadedBy, String uploadedByName) {
        if (file == null || file.isEmpty()) {
            throw new HandyFlowException("A non-empty file is required", HttpStatus.BAD_REQUEST, "FILE_REQUIRED");
        }
        if (file.getSize() > maxAttachmentBytes) {
            throw new HandyFlowException(
                    "File exceeds the maximum allowed size of " + (maxAttachmentBytes / (1024 * 1024)) + " MB",
                    HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        }
        TenantId tenantId = TenantId.of(tenantId());
        ProjectRfi rfi = getVerified(rfiId, tenantId.getValue());
        return evidenceFacade.attach(tenantId, file, "RFI_ATTACHMENT", "projects", "ProjectRfi",
                rfi.getId(), null, uploadedBy, uploadedByName);
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getAttachments(UUID rfiId) {
        TenantId tenantId = TenantId.of(tenantId());
        ProjectRfi rfi = getVerified(rfiId, tenantId.getValue());
        return evidenceFacade.listFor(tenantId, "projects", "ProjectRfi", rfi.getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void record(ProjectRfi rfi, String type, String comment) {
        eventRepo.save(ProjectRfiEvent.record(rfi.getTenantId(), rfi.getProjectId(), rfi.getId(), type,
                comment, currentUserIdOrNull(), TenantContext.getCurrentUserName()));
    }

    /** Why this RFI cannot be closed right now, or null. Only meaningful once it has been answered. */
    private String blockerOf(UUID tenantId, ProjectRfi r) {
        if (r.statusEnum() != RfiStatus.RESPONDED) return null;
        return RfiRules.closureBlocker(r.isImpactsSchedule(), r.isImpactsCost(),
                changeOrderStatus(tenantId, r), r.getNoChangeNote()).orElse(null);
    }

    private String changeOrderStatus(UUID tenantId, ProjectRfi r) {
        if (r.getChangeOrderId() == null) return null;
        return changeOrderRepo.findByTenantAndId(tenantId, r.getChangeOrderId()).map(ChangeOrder::getStatus).orElse(null);
    }

    private ProjectRfi getForUpdate(UUID rfiId, UUID tenantId) {
        return rfiRepo.findByIdAndTenantIdForUpdate(rfiId, tenantId).orElseThrow(() -> notFound("RFI"));
    }

    private ProjectRfi getVerified(UUID rfiId, UUID tenantId) {
        return rfiRepo.findByIdAndTenantId(rfiId, tenantId).orElseThrow(() -> notFound("RFI"));
    }

    private Project verifyProject(UUID projectId, UUID tenantId) {
        return projectRepo.findByTenantAndId(tenantId, projectId).orElseThrow(() -> notFound("Project"));
    }

    /** Everything except creating a new RFI stays possible until the project is CLOSED. */
    private Project verifyOpenProject(UUID projectId, UUID tenantId) {
        Project p = verifyProject(projectId, tenantId);
        if (p.getStatus() == ProjectStatus.CLOSED) {
            throw new HandyFlowException("Project is CLOSED — its RFIs can no longer be changed",
                    HttpStatus.CONFLICT, "PROJECT_CLOSED");
        }
        return p;
    }

    private static boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "PM_ADMIN".equals(a.getAuthority()));
    }

    private static UUID tenantId() { return UUID.fromString(TenantContext.getTenantId()); }

    private static UUID currentUserIdOrNull() {
        String id = TenantContext.getUserId();
        return id != null ? UUID.fromString(id) : null;
    }

    private static String displayName(String name, UUID id) {
        return (name != null && !name.isBlank()) ? name.trim() : String.valueOf(id);
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
    }

    private static HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private RfiResponse toResponse(ProjectRfi r, String closureBlocker) {
        return new RfiResponse(
                r.getId(), r.getProjectId(), r.getRfiNumber(),
                r.getTitle(), r.getDescription(), r.getCategory(),
                r.getRequestedBy(), r.getRequestedById(), r.getRequestedDate(), r.getDueDate(),
                r.getAssignedToId(), r.getAssignedToName(), r.getAssignedAt(),
                r.getRespondedBy(), r.getRespondedById(), r.getRespondedDate(), r.getResponse(),
                r.getClarificationCount(),
                r.getStatus(), r.isOverdue(), r.daysUntilDue(), r.getEscalationLevel(),
                r.getImpactTaskId(), r.isImpactsSchedule(), r.isImpactsCost(), r.getImpactNotes(), r.getNoChangeNote(),
                r.getChangeOrderId(), closureBlocker,
                r.getClosedAt(), r.getCancelledAt(), r.getCancellationReason(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}