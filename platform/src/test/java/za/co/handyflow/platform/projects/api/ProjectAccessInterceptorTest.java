package za.co.handyflow.platform.projects.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import za.co.handyflow.platform.projects.application.internal.ProjectAccess;
import za.co.handyflow.platform.projects.application.internal.ProjectAccessGuard;
import za.co.handyflow.platform.projects.application.internal.ProjectScopeResolver;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectAccessInterceptorTest {

    @Mock ProjectAccessGuard   guard;
    @Mock ProjectScopeResolver resolver;

    ProjectAccessInterceptor interceptor;
    UUID tenant = UUID.randomUUID(), user = UUID.randomUUID(), project = UUID.randomUUID();
    MockHttpServletResponse response = new MockHttpServletResponse();

    static final ProjectAccess READ_ONLY = new ProjectAccess(true, false, false, false);
    static final ProjectAccess MEMBER    = new ProjectAccess(true, true, false, false);
    static final ProjectAccess COST      = new ProjectAccess(true, true, true, false);

    @BeforeEach
    void setUp() {
        interceptor = new ProjectAccessInterceptor(guard, resolver);
        TenantContext.setTenantId(tenant.toString());
        TenantContext.setUserId(user.toString());
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private MockHttpServletRequest req(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private void restricted(ProjectAccess access) {
        when(guard.isRestricted()).thenReturn(true);
        when(guard.isAdmin()).thenReturn(false);
        when(guard.accessFor(tenant, user, project, false)).thenReturn(access);
    }

    private HandyFlowException denied(MockHttpServletRequest r) {
        return assertThrows(HandyFlowException.class, () -> interceptor.preHandle(r, response, null));
    }

    @Test
    void unrestricted_passesEverythingThrough() {
        when(guard.isRestricted()).thenReturn(false);
        assertTrue(interceptor.preHandle(req("DELETE", "/api/v1/projects/" + project + "/budget"), response, null));
        verifyNoInteractions(resolver);
    }

    @Test
    void nonMember_getsNotFound_notForbidden() {
        restricted(ProjectAccess.NONE);
        HandyFlowException e = denied(req("GET", "/api/v1/projects/" + project + "/tasks"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void viewer_canRead_butNotWrite() {
        restricted(READ_ONLY);
        assertTrue(interceptor.preHandle(req("GET", "/api/v1/projects/" + project + "/tasks"), response, null));
        assertEquals(HttpStatus.FORBIDDEN, denied(req("POST", "/api/v1/projects/" + project + "/tasks")).getStatus());
    }

    @Test
    void member_withoutCost_isBlockedFromBudgetAndChangeOrders_evenForReads() {
        restricted(MEMBER);
        assertEquals(HttpStatus.FORBIDDEN, denied(req("GET", "/api/v1/projects/" + project + "/budget")).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, denied(req("GET", "/api/v1/projects/" + project + "/change-orders")).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, denied(req("GET", "/api/v1/projects/" + project + "/budget/evm")).getStatus());
    }

    @Test
    void costRole_canAccessBudget() {
        restricted(COST);
        assertTrue(interceptor.preHandle(req("GET", "/api/v1/projects/" + project + "/budget"), response, null));
    }

    @Test
    void memberChanges_needManageMembers() {
        restricted(COST);   // can write, cannot manage members
        assertEquals(HttpStatus.FORBIDDEN,
                denied(req("POST", "/api/v1/projects/" + project + "/members")).getStatus());
        assertTrue(interceptor.preHandle(req("GET", "/api/v1/projects/" + project + "/members"), response, null));
    }

    @Test
    void entityRoute_resolvesProject_andEnforcesThatProject() {
        UUID taskId = UUID.randomUUID();
        when(resolver.projectIdFor("tasks", taskId, tenant)).thenReturn(Optional.of(project));
        restricted(ProjectAccess.NONE);
        assertEquals(HttpStatus.NOT_FOUND,
                denied(req("POST", "/api/v1/projects/tasks/" + taskId + "/status")).getStatus());
    }

    @Test
    void entityRoute_forMissingEntity_passesThroughToController404() {
        UUID taskId = UUID.randomUUID();
        when(guard.isRestricted()).thenReturn(true);
        when(resolver.projectIdFor("tasks", taskId, tenant)).thenReturn(Optional.empty());
        assertTrue(interceptor.preHandle(req("GET", "/api/v1/projects/tasks/" + taskId + "/status"), response, null));
    }

    @Test
    void unknownRoute_failsClosed() {
        when(guard.isRestricted()).thenReturn(true);
        assertEquals(HttpStatus.FORBIDDEN, denied(req("GET", "/api/v1/projects/brand-new-thing")).getStatus());
    }

    @Test
    void collectionAndSpecialRoutes_areLeftToTheServiceLayer() {
        when(guard.isRestricted()).thenReturn(true);
        assertTrue(interceptor.preHandle(req("GET", "/api/v1/projects"), response, null));
        assertTrue(interceptor.preHandle(req("GET", "/api/v1/projects/summary"), response, null));
        assertTrue(interceptor.preHandle(req("GET", "/api/v1/projects/time-approvals"), response, null));
    }

    @Test
    void unauthenticated_isLeftToSecurityLayer() {
        TenantContext.clear();
        when(guard.isRestricted()).thenReturn(true);
        assertTrue(interceptor.preHandle(req("GET", "/api/v1/projects/" + project + "/tasks"), response, null));
    }
}