package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.ProjectResource;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectResourceRepository;
import za.co.handyflow.platform.projects.dto.CreateResourceRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    @Transactional
    public ProjectResource assignResource(TenantId tenantId, UUID projectId, CreateResourceRequest req) {
        verifyProject(tenantId, projectId);

        // Conflict check for HUMAN resources
        if ("HUMAN".equals(req.resourceType()) && req.resourceId() != null
                && req.startDate() != null && req.endDate() != null) {
            List<ProjectResource> conflicts = resourceRepo.findByResourceAndDateRange(
                    tenantId.getValue(), req.resourceId(), req.startDate(), req.endDate());
            if (!conflicts.isEmpty()) {
                // Warn but don't block — let manager decide (business rule)
                // A future AI layer will suggest re-sequencing
            }
        }

        ProjectResource r = ProjectResource.create(
                tenantId.getValue(), projectId, req.taskId(),
                req.resourceType(), req.resourceId(), req.resourceName(),
                req.role(), req.allocationPct(),
                req.startDate(), req.endDate(),
                req.hourlyRate(), req.dailyRate(), req.plannedHours());
        return resourceRepo.save(r);
    }

    @Transactional
    public void removeResource(TenantId tenantId, UUID resourceId) {
        ProjectResource r = resourceRepo.findByTenantAndId(tenantId.getValue(), resourceId)
                .orElseThrow(() -> notFound("Resource assignment"));
        resourceRepo.delete(r);
    }

    /**
     * Detect conflicts for a resource over a date range.
     * Returns a list of overlapping assignments — used by the AI conflict resolver.
     */
    @Transactional(readOnly = true)
    public List<ProjectResource> detectConflicts(TenantId tenantId, UUID resourceId,
                                                 LocalDate from, LocalDate to) {
        return resourceRepo.findByResourceAndDateRange(tenantId.getValue(), resourceId, from, to);
    }

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
