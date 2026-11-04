package za.co.handyflow.platform.projects.application.internal.clientportal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.projects.application.internal.*;
import za.co.handyflow.platform.projects.application.internal.document.DocumentRules;
import za.co.handyflow.platform.projects.application.internal.notify.PmEvent;
import za.co.handyflow.platform.projects.application.internal.notify.ProjectEvent;
import za.co.handyflow.platform.projects.application.internal.notify.ProjectNotifier;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;
import za.co.handyflow.platform.projects.domain.model.ChangeOrder;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalGrant;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalRequest;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalUpload;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalGrantRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalRequestRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalUploadRepository;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.projects.dto.ClientPortalDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * What a logged-in client contact sees and does. Every call re-checks the caller's grants ({@link ClientPortalAccess}).
 * Clients can approve or dispute change orders that were approved internally, send questions, change requests and
 * new-project requests, and send files. Nothing a client sends changes the project by itself: requests and files land
 * in the project team's inbox to accept or decline.
 */
@Slf4j
@Service("projectsClientPortalService")
@RequiredArgsConstructor
public class ClientPortalService {

    /** Restricted evidence: only this module can read it, and the generic evidence screens never list it. */
    static final String EVIDENCE_MODULE = EvidenceFacade.RESTRICTED_PREFIX + "projects-portal";
    static final String EVIDENCE_ENTITY = "PortalUpload";

    private final ClientPortalAccess access;
    private final ProjectPortalGrantRepository grants;
    private final ProjectPortalRequestRepository requests;
    private final ProjectPortalUploadRepository uploads;
    private final ProjectService projectService;
    private final FieldService fieldService;
    private final DocumentService documentService;
    private final ChangeOrderService changeOrderService;
    private final SequenceService sequenceService;
    private final ProjectActivityService activity;
    private final EvidenceFacade evidence;
    private final ProjectNotifier notifier;

    @Value("${app.projects.client-portal.max-upload-bytes:10485760}")
    private long maxUploadBytes;

    // ── Reading ───────────────────────────────────────────────────────────────

    @Transactional
    public Me me(UUID portalUserId) {
        PortalUser u = access.user(portalUserId);
        List<ProjectPortalGrant> mine = access.grants(portalUserId);
        Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
        for (ProjectPortalGrant g : mine)
            if (g.getLastSeenAt() == null || g.getLastSeenAt().isBefore(hourAgo)) { g.seen(); grants.save(g); }
        return new Me(u.getFullName(), u.getEmail(), mine.stream().map(g -> new Account(g.getId(), g.getClientName())).toList());
    }

    @Transactional(readOnly = true)
    public List<MyProject> myProjects(UUID portalUserId) {
        return access.visible(portalUserId).stream().map(v -> summary(v.project())).toList();
    }

    @Transactional(readOnly = true)
    public ProjectView project(UUID portalUserId, UUID projectId) {
        Project p = access.require(portalUserId, projectId).project();
        List<PortalMilestoneResponse> milestones = projectService.getMilestonesForPortal(p).stream().map(PortalMilestoneResponse::of).toList();
        List<PortalRiskResponse> red = projectService.getRisksForPortal(p).stream()
                .filter(r -> "RED".equals(r.getRating()) && "OPEN".equals(r.getStatus())).map(PortalRiskResponse::of).toList();
        return new ProjectView(summary(p), p.getSiteAddress(), milestones,
                fieldService.getSnagsForPortal(p, true).stream().map(PortalSnagResponse::of).toList(), red,
                documentService.getClientVisibleForPortal(p).stream().map(PortalDocumentResponse::of).toList(),
                changeOrders(p).stream().map(ClientPortalService::clientView).toList(),
                requests.findByProject(p.getTenantId(), p.getId()).stream().map(ClientPortalService::requestView).toList(),
                uploads.findByProject(p.getTenantId(), p.getId()).stream().map(ClientPortalService::uploadView).toList());
    }

    @Transactional
    public DocumentService.FileDownload download(UUID portalUserId, UUID projectId, UUID documentId) {
        return documentService.downloadForPortal(access.require(portalUserId, projectId).project(), documentId);
    }

    /** New-project requests the caller's company sent (they belong to no project yet). */
    @Transactional(readOnly = true)
    public List<RequestView> newProjectRequests(UUID portalUserId) {
        List<UUID> ids = access.grants(portalUserId).stream().map(ProjectPortalGrant::getId).toList();
        return requests.findByGrants(ids).stream().filter(r -> r.getProjectId() == null).map(ClientPortalService::requestView).toList();
    }

    // ── Change orders ─────────────────────────────────────────────────────────

    @Transactional
    public ClientChangeOrder approveChangeOrder(UUID portalUserId, UUID projectId, UUID changeOrderId) {
        ClientPortalAccess.Visible v = access.require(portalUserId, projectId);
        PortalUser u = access.user(portalUserId);
        ChangeOrder co = approvedChangeOrder(v.project(), changeOrderId);
        ChangeOrder saved = changeOrderService.markClientApproved(TenantId.of(v.project().getTenantId()), co.getId());
        afterClientDecision(v.project(), saved, u, "approved", null);
        return clientView(saved);
    }

    @Transactional
    public ClientChangeOrder disputeChangeOrder(UUID portalUserId, UUID projectId, UUID changeOrderId, String reason) {
        ClientPortalAccess.Visible v = access.require(portalUserId, projectId);
        PortalUser u = access.user(portalUserId);
        ChangeOrder co = approvedChangeOrder(v.project(), changeOrderId);
        ChangeOrder saved = changeOrderService.raiseClientDispute(TenantId.of(v.project().getTenantId()), co.getId(), reason);
        afterClientDecision(v.project(), saved, u, "disputed", reason.trim());
        return clientView(saved);
    }

    private void afterClientDecision(Project p, ChangeOrder co, PortalUser u, String verb, String reason) {
        activity.record(p.getTenantId(), p.getId(), "CLIENT_CO_" + verb.toUpperCase(Locale.ROOT),
                "Client " + verb + " " + co.getChangeNumber() + " " + co.getTitle(), reason, null, actor(u));
        Set<UUID> users = new HashSet<>();
        if (co.getSubmittedBy() != null) users.add(co.getSubmittedBy());
        if (p.getProjectManagerId() != null) users.add(p.getProjectManagerId());
        notifier.publish(new ProjectEvent(p.getTenantId(), p.getId(), PmEvent.CLIENT_CO_DECISION,
                "Client " + verb + " " + co.getChangeNumber(),
                u.getFullName() + " " + verb + " " + co.getChangeNumber() + " " + co.getTitle() + " on " + p.getProjectNumber()
                        + (reason == null ? "" : ": " + reason),
                co.getId(), users, Set.of(ProjectMemberRole.MANAGER), null));
    }

    private ChangeOrder approvedChangeOrder(Project p, UUID id) {
        return changeOrders(p).stream().filter(c -> c.getId().equals(id)).findFirst()
                .orElseThrow(() -> new HandyFlowException("Change order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /** Only change orders approved internally are ever shown to the client. */
    private List<ChangeOrder> changeOrders(Project p) {
        return changeOrderService.getChangeOrders(TenantId.of(p.getTenantId()), p.getId()).stream()
                .filter(c -> "APPROVED".equals(c.getStatus())).toList();
    }

    // ── Requests ──────────────────────────────────────────────────────────────

    @Transactional
    public RequestView raise(UUID portalUserId, RaiseRequest req) {
        PortalUser u = access.user(portalUserId);
        ProjectPortalGrant g;
        Project p = null;
        if ("NEW_PROJECT".equals(req.kind())) {
            if (req.projectId() != null) throw new HandyFlowException("A new-project request is not about an existing project", HttpStatus.BAD_REQUEST, "PROJECT_NOT_EXPECTED");
            g = access.requireGrant(portalUserId, req.grantId());
        } else {
            if (req.projectId() == null) throw new HandyFlowException("Choose the project", HttpStatus.BAD_REQUEST, "PROJECT_REQUIRED");
            ClientPortalAccess.Visible v = access.require(portalUserId, req.projectId());
            g = v.grant(); p = v.project();
        }
        if (req.neededBy() != null && req.neededBy().isBefore(LocalDate.now()))
            throw new HandyFlowException("The needed-by date is in the past", HttpStatus.BAD_REQUEST, "INVALID_DATE");
        String number = "CR-" + String.format("%04d", sequenceService.next(g.getTenantId(), "CLIENT_REQUEST"));
        ProjectPortalRequest r = requests.save(ProjectPortalRequest.raise(g.getTenantId(), p == null ? null : p.getId(), g.getId(), number,
                req.kind(), req.title(), req.description(), req.neededBy(), u.getFullName(), u.getEmail()));
        String what = switch (r.getKind()) { case "QUESTION" -> "a question"; case "CHANGE" -> "a change request"; default -> "a new-project request"; };
        if (p != null) {
            activity.record(p.getTenantId(), p.getId(), "CLIENT_REQUEST", "Client sent " + what + ": " + number + " " + r.getTitle(), null, null, actor(u));
            notifier.publish(new ProjectEvent(p.getTenantId(), p.getId(), PmEvent.CLIENT_REQUEST, g.getClientName() + " sent " + what,
                    u.getFullName() + " sent " + what + " on " + p.getProjectNumber() + ": " + r.getTitle(), r.getId(),
                    p.getProjectManagerId() == null ? Set.of() : Set.of(p.getProjectManagerId()), Set.of(ProjectMemberRole.MANAGER), null));
        } else if (g.getInvitedBy() != null) {
            notifier.publish(new ProjectEvent(g.getTenantId(), null, PmEvent.CLIENT_REQUEST, g.getClientName() + " asked for a new project",
                    u.getFullName() + " asked for a new project: " + r.getTitle(), r.getId(), Set.of(g.getInvitedBy()), Set.of(), null));
        }
        return requestView(r);
    }

    // ── Files ─────────────────────────────────────────────────────────────────

    @Transactional
    public UploadView upload(UUID portalUserId, UUID projectId, MultipartFile file, String note) {
        ClientPortalAccess.Visible v = access.require(portalUserId, projectId);
        PortalUser u = access.user(portalUserId);
        if (file != null && file.getSize() > maxUploadBytes)
            throw new HandyFlowException("Files can be at most " + (maxUploadBytes / (1024 * 1024)) + " MB", HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        if (note != null && note.length() > 1000) throw new HandyFlowException("The note is too long (1 000 characters at most)", HttpStatus.BAD_REQUEST, "NOTE_TOO_LONG");
        byte[] bytes = documentService.screen(file);        // size, executable and malware checks, before anything is stored
        String name = DocumentRules.safeFileName(file.getOriginalFilename());
        Project p = v.project();
        UUID id = UUID.randomUUID();
        EvidenceResponse ev = evidence.attachRestricted(TenantId.of(p.getTenantId()), file, "CLIENT_UPLOAD", EVIDENCE_MODULE, EVIDENCE_ENTITY, id,
                null, u.getFullName() + " (client)");
        ProjectPortalUpload up = uploads.save(ProjectPortalUpload.received(id, p.getTenantId(), p.getId(), v.grant().getId(), ev.id(), name,
                bytes.length, DocumentRules.sha256(bytes), note, u.getFullName(), u.getEmail()));
        activity.record(p.getTenantId(), p.getId(), "CLIENT_UPLOAD", "Client sent a file: " + name, up.getNote(), null, actor(u));
        notifier.publish(new ProjectEvent(p.getTenantId(), p.getId(), PmEvent.CLIENT_UPLOAD, v.grant().getClientName() + " sent a file",
                u.getFullName() + " sent " + name + " on " + p.getProjectNumber() + ". Accept it into the register or reject it.", up.getId(),
                Set.of(), Set.of(ProjectMemberRole.MANAGER, ProjectMemberRole.DOCUMENT_CONTROLLER), null));
        return uploadView(up);
    }

    // ── Views ─────────────────────────────────────────────────────────────────

    /** Who did it, as the activity feed shows it (the column holds 255 characters). */
    private static String actor(PortalUser u) {
        String s = u.getFullName() + " (" + u.getEmail() + ") · client portal";
        return s.length() <= 255 ? s : s.substring(0, 254) + "…";
    }

    private MyProject summary(Project p) {
        List<BigDecimal> progress = projectService.getMilestonesForPortal(p).stream()
                .map(t -> t.getProgressPct() == null ? BigDecimal.ZERO : t.getProgressPct()).toList();
        BigDecimal pct = progress.isEmpty() ? BigDecimal.ZERO
                : progress.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(progress.size()), 2, RoundingMode.HALF_UP);
        int awaiting = (int) changeOrders(p).stream().filter(c -> "AWAITING".equals(clientState(c))).count();
        List<ProjectPortalRequest> rs = requests.findByProject(p.getTenantId(), p.getId());
        return new MyProject(p.getId(), p.getProjectNumber(), p.getName(), p.getClientName(), p.getStatus().name(), p.getHealth().name(),
                p.getStartDate(), p.getEndDate(), pct, fieldService.getSnagsForPortal(p, true).size(), awaiting,
                (int) rs.stream().filter(ProjectPortalRequest::isOpen).count(),
                (int) rs.stream().filter(r -> !r.isOpen()).count());
    }

    static String clientState(ChangeOrder c) {
        if (c.getClientApprovedAt() != null) return "APPROVED";
        if (c.hasOpenClientDispute()) return "DISPUTED";
        return "AWAITING";
    }

    static ClientChangeOrder clientView(ChangeOrder c) {
        return new ClientChangeOrder(c.getId(), c.getChangeNumber(), c.getTitle(), c.getDescription(), c.getReason(), c.getCostImpact(),
                c.getScheduleImpact(), c.getApprovedAt(), clientState(c), c.getClientApprovedAt(), c.getClientDisputedAt(),
                c.getClientDisputeReason(), c.getClientDisputeResolvedAt(), c.getClientDisputeResolution());
    }

    static RequestView requestView(ProjectPortalRequest r) {
        return new RequestView(r.getId(), r.getRequestNumber(), r.getKind(), r.getProjectId(), r.getTitle(), r.getDescription(), r.getNeededBy(),
                r.getStatus(), r.getResponse(), r.getRaisedByName(), r.getRaisedByEmail(), r.getDecidedByName(), r.getDecidedAt(),
                r.getLinkedType(), r.getLinkedId(), r.getCreatedAt());
    }

    static UploadView uploadView(ProjectPortalUpload u) {
        return new UploadView(u.getId(), u.getProjectId(), u.getFileName(), u.getSizeBytes(), u.getNote(), u.getStatus(), u.getReason(),
                u.getDocumentId(), u.getUploadedByName(), u.getUploadedByEmail(), u.getDecidedByName(), u.getDecidedAt(), u.getCreatedAt());
    }
}
