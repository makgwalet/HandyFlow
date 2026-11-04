package za.co.handyflow.platform.projects.application.internal.clientportal;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalGrant;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalRequest;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalUpload;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalGrantRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Client portal accounts (patch 0573): invite lifecycle, what a login can see, and the auth rules. */
class ClientPortalRulesTest {

    UUID tenant = UUID.randomUUID(), otherTenant = UUID.randomUUID(), staff = UUID.randomUUID();

    ProjectPortalGrantRepository grantRepo = mock(ProjectPortalGrantRepository.class);
    ProjectRepository projectRepo = mock(ProjectRepository.class);
    PortalUserRepository userRepo = mock(PortalUserRepository.class);
    ClientPortalAccess access = new ClientPortalAccess(userRepo, grantRepo, projectRepo);

    private Project project(UUID t, String client) {
        return Project.create(t, "PRJ0001", "Warehouse", "CONSTRUCTION", null, client, null, null, BigDecimal.TEN, staff);
    }

    private ProjectPortalGrant grant(boolean all, Set<UUID> ids, String token) {
        return ProjectPortalGrant.invite(tenant, null, "  Acme Holdings ", all, ids, " Jane@Acme.co.za ", "Jane", ClientPortalTokens.hash(token), staff, "Sam");
    }

    @Test
    void tokensAreLongRandomAndStoredOnlyHashed() {
        String t = ClientPortalTokens.newToken();
        assertTrue(ClientPortalTokens.wellFormed(t));
        assertEquals(64, ClientPortalTokens.hash(t).length());
        assertNotEquals(t, ClientPortalTokens.hash(t));
        assertNotEquals(t, ClientPortalTokens.newToken());
        assertFalse(ClientPortalTokens.wellFormed("ABC"));
        assertFalse(ClientPortalTokens.wellFormed(null));
    }

    @Test
    void inviteLifecycle() {
        ProjectPortalGrant g = grant(true, null, ClientPortalTokens.newToken());
        assertEquals("jane@acme.co.za", g.getInviteEmail());
        assertEquals("Acme Holdings", g.getClientName());
        assertTrue(g.inviteUsable(Instant.now()));
        assertFalse(g.inviteUsable(Instant.now().plusSeconds(15 * 24 * 3600L)), "invites last 14 days");
        UUID user = UUID.randomUUID();
        g.accept(user);
        assertTrue(g.isActive());
        assertNull(g.getInviteTokenHash(), "the link cannot be used twice");
        assertThrows(IllegalStateException.class, () -> g.accept(user));
        assertThrows(IllegalStateException.class, () -> g.reissue("x"), "only a pending invite is sent again");
        g.revoke(staff);
        assertEquals("REVOKED", g.getStatus());
        assertThrows(IllegalArgumentException.class, () -> grant(false, Set.of(), "t"), "chosen projects need at least one");
    }

    @Test
    void allProjectsMatchesTheClientByNameAndChosenProjectsStayInTheirTenant() {
        Project mine = project(tenant, "ACME HOLDINGS"), elsewhere = project(otherTenant, "Acme Holdings");
        when(projectRepo.findByClientNameNormalised(tenant, "acme holdings")).thenReturn(List.of(mine));
        assertEquals(List.of(mine), access.projectsOf(grant(true, null, "t1")));

        when(projectRepo.findAllById(any())).thenReturn(List.of(mine, elsewhere));
        assertEquals(List.of(mine), access.projectsOf(grant(false, Set.of(mine.getId(), elsewhere.getId()), "t2")),
                "a project in another tenant never shows, even if its id is listed");
    }

    @Test
    void aLoginSeesOnlyItsProjectsAndNothingOnceAccessIsRemoved() {
        PortalUser u = PortalUser.create("jane@acme.co.za", "x", "Jane");
        when(userRepo.findById(u.getId())).thenReturn(Optional.of(u));
        Project mine = project(tenant, "Acme Holdings"), notMine = project(tenant, "Beta");
        ProjectPortalGrant g = grant(true, null, "t3");
        g.accept(u.getId());
        when(grantRepo.findActiveForUser(u.getId())).thenReturn(List.of(g));
        when(projectRepo.findByClientNameNormalised(tenant, "acme holdings")).thenReturn(List.of(mine));

        assertEquals(mine, access.require(u.getId(), mine.getId()).project());
        HandyFlowException hidden = assertThrows(HandyFlowException.class, () -> access.require(u.getId(), notMine.getId()));
        assertEquals("NOT_FOUND", hidden.getErrorCode());

        when(grantRepo.findActiveForUser(u.getId())).thenReturn(List.of());
        assertEquals("NO_PROJECT_ACCESS", assertThrows(HandyFlowException.class, () -> access.visible(u.getId())).getErrorCode());
    }

    @Test
    void acceptingWithAnExistingLoginNeedsItsPasswordAndLoginNeedsProjectAccess() {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder(4);
        PortalJwtService jwt = mock(PortalJwtService.class);
        when(jwt.generateToken(any(), any())).thenReturn("jwt");
        ClientPortalAuthService auth = new ClientPortalAuthService(userRepo, grantRepo, enc, jwt);

        String token = ClientPortalTokens.newToken();
        ProjectPortalGrant g = grant(true, null, token);
        when(grantRepo.findByInviteTokenHash(ClientPortalTokens.hash(token))).thenReturn(Optional.of(g));
        PortalUser existing = PortalUser.create("jane@acme.co.za", enc.encode("right-password"), "Jane");
        when(userRepo.findByEmail("jane@acme.co.za")).thenReturn(Optional.of(existing));

        assertTrue(auth.inviteInfo(token).existingAccount());
        assertEquals("INVALID_CREDENTIALS", assertThrows(HandyFlowException.class, () -> auth.register(token, "wrong-password", null)).getErrorCode());
        assertEquals("PENDING", g.getStatus(), "a failed attempt leaves the invite usable");
        assertEquals("jwt", auth.register(token, "right-password", null).token());
        assertEquals(existing.getId(), g.getPortalUserId());
        assertEquals("INVITE_INVALID", assertThrows(HandyFlowException.class, () -> auth.inviteInfo("not-a-token")).getErrorCode());

        when(grantRepo.findActiveForUser(existing.getId())).thenReturn(List.of());
        assertEquals("NO_PROJECT_ACCESS", assertThrows(HandyFlowException.class, () -> auth.login("JANE@acme.co.za ", "right-password")).getErrorCode());
        assertEquals("INVALID_CREDENTIALS", assertThrows(HandyFlowException.class, () -> auth.login("jane@acme.co.za", "nope")).getErrorCode());
    }

    @Test
    void requestsAndFilesAreDecidedOnceWithAReplyOrReason() {
        UUID grantId = UUID.randomUUID(), project = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> ProjectPortalRequest.raise(tenant, project, grantId, "CR-0001", "NEW_PROJECT", "t", null, null, "J", "j@x"));
        assertThrows(IllegalArgumentException.class, () -> ProjectPortalRequest.raise(tenant, null, grantId, "CR-0001", "QUESTION", "t", null, null, "J", "j@x"));
        ProjectPortalRequest r = ProjectPortalRequest.raise(tenant, project, grantId, "CR-0001", "QUESTION", " When is handover? ", " ", null, "J", "j@x");
        assertNull(r.getDescription());
        assertThrows(IllegalArgumentException.class, () -> r.decide("ANSWERED", " ", staff, "Sam", null, null), "the client must get a reply");
        r.decide("ANSWERED", "On 12 December", staff, "Sam", null, null);
        assertThrows(IllegalStateException.class, () -> r.decide("DECLINED", "no", staff, "Sam", null, null));

        ProjectPortalUpload u = ProjectPortalUpload.received(UUID.randomUUID(), tenant, project, grantId, UUID.randomUUID(), "plan.pdf", 10, "a".repeat(64), null, "J", "j@x");
        assertThrows(IllegalArgumentException.class, () -> u.reject(" ", staff, "Sam"));
        u.reject("Wrong project", staff, "Sam");
        assertThrows(IllegalStateException.class, () -> u.accept(UUID.randomUUID(), staff, "Sam"));
    }
}
