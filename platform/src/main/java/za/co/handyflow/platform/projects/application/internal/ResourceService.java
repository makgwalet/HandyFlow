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


@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ProjectResourceRepository resourceRepo;
    private final ProjectRepository         projectRepo;
    private final ProjectReferenceValidator referenceValidator;

    @Transactional(readOnly = true)
    public List<ProjectResource> getResources(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return resourceRepo.findByProject(projectId);
    }

    @Transactional
    public AssignResourceResult assignResource(TenantId tenantId, UUID projectId,
                                               CreateResourceRequest req) {
        verifyProject(tenantId, projectId);
        referenceValidator.requireTaskInProject(tenantId, projectId, req.taskId());

        List<String> warnings = List.of();

        if ("HUMAN".equals(req.resourceType())
                && req.resourceId()  != null
                && req.startDate()   != null
                && req.endDate()     != null) {

            List<ProjectResource> conflicts = resourceRepo.findByResourceAndDateRange(
                    tenantId.getValue(), req.resourceId(),
                    req.startDate(), req.endDate());

            if (!conflicts.isEmpty()) {
                warnings = conflicts.stream()
                        .map(c -> String.format(
                                "Resource '%s' already has an overlapping assignment (%s → %s) at %d%% allocation.",
                                req.resourceName(),
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