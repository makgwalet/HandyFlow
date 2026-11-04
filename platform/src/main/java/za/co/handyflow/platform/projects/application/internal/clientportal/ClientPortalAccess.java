package za.co.handyflow.platform.projects.application.internal.clientportal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalGrant;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalGrantRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

import java.util.*;

/**
 * Which projects a portal login may see, worked out again on every request from its ACTIVE grants, so revoking access
 * or narrowing it takes effect at once. A portal token is not tied to a tenant: the tenant always comes from the grant.
 * Anything outside the caller's projects is the same 404, whether it exists or not.
 */
@Service
@RequiredArgsConstructor
public class ClientPortalAccess {

    private final PortalUserRepository portalUsers;
    private final ProjectPortalGrantRepository grants;
    private final ProjectRepository projects;

    /** A project the caller can see, and the grant that lets them. */
    public record Visible(ProjectPortalGrant grant, Project project) {}

    @Transactional(readOnly = true)
    public PortalUser user(UUID portalUserId) {
        PortalUser u = portalUsers.findById(portalUserId)
                .orElseThrow(() -> new HandyFlowException("Invalid portal session", HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_SESSION"));
        if (!u.isActive()) throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        return u;
    }

    @Transactional(readOnly = true)
    public List<ProjectPortalGrant> grants(UUID portalUserId) {
        user(portalUserId);
        List<ProjectPortalGrant> list = grants.findActiveForUser(portalUserId);
        if (list.isEmpty()) throw noAccess();
        return list;
    }

    @Transactional(readOnly = true)
    public List<Visible> visible(UUID portalUserId) {
        Map<UUID, Visible> out = new LinkedHashMap<>();
        for (ProjectPortalGrant g : grants(portalUserId))
            for (Project p : projectsOf(g)) out.putIfAbsent(p.getId(), new Visible(g, p));
        return new ArrayList<>(out.values());
    }

    @Transactional(readOnly = true)
    public Visible require(UUID portalUserId, UUID projectId) {
        return visible(portalUserId).stream().filter(v -> v.project().getId().equals(projectId)).findFirst()
                .orElseThrow(() -> new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public ProjectPortalGrant requireGrant(UUID portalUserId, UUID grantId) {
        List<ProjectPortalGrant> mine = grants(portalUserId);
        if (grantId == null) {
            if (mine.size() == 1) return mine.get(0);
            throw new HandyFlowException("Say which company this request is for", HttpStatus.BAD_REQUEST, "ACCOUNT_REQUIRED");
        }
        return mine.stream().filter(g -> g.getId().equals(grantId)).findFirst()
                .orElseThrow(() -> new HandyFlowException("Not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    /** Every project of the grant's client (by client id, and by client name), or only the chosen ones. Never cancelled projects. */
    @Transactional(readOnly = true)
    public List<Project> projectsOf(ProjectPortalGrant g) {
        if (!g.isAllProjects()) {
            return projects.findAllById(g.getProjectIds()).stream()
                    .filter(p -> p.getTenantId().equals(g.getTenantId()) && p.getStatus() != ProjectStatus.CANCELLED)
                    .sorted(Comparator.comparing(Project::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        return clientProjects(g.getTenantId(), g.getClientId(), g.getClientName());
    }

    /** All non-cancelled projects of one client in one tenant. */
    @Transactional(readOnly = true)
    public List<Project> clientProjects(UUID tenantId, UUID clientId, String clientName) {
        Map<UUID, Project> out = new LinkedHashMap<>();
        if (clientId != null) projects.findByClient(tenantId, clientId).forEach(p -> out.putIfAbsent(p.getId(), p));
        String name = ClientPortalTokens.normalise(clientName);
        if (name != null && !name.isEmpty()) projects.findByClientNameNormalised(tenantId, name).forEach(p -> out.putIfAbsent(p.getId(), p));
        return new ArrayList<>(out.values());
    }

    static HandyFlowException noAccess() {
        return new HandyFlowException("This login has no project access at the moment. Ask the company that invited you to send a new invite.",
                HttpStatus.FORBIDDEN, "NO_PROJECT_ACCESS");
    }
}
