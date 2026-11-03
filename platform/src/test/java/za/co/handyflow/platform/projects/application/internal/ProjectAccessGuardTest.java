package za.co.handyflow.platform.projects.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.projects.domain.enums.ProjectMemberRole;
import za.co.handyflow.platform.projects.domain.model.ProjectMember;
import za.co.handyflow.platform.projects.domain.repository.ProjectMemberRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardTest {

    @Mock ProjectMemberRepository memberRepo;

    UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), project = UUID.randomUUID();

    private void memberWithRole(ProjectMemberRole role) {
        when(memberRepo.findByProjectAndUser(tenant, project, user))
                .thenReturn(Optional.of(ProjectMember.create(tenant, project, user, "U", role, null)));
    }

    @Test
    void unrestrictedMode_givesEveryoneFullAccess_withoutTouchingTheDatabase() {
        ProjectAccessGuard g = new ProjectAccessGuard(memberRepo, false);
        assertEquals(ProjectAccess.FULL, g.accessFor(tenant, user, project, false));
        verifyNoInteractions(memberRepo);
    }

    @Test
    void restricted_nonMember_hasNoAccess() {
        ProjectAccessGuard g = new ProjectAccessGuard(memberRepo, true);
        when(memberRepo.findByProjectAndUser(tenant, project, user)).thenReturn(Optional.empty());
        assertEquals(ProjectAccess.NONE, g.accessFor(tenant, user, project, false));
    }

    @Test
    void restricted_admin_bypassesMembership() {
        ProjectAccessGuard g = new ProjectAccessGuard(memberRepo, true);
        assertEquals(ProjectAccess.FULL, g.accessFor(tenant, user, project, true));
        verifyNoInteractions(memberRepo);
    }

    @Test
    void viewer_canOnlyRead() {
        ProjectAccessGuard g = new ProjectAccessGuard(memberRepo, true);
        memberWithRole(ProjectMemberRole.VIEWER);
        ProjectAccess a = g.accessFor(tenant, user, project, false);
        assertTrue(a.view());
        assertFalse(a.write());
        assertFalse(a.cost());
        assertFalse(a.manageMembers());
    }

    @Test
    void member_canWrite_butNotSeeCost() {
        ProjectAccessGuard g = new ProjectAccessGuard(memberRepo, true);
        memberWithRole(ProjectMemberRole.MEMBER);
        ProjectAccess a = g.accessFor(tenant, user, project, false);
        assertTrue(a.view());
        assertTrue(a.write());
        assertFalse(a.cost());
    }

    @Test
    void costController_seesCost_butCannotManageMembers() {
        ProjectAccessGuard g = new ProjectAccessGuard(memberRepo, true);
        memberWithRole(ProjectMemberRole.COST_CONTROLLER);
        ProjectAccess a = g.accessFor(tenant, user, project, false);
        assertTrue(a.cost());
        assertFalse(a.manageMembers());
    }

    @Test
    void manager_hasEverything() {
        ProjectAccessGuard g = new ProjectAccessGuard(memberRepo, true);
        memberWithRole(ProjectMemberRole.MANAGER);
        assertEquals(ProjectAccess.FULL, g.accessFor(tenant, user, project, false));
    }

    @Test
    void roleCapabilityMatrix() {
        for (ProjectMemberRole r : ProjectMemberRole.values()) {
            assertEquals(r != ProjectMemberRole.VIEWER, r.canWrite(), r + " write");
            assertEquals(r == ProjectMemberRole.SPONSOR || r == ProjectMemberRole.MANAGER
                    || r == ProjectMemberRole.COST_CONTROLLER, r.canViewCost(), r + " cost");
            assertEquals(r == ProjectMemberRole.MANAGER, r.canManageMembers(), r + " manage");
        }
    }
}