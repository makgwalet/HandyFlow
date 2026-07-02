package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.ProjectResource;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectResourceRepository;
import za.co.handyflow.platform.projects.dto.AssignResourceResult;
import za.co.handyflow.platform.projects.dto.CreateResourceRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Resource assignment and conflict detection.
 *
 * CHANGE FROM ORIGINAL — conflict detection now surfaces warnings
 * ───────────────────────────────────────────────────────────────
 * The original code detected HUMAN resource conflicts and then did nothing:
 *
 *   if (!conflicts.isEmpty()) {
 *       // Warn but don't block — let manager decide (business rule)
 *       // A future AI layer will suggest re-sequencing
 *   }
 *
 * The conflict check runs a real DB query, finds real overlapping assignments,
 * and silently discards them.  The API response gave no indication a conflict
 * existed.  Managers were never informed of double-bookings.
 *
 * FIX: assignResource() now returns AssignResourceResult — a record that contains
 * both the saved resource assignment AND a list of conflict descriptions.
 *
 * This keeps the "warn but don't block" business rule intact (managers can still
 * override) while making conflicts visible in the API response.  The controller
 * can surface these as a "warnings" field in the response body and the frontend
 * can show a toast notification.
 *
 * IMPORTANT: the return type of assignResource() changed from ProjectResource
 * to AssignResourceResult.  ResourceController must be updated accordingly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ProjectResourceRepository resourceRepo;
    private final ProjectRepository         projectRepo;

    @Transactional(readOnly = true)
    public List<ProjectResource> getResources(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return resourceRepo.findByProject(projectId);
    }

    /**
     * Assigns a resource to a project and returns both the assignment and any
     * double-booking warnings.
     *
     * Conflicts are WARNINGS, not errors — the assignment is saved regardless.
     * The caller (controller) decides how to surface the warnings to the user.
     */
    @Transactional
    public AssignResourceResult assignResource(TenantId tenantId, UUID projectId,
                                               CreateResourceRequest req) {
        verifyProject(tenantId, projectId);

        List<String> warnings = List.of();

        // Detect HUMAN resource double-bookings
        if ("HUMAN".equals(req.resourceType())
                && req.resourceId()  != null
                && req.startDate()   != null
                && req.endDate()     != null) {

            List<ProjectResource> conflicts = resourceRepo.findByResourceAndDateRange(
                    tenantId.getValue(), req.resourceId(),
                    req.startDate(), req.endDate());

            if (!conflicts.isEmpty()) {
                // Build human-readable warning messages for each conflicting assignment
                warnings = conflicts.stream()
                        .map(c -> String.format(
                                "Resource '%s' is already assigned to project %s (%s → %s) at %d%% allocation.",
                                req.resourceName(),
                                c.getProjectId(),
                                c.getStartDate() != null ? c.getStartDate() : "open",
                                c.getEndDate()   != null ? c.getEndDate()   : "open",
                                c.getAllocationPct().intValue()))
                        .collect(Collectors.toList());

                log.warn("Double-booking detected for resource={} tenant={}: {} conflicts",
                        req.resourceId(), tenantId.getValue(), conflicts.size());
            }
        }

        ProjectResource r = ProjectResource.create(
                tenantId.getValue(), projectId, req.taskId(),
                req.resourceType(), req.resourceId(), req.resourceName(),
                req.role(), req.allocationPct(),
                req.startDate(), req.endDate(),
                req.hourlyRate(), req.dailyRate(), req.plannedHours());
        resourceRepo.save(r);

        return new AssignResourceResult(r, warnings);
    }

    @Transactional
    public void removeResource(TenantId tenantId, UUID resourceId) {
        ProjectResource r = resourceRepo.findByTenantAndId(tenantId.getValue(), resourceId)
                .orElseThrow(() -> notFound("Resource assignment"));
        resourceRepo.delete(r);
    }

    /**
     * Standalone conflict detection — used by the AI conflict resolver and
     * by scheduling suggestions.
     */
    @Transactional(readOnly = true)
    public List<ProjectResource> detectConflicts(TenantId tenantId, UUID resourceId,
                                                 LocalDate from, LocalDate to) {
        return resourceRepo.findByResourceAndDateRange(tenantId.getValue(), resourceId, from, to);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
