package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.ProjectSummary;
import za.co.handyflow.platform.projects.ProjectsFacade;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ProjectsFacadeImpl implements ProjectsFacade {

    private final ProjectRepository projects;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectSummary> findProject(TenantId tenantId, UUID projectId) {
        if (tenantId == null || projectId == null) return Optional.empty();
        return projects.findByTenantAndId(tenantId.getValue(), projectId)
                .filter(p -> !"CANCELLED".equals(p.getStatus().name()))
                .map(ProjectsFacadeImpl::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSummary> findProjectsByExactName(TenantId tenantId, String name) {
        if (tenantId == null || name == null || name.isBlank()) return List.of();
        return projects.findActiveByExactName(tenantId.getValue(), name).stream().map(ProjectsFacadeImpl::toSummary).toList();
    }

    private static ProjectSummary toSummary(Project p) {
        return new ProjectSummary(p.getId(), p.getProjectNumber(), p.getName(), p.getClientId(), p.getClientName(),
                p.getSiteAddress(), p.getStatus().name());
    }
}
