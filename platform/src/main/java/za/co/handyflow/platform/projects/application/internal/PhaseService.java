package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.ProjectPhase;
import za.co.handyflow.platform.projects.domain.repository.ProjectPhaseRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.CreatePhaseRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhaseService {

    private final ProjectPhaseRepository phaseRepo;
    private final ProjectRepository      projectRepo;

    @Transactional(readOnly = true)
    public List<ProjectPhase> getPhases(TenantId tenantId, UUID projectId) {
        verifyProject(tenantId, projectId);
        return phaseRepo.findByProject(projectId);
    }

    @Transactional
    public ProjectPhase createPhase(TenantId tenantId, UUID projectId, CreatePhaseRequest req) {
        verifyProject(tenantId, projectId);
        int nextOrder = phaseRepo.findMaxSortOrder(projectId) + 1;
        ProjectPhase phase = ProjectPhase.create(
                tenantId.getValue(), projectId, req.name(), req.description(),
                req.sortOrder() > 0 ? req.sortOrder() : nextOrder,
                req.startDate(), req.endDate());
        return phaseRepo.save(phase);
    }

    @Transactional
    public ProjectPhase updatePhaseStatus(TenantId tenantId, UUID phaseId, String action) {
        ProjectPhase phase = phaseRepo.findByTenantAndId(tenantId.getValue(), phaseId)
                .orElseThrow(() -> notFound("Phase"));
        switch (action.toUpperCase()) {
            case "START"    -> phase.start();
            case "COMPLETE" -> phase.complete();
            case "SKIP"     -> phase.skip();
            default -> throw new HandyFlowException("Unknown action: " + action,
                    HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        return phaseRepo.save(phase);
    }

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
