package za.co.handyflow.platform.projects.application.internal.clientportal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalGrant;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalGrantRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.ClientPortalDtos.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Staff side of client logins: who can sign in to the Projects client portal, for which client, and which projects.
 * The invite link is emailed and never shown or stored in clear; sending it again replaces it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPortalAdminService {

    private final ProjectPortalGrantRepository grants;
    private final ProjectRepository projects;
    private final ClientPortalAccess access;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /** Every client named on a non-cancelled project, with its projects: one entry per client id, else per client name. */
    @Transactional(readOnly = true)
    public List<ClientOption> clients(TenantId tenant) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, List<ProjectOption>> byKey = new LinkedHashMap<>();
        for (Project p : projects.findWithClient(tenant.getValue())) {
            String key = p.getClientId() != null ? "id:" + p.getClientId() : "name:" + ClientPortalTokens.normalise(p.getClientName());
            ids.putIfAbsent(key, p.getClientId());
            if (p.getClientName() != null && !p.getClientName().isBlank()) names.putIfAbsent(key, p.getClientName().trim());
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(option(p));
        }
        List<ClientOption> out = new ArrayList<>();
        byKey.forEach((k, ps) -> out.add(new ClientOption(ids.get(k), names.getOrDefault(k, "Client"), ps)));
        out.sort(Comparator.comparing(c -> c.clientName().toLowerCase(Locale.ROOT)));
        return out;
    }

    @Transactional(readOnly = true)
    public List<GrantView> list(TenantId tenant) {
        return grants.findByTenant(tenant.getValue()).stream().map(this::view).toList();
    }

    @Transactional
    public GrantView invite(TenantId tenant, InviteRequest req, UUID by, String byName) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        List<Project> clientProjects = access.clientProjects(tenant.getValue(), req.clientId(), req.clientName());
        if (clientProjects.isEmpty())
            throw bad("No projects name this client. Check the client name on the project.", "CLIENT_HAS_NO_PROJECTS");
        Set<UUID> chosen = req.allProjects() ? Set.of() : checkChosen(clientProjects, req.projectIds());

        String clientKey = req.clientId() != null ? req.clientId().toString() : ClientPortalTokens.normalise(req.clientName());
        boolean live = grants.findByTenant(tenant.getValue()).stream()
                .filter(g -> !"REVOKED".equals(g.getStatus()) && g.getInviteEmail().equals(email))
                .anyMatch(g -> (g.getClientId() != null ? g.getClientId().toString() : ClientPortalTokens.normalise(g.getClientName())).equals(clientKey));
        if (live) throw new HandyFlowException(email + " already has access for this client. Change its projects or send the invite again.",
                HttpStatus.CONFLICT, "ALREADY_INVITED");

        String token = ClientPortalTokens.newToken();
        ProjectPortalGrant g = ProjectPortalGrant.invite(tenant.getValue(), req.clientId(), req.clientName(), req.allProjects(), chosen,
                email, blank(req.contactName()), ClientPortalTokens.hash(token), by, byName);
        try {
            g = grants.saveAndFlush(g);
        } catch (DataIntegrityViolationException e) {
            throw new HandyFlowException(email + " already has access for this client.", HttpStatus.CONFLICT, "ALREADY_INVITED");
        }
        send(g, token);
        log.info("Projects portal invite grant={} client='{}' tenant={}", g.getId(), g.getClientName(), tenant.getValue());
        return view(g);
    }

    @Transactional
    public GrantView resend(TenantId tenant, UUID id) {
        ProjectPortalGrant g = find(tenant, id);
        String token = ClientPortalTokens.newToken();
        g.reissue(ClientPortalTokens.hash(token));
        grants.save(g);
        send(g, token);
        return view(g);
    }

    @Transactional
    public GrantView changeScope(TenantId tenant, UUID id, ScopeRequest req) {
        ProjectPortalGrant g = find(tenant, id);
        if ("REVOKED".equals(g.getStatus())) throw new IllegalStateException("This access has been removed; invite them again instead");
        Set<UUID> chosen = req.allProjects() ? Set.of()
                : checkChosen(access.clientProjects(tenant.getValue(), g.getClientId(), g.getClientName()), req.projectIds());
        g.changeScope(req.allProjects(), chosen);
        return view(grants.save(g));
    }

    @Transactional
    public GrantView revoke(TenantId tenant, UUID id, UUID by) {
        ProjectPortalGrant g = find(tenant, id);
        g.revoke(by);
        log.info("Projects portal access revoked grant={} tenant={}", id, tenant.getValue());
        return view(grants.save(g));
    }

    /** The chosen projects must be this client's: access cannot be given to another client's project by mistake. */
    private static Set<UUID> checkChosen(List<Project> clientProjects, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) throw bad("Choose at least one project", "PROJECTS_REQUIRED");
        Set<UUID> allowed = new HashSet<>();
        clientProjects.forEach(p -> allowed.add(p.getId()));
        Set<UUID> chosen = new LinkedHashSet<>(ids);
        if (!allowed.containsAll(chosen)) throw bad("Only this client's own projects can be chosen", "PROJECT_NOT_CLIENTS");
        return chosen;
    }

    private void send(ProjectPortalGrant g, String token) {
        String url = frontendUrl + "/projects/client-portal/accept-invite?token=" + token;
        try {
            emailService.send(g.getInviteEmail(), "Follow your projects online: " + g.getClientName(),
                    EmailTemplates.portalInvite(g.getClientName(), "Projects", url));
        } catch (Exception e) {
            log.warn("Could not send the Projects portal invite grant={}: {}", g.getId(), e.getMessage());
        }
    }

    private ProjectPortalGrant find(TenantId tenant, UUID id) {
        return grants.findByTenantAndId(tenant.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Client access not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    GrantView view(ProjectPortalGrant g) {
        List<ProjectOption> ps = "REVOKED".equals(g.getStatus()) ? List.of() : access.projectsOf(g).stream().map(ClientPortalAdminService::option).toList();
        return new GrantView(g.getId(), g.getClientId(), g.getClientName(), g.isAllProjects(), ps, g.getInviteEmail(), g.getContactName(),
                g.getStatus(), g.getInvitedByName(), g.getInvitedAt(), g.getInviteExpiresAt(), g.getAcceptedAt(), g.getLastSeenAt(), g.getRevokedAt());
    }

    static ProjectOption option(Project p) {
        return new ProjectOption(p.getId(), p.getProjectNumber(), p.getName(), p.getStatus().name());
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
}
