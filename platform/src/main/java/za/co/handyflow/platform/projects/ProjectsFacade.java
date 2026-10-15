package za.co.handyflow.platform.projects;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the Projects module for other modules. Returns plain summaries, never Projects entities.
 * Every method is tenant-scoped: a project from another company is never visible.
 */
public interface ProjectsFacade {

    /** The project if it exists in this company and is not cancelled. */
    Optional<ProjectSummary> findProject(TenantId tenantId, UUID projectId);

    /** Projects (not cancelled) whose name equals the given name, ignoring case and surrounding spaces. More than one means ambiguous. */
    List<ProjectSummary> findProjectsByExactName(TenantId tenantId, String name);
}
