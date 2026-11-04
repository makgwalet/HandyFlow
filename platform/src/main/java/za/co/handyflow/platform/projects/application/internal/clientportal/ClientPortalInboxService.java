package za.co.handyflow.platform.projects.application.internal.clientportal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.projects.application.internal.*;
import za.co.handyflow.platform.projects.domain.model.*;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalGrantRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalRequestRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalUploadRepository;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.projects.dto.ClientPortalDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

/**
 * The project team's inbox of what clients sent through the portal. Accepting a request turns it into ordinary work
 * (a draft RFI, a draft change order, or a project in planning) through the normal services, so every rule those apply
 * still applies. Accepting a file puts it in the document register through the normal upload path, scanned again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPortalInboxService {

    private static final Set<String> PROJECT_TYPES = Set.of("CONSTRUCTION", "EARTHMOVING", "SECURITY", "EVENT", "IT", "GENERAL");

    private final ProjectPortalRequestRepository requests;
    private final ProjectPortalUploadRepository uploads;
    private final ProjectPortalGrantRepository grants;
    private final RfiService rfiService;
    private final ChangeOrderService changeOrderService;
    private final ProjectService projectService;
    private final DocumentService documentService;
    private final ProjectAccessGuard guard;
    private final ProjectActivityService activity;
    private final EvidenceFacade evidence;

    @Transactional(readOnly = true)
    public List<RequestView> requests(TenantId t, UUID projectId) {
        return requests.findByProject(t.getValue(), projectId).stream().map(ClientPortalService::requestView).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestView> newProjectRequests(TenantId t) {
        return requests.findNewProjectRequests(t.getValue()).stream().map(ClientPortalService::requestView).toList();
    }

    @Transactional(readOnly = true)
    public List<UploadView> uploads(TenantId t, UUID projectId) {
        return uploads.findByProject(t.getValue(), projectId).stream().map(ClientPortalService::uploadView).toList();
    }

    /** A request on a project. projectId must match: the route is checked against the project, so the request must be on it. */
    @Transactional
    public RequestView decide(TenantId t, UUID projectId, UUID requestId, DecideRequest body, UUID by, String byName) {
        ProjectPortalRequest r = requests.findByTenantAndId(t.getValue(), requestId)
                .filter(x -> Objects.equals(x.getProjectId(), projectId))
                .orElseThrow(() -> notFound("Request"));
        return decide(t, r, body, by, byName);
    }

    @Transactional
    public RequestView decideNewProject(TenantId t, UUID requestId, DecideRequest body, UUID by, String byName) {
        ProjectPortalRequest r = requests.findByTenantAndId(t.getValue(), requestId)
                .filter(x -> x.getProjectId() == null)
                .orElseThrow(() -> notFound("Request"));
        return decide(t, r, body, by, byName);
    }

    private RequestView decide(TenantId t, ProjectPortalRequest r, DecideRequest body, UUID by, String byName) {
        if (!r.isOpen()) throw new IllegalStateException("This request has already been " + r.getStatus().toLowerCase(Locale.ROOT));
        String origin = "From " + r.getRaisedByName() + " (" + r.getRaisedByEmail() + ") through the client portal, " + r.getRequestNumber() + ".";
        String linkedType = null; UUID linkedId = null;
        String outcome = switch (body.outcome()) { case "ACCEPT" -> "ACCEPTED"; case "ANSWER" -> "ANSWERED"; default -> "DECLINED"; };
        if ("ACCEPTED".equals(outcome)) {
            switch (r.getKind()) {
                case "QUESTION" -> {
                    LocalDate due = r.getNeededBy() != null && !r.getNeededBy().isBefore(LocalDate.now()) ? r.getNeededBy() : null;
                    RfiResponse rfi = rfiService.createRfi(r.getProjectId(), new CreateRfiRequest(cut(r.getTitle(), 500),
                            cut(join(r.getDescription(), origin), 5000), "OTHER", cut(r.getRaisedByName(), 255), null, null, due, false));
                    linkedType = "RFI"; linkedId = rfi.id();
                }
                case "CHANGE" -> {
                    if (!guard.accessFor(t.getValue(), r.getProjectId()).cost())
                        throw new HandyFlowException("Turning a request into a change order needs cost access on this project",
                                HttpStatus.FORBIDDEN, "COST_ACCESS_REQUIRED");
                    ChangeOrder co = changeOrderService.createChangeOrder(t, r.getProjectId(), new CreateChangeOrderRequest(cut(r.getTitle(), 200),
                            cut(r.getDescription(), 2000), cut(origin, 1000), null, 0, null, null, null, null, null), by);
                    linkedType = "CHANGE_ORDER"; linkedId = co.getId();
                }
                default -> {
                    if (body.projectType() == null || !PROJECT_TYPES.contains(body.projectType()))
                        throw new HandyFlowException("Choose the project type", HttpStatus.BAD_REQUEST, "PROJECT_TYPE_REQUIRED");
                    if (body.budgetTotal() == null || body.budgetTotal().signum() <= 0)
                        throw new HandyFlowException("Enter the budget", HttpStatus.BAD_REQUEST, "BUDGET_REQUIRED");
                    ProjectPortalGrant g = grants.findById(r.getGrantId()).orElseThrow(() -> notFound("Client access"));
                    Project p = projectService.createProject(t, new CreateProjectRequest(cut(r.getTitle(), 255), body.projectType(),
                            cut(r.getDescription(), 2000), g.getClientId(), g.getClientName(), body.startDate(), body.endDate(), body.budgetTotal(),
                            null, null, null, null, null, null, null, null, null, null, null, null, null, null, cut(origin, 2000)), by);
                    if (!g.isAllProjects() && g.isActive()) {        // a chosen-projects login gets the project it asked for
                        Set<UUID> ids = new LinkedHashSet<>(g.getProjectIds());
                        ids.add(p.getId());
                        g.changeScope(false, ids);
                        grants.save(g);
                    }
                    linkedType = "PROJECT"; linkedId = p.getId();
                }
            }
        }
        r.decide(outcome, body.response(), by, byName, linkedType, linkedId);
        requests.save(r);
        if (r.getProjectId() != null)
            activity.record(t.getValue(), r.getProjectId(), "CLIENT_REQUEST_DECIDED",
                    "Client request " + r.getRequestNumber() + " " + outcome.toLowerCase(Locale.ROOT), r.getResponse(), by, byName);
        log.info("Client request {} {} linked={} {}", r.getId(), outcome, linkedType, linkedId);
        return ClientPortalService.requestView(r);
    }

    @Transactional(readOnly = true)
    public DocumentService.FileDownload downloadUpload(TenantId t, UUID projectId, UUID uploadId) {
        ProjectPortalUpload u = upload(t, projectId, uploadId);
        EvidenceFacade.DownloadedEvidence f = evidence.downloadRestricted(t, ClientPortalService.EVIDENCE_MODULE,
                ClientPortalService.EVIDENCE_ENTITY, u.getId(), u.getEvidenceId());
        return new DocumentService.FileDownload(f.content(), u.getFileName(), f.contentType());
    }

    @Transactional
    public UploadView acceptUpload(TenantId t, UUID projectId, UUID uploadId, AcceptUpload body, UUID by, String byName) {
        ProjectPortalUpload u = upload(t, projectId, uploadId);
        if (!u.isPending()) throw new IllegalStateException("This file has already been " + u.getStatus().toLowerCase(Locale.ROOT));
        EvidenceFacade.DownloadedEvidence f = evidence.downloadRestricted(t, ClientPortalService.EVIDENCE_MODULE,
                ClientPortalService.EVIDENCE_ENTITY, u.getId(), u.getEvidenceId());
        String description = cut(join(u.getNote(), "Sent by " + u.getUploadedByName() + " (" + u.getUploadedByEmail() + ") through the client portal."), 2000);
        ProjectDocument doc = documentService.registerFile(t, projectId,
                new RegisterFileRequest(body.documentType() == null ? "GENERAL" : body.documentType(), body.documentNumber(), body.title(),
                        body.revision(), description, body.visibility() == null ? "INTERNAL" : body.visibility()),
                new ByteArrayFile(u.getFileName(), f.contentType(), f.content()), by, byName);
        u.accept(doc.getId(), by, byName);
        uploads.save(u);
        activity.record(t.getValue(), projectId, "CLIENT_UPLOAD_ACCEPTED", "Client file " + u.getFileName() + " added to the register", null, by, byName);
        return ClientPortalService.uploadView(u);
    }

    @Transactional
    public UploadView rejectUpload(TenantId t, UUID projectId, UUID uploadId, String reason, UUID by, String byName) {
        ProjectPortalUpload u = upload(t, projectId, uploadId);
        u.reject(reason, by, byName);
        uploads.save(u);
        activity.record(t.getValue(), projectId, "CLIENT_UPLOAD_REJECTED", "Client file " + u.getFileName() + " not accepted", u.getReason(), by, byName);
        return ClientPortalService.uploadView(u);
    }

    private ProjectPortalUpload upload(TenantId t, UUID projectId, UUID id) {
        return uploads.findByTenantAndId(t.getValue(), id).filter(u -> u.getProjectId().equals(projectId)).orElseThrow(() -> notFound("File"));
    }

    private static String join(String a, String b) { return a == null || a.isBlank() ? b : a.trim() + "\n\n" + b; }
    private static String cut(String s, int max) { return s == null ? null : s.length() <= max ? s : s.substring(0, max - 1) + "…"; }
    private static HandyFlowException notFound(String what) { return new HandyFlowException(what + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }
}
