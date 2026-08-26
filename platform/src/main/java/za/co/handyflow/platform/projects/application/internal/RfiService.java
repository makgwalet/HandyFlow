package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.projects.domain.model.ChangeOrder;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectRfi;
import za.co.handyflow.platform.projects.domain.repository.ChangeOrderRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRfiRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.CreateRfiRequest;
import za.co.handyflow.platform.projects.dto.RespondRfiRequest;
import za.co.handyflow.platform.projects.dto.RfiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * FIX: backlog 6.3 — RFI workflow refinement pass. Four changes:
 * (1) every method now returns RfiResponse instead of the raw ProjectRfi
 * entity — this was the only module in the codebase exposing a JPA
 * entity directly through the API; (2) isOverdue()/daysUntilDue() are
 * now surfaced, matching ApBill's own established pattern;
 * (3) evidence attachment support via EvidenceFacade, same proven
 * pattern as Payroll Bureau/Recruitment Agency; (4) an RFI can now be
 * linked to the Change Order its answer resulted in, validated against
 * the same project.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfiService {

    private final ProjectRfiRepository  rfiRepo;
    private final ProjectRepository     projectRepo;
    private final ChangeOrderRepository changeOrderRepo;
    private final SequenceService       sequenceService;
    private final PmNotificationService notificationService;
    private final EvidenceFacade        evidenceFacade;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RfiResponse> getRfis(UUID projectId) {
        UUID tenantId = tenantId();
        verifyProject(projectId, tenantId);
        return rfiRepo.findByProject(projectId, tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RfiResponse getRfi(UUID rfiId) {
        return toResponse(getVerified(rfiId, tenantId()));
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public RfiResponse createRfi(UUID projectId, CreateRfiRequest req) {
        UUID    tenantId  = tenantId();
        Project project   = verifyProject(projectId, tenantId);

        // nextRfiNumber uses SequenceService.next() — method is next(UUID, String)
        int    seq        = sequenceService.next(tenantId, "RFI:" + projectId);
        String rfiNumber  = "RFI-" + String.format("%03d", seq);

        ProjectRfi rfi = new ProjectRfi();
        rfi.setTenantId(tenantId);
        rfi.setProjectId(projectId);
        rfi.setRfiNumber(rfiNumber);
        rfi.setTitle(req.title());
        rfi.setDescription(req.description());
        rfi.setCategory(req.category());
        rfi.setRequestedBy(req.requestedBy() != null ? req.requestedBy() : TenantContext.getCurrentUserName());
        rfi.setRequestedById(req.requestedById());
        rfi.setRequestedDate(req.requestedDate() != null ? req.requestedDate() : LocalDate.now());
        rfi.setDueDate(req.dueDate());

        if (req.submitImmediately()) {
            rfi.submit();
            notificationService.notifyRfiSubmitted(tenantId, project.getName(), rfiNumber, rfi.getTitle());
        }

        ProjectRfi saved = rfiRepo.save(rfi);
        log.info("Created RFI={} number={} project={} status={}", saved.getId(), rfiNumber, projectId, saved.getStatus());
        return toResponse(saved);
    }

    // ── Submit ─────────────────────────────────────────────────────────────────

    @Transactional
    public RfiResponse submit(UUID rfiId) {
        UUID       tenantId = tenantId();
        ProjectRfi rfi      = getVerified(rfiId, tenantId);
        Project    project  = verifyProject(rfi.getProjectId(), tenantId);

        rfi.submit();
        ProjectRfi saved = rfiRepo.save(rfi);
        notificationService.notifyRfiSubmitted(tenantId, project.getName(), rfi.getRfiNumber(), rfi.getTitle());
        log.info("Submitted RFI={}", rfiId);
        return toResponse(saved);
    }

    // ── Respond ────────────────────────────────────────────────────────────────

    @Transactional
    public RfiResponse respond(UUID rfiId, RespondRfiRequest req) {
        UUID       tenantId = tenantId();
        ProjectRfi rfi      = getVerified(rfiId, tenantId);
        Project    project  = verifyProject(rfi.getProjectId(), tenantId);

        String userName = TenantContext.getCurrentUserName();
        UUID   userId   = TenantContext.getCurrentUserId();
        rfi.respond(userName, userId, req.response());

        ProjectRfi saved = rfiRepo.save(rfi);
        notificationService.notifyRfiResponded(tenantId, project.getName(), rfi.getRfiNumber(), rfi.getTitle(), userName);
        log.info("Responded to RFI={} by={}", rfiId, userName);
        return toResponse(saved);
    }

    // ── Close ──────────────────────────────────────────────────────────────────

    @Transactional
    public RfiResponse close(UUID rfiId) {
        UUID       tenantId = tenantId();
        ProjectRfi rfi      = getVerified(rfiId, tenantId);
        rfi.close();
        log.info("Closed RFI={}", rfiId);
        return toResponse(rfiRepo.save(rfi));
    }

    // ── Cancel ─────────────────────────────────────────────────────────────────

    @Transactional
    public RfiResponse cancel(UUID rfiId, String reason) {
        UUID       tenantId = tenantId();
        ProjectRfi rfi      = getVerified(rfiId, tenantId);
        rfi.cancel(reason);
        log.info("Cancelled RFI={} reason={}", rfiId, reason);
        return toResponse(rfiRepo.save(rfi));
    }

    // ── Change Order link ────────────────────────────────────────────────────

    /**
     * FIX: backlog 6.3. Validates the Change Order actually belongs to
     * the same project as the RFI — a real, if unlikely, mistake to
     * guard against (picking the right change order in the wrong
     * project's list), not a formality.
     */
    @Transactional
    public RfiResponse linkChangeOrder(UUID rfiId, UUID changeOrderId) {
        UUID       tenantId = tenantId();
        ProjectRfi rfi      = getVerified(rfiId, tenantId);

        ChangeOrder co = changeOrderRepo.findById(changeOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Change Order not found: " + changeOrderId));
        if (!rfi.getProjectId().equals(co.getProjectId())) {
            throw new IllegalArgumentException(
                    "Change Order " + changeOrderId + " belongs to a different project than this RFI");
        }

        rfi.linkChangeOrder(changeOrderId);
        log.info("Linked RFI={} to changeOrder={}", rfiId, changeOrderId);
        return toResponse(rfiRepo.save(rfi));
    }

    // ── Evidence attachments ─────────────────────────────────────────────────

    /**
     * FIX: backlog 6.3 — same EvidenceFacade pattern already proven for
     * Payroll Bureau's logo attachments and Recruitment Agency's CV
     * uploads. docType "RFI_ATTACHMENT" covers drawings, photos, and
     * spec references alike — this module has no need to distinguish
     * between them the way, say, Accountant's FICA documents do.
     */
    @Transactional
    public EvidenceResponse attachEvidence(UUID rfiId,
                                           org.springframework.web.multipart.MultipartFile file,
                                           UUID uploadedBy, String uploadedByName) {
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

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ProjectRfi getVerified(UUID rfiId, UUID tenantId) {
        return rfiRepo.findByIdAndTenantId(rfiId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("RFI not found: " + rfiId));
    }

    private Project verifyProject(UUID projectId, UUID tenantId) {
        // findById() is always on JpaRepository; tenant isolation enforced by filter
        return projectRepo.findById(projectId)
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    /** TenantContext stores tenant ID as String; parse to UUID for repo calls. */
    private static UUID tenantId() {
        return UUID.fromString(TenantContext.getTenantId());
    }

    private RfiResponse toResponse(ProjectRfi r) {
        return new RfiResponse(
                r.getId(), r.getProjectId(), r.getRfiNumber(),
                r.getTitle(), r.getDescription(), r.getCategory(),
                r.getRequestedBy(), r.getRequestedById(), r.getRequestedDate(), r.getDueDate(),
                r.getRespondedBy(), r.getRespondedById(), r.getRespondedDate(), r.getResponse(),
                r.getStatus(), r.isOverdue(), r.daysUntilDue(),
                r.getChangeOrderId(),
                r.getClosedAt(), r.getCancelledAt(), r.getCancellationReason(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}