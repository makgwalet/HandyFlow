package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectRfi;
import za.co.handyflow.platform.projects.domain.repository.ProjectRfiRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.CreateRfiRequest;
import za.co.handyflow.platform.projects.dto.RespondRfiRequest;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RfiService {

    private final ProjectRfiRepository  rfiRepo;
    private final ProjectRepository     projectRepo;
    private final SequenceService       sequenceService;
    private final PmNotificationService notificationService;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectRfi> getRfis(UUID projectId) {
        UUID tenantId = tenantId();
        verifyProject(projectId, tenantId);
        return rfiRepo.findByProject(projectId, tenantId);
    }

    @Transactional(readOnly = true)
    public ProjectRfi getRfi(UUID rfiId) {
        return rfiRepo.findByIdAndTenantId(rfiId, tenantId())
                .orElseThrow(() -> new IllegalArgumentException("RFI not found: " + rfiId));
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Transactional
    public ProjectRfi createRfi(UUID projectId, CreateRfiRequest req) {
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
        return saved;
    }

    // ── Submit ─────────────────────────────────────────────────────────────────

    @Transactional
    public ProjectRfi submit(UUID rfiId) {
        UUID       tenantId = tenantId();
        ProjectRfi rfi      = getVerified(rfiId, tenantId);
        Project    project  = verifyProject(rfi.getProjectId(), tenantId);

        rfi.submit();
        ProjectRfi saved = rfiRepo.save(rfi);
        notificationService.notifyRfiSubmitted(tenantId, project.getName(), rfi.getRfiNumber(), rfi.getTitle());
        log.info("Submitted RFI={}", rfiId);
        return saved;
    }

    // ── Respond ────────────────────────────────────────────────────────────────

    @Transactional
    public ProjectRfi respond(UUID rfiId, RespondRfiRequest req) {
        UUID       tenantId = tenantId();
        ProjectRfi rfi      = getVerified(rfiId, tenantId);
        Project    project  = verifyProject(rfi.getProjectId(), tenantId);

        String userName = TenantContext.getCurrentUserName();
        UUID   userId   = TenantContext.getCurrentUserId();
        rfi.respond(userName, userId, req.response());

        ProjectRfi saved = rfiRepo.save(rfi);
        notificationService.notifyRfiResponded(tenantId, project.getName(), rfi.getRfiNumber(), rfi.getTitle(), userName);
        log.info("Responded to RFI={} by={}", rfiId, userName);
        return saved;
    }

    // ── Close ──────────────────────────────────────────────────────────────────

    @Transactional
    public ProjectRfi close(UUID rfiId) {
        UUID       tenantId = tenantId();
        ProjectRfi rfi      = getVerified(rfiId, tenantId);
        rfi.close();
        log.info("Closed RFI={}", rfiId);
        return rfiRepo.save(rfi);
    }

    // ── Cancel ─────────────────────────────────────────────────────────────────

    @Transactional
    public ProjectRfi cancel(UUID rfiId, String reason) {
        UUID       tenantId = tenantId();
        ProjectRfi rfi      = getVerified(rfiId, tenantId);
        rfi.cancel(reason);
        log.info("Cancelled RFI={} reason={}", rfiId, reason);
        return rfiRepo.save(rfi);
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
}
