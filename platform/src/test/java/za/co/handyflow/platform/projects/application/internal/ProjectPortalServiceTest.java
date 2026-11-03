package za.co.handyflow.platform.projects.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.PortalStatusResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RateLimiter;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectPortalServiceTest {

    @Mock ProjectRepository projectRepo;
    @Mock RateLimiter       rateLimiter;

    ProjectPortalService service;
    UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProjectPortalService(projectRepo, rateLimiter, 365, 60, 60_000);
        lenient().when(rateLimiter.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(true);
    }

    private Project project() {
        return Project.create(tenant, "PRJ0001", "Test", "GENERAL", null, null,
                LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("1000"), UUID.randomUUID());
    }

    private static void assertNotFound(HandyFlowException e) {
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void validToken_returnsProject() {
        Project p = project();
        when(projectRepo.findByPortalToken(p.getClientPortalToken())).thenReturn(Optional.of(p));
        assertSame(p, service.resolve(p.getClientPortalToken(), "1.2.3.4"));
    }

    @Test
    void malformedToken_is404_andNeverHitsDatabase() {
        assertNotFound(assertThrows(HandyFlowException.class, () -> service.resolve("not-a-token", "1.2.3.4")));
        assertNotFound(assertThrows(HandyFlowException.class, () -> service.resolve(null, "1.2.3.4")));
        verifyNoInteractions(projectRepo);
    }

    @Test
    void unknownToken_is404() {
        String token = "a".repeat(32);
        when(projectRepo.findByPortalToken(token)).thenReturn(Optional.empty());
        assertNotFound(assertThrows(HandyFlowException.class, () -> service.resolve(token, "1.2.3.4")));
    }

    @Test
    void disabledPortal_is404_sameAsUnknown() {
        Project p = project();
        p.disablePortal();
        when(projectRepo.findByPortalToken(p.getClientPortalToken())).thenReturn(Optional.of(p));
        assertNotFound(assertThrows(HandyFlowException.class,
                () -> service.resolve(p.getClientPortalToken(), "1.2.3.4")));
    }

    @Test
    void expiredToken_is404() {
        Project p = project();
        p.setPortalTokenExpiresAt(Instant.now().minusSeconds(60));
        when(projectRepo.findByPortalToken(p.getClientPortalToken())).thenReturn(Optional.of(p));
        assertNotFound(assertThrows(HandyFlowException.class,
                () -> service.resolve(p.getClientPortalToken(), "1.2.3.4")));
    }

    @Test
    void rateLimited_is429_andNeverHitsDatabase() {
        when(rateLimiter.tryConsume(anyString(), anyInt(), anyLong())).thenReturn(false);
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> service.resolve("a".repeat(32), "1.2.3.4"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        verifyNoInteractions(projectRepo);
    }

    @Test
    void rotate_issuesNewToken_reenables_andOldTokenNoLongerResolves() {
        Project p = project();
        p.disablePortal();
        String oldToken = p.getClientPortalToken();
        when(projectRepo.findByTenantAndId(tenant, p.getId())).thenReturn(Optional.of(p));
        when(projectRepo.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        PortalStatusResponse r = service.rotateToken(TenantId.of(tenant), p.getId(), 30);

        assertNotNull(r.token());
        assertNotEquals(oldToken, r.token());
        assertTrue(r.enabled());
        assertTrue(r.accessible());
        assertNotNull(r.expiresAt());
        // Old token is gone from the project, so a repo lookup for it would now find nothing.
        assertEquals(r.token(), p.getClientPortalToken());
    }

    @Test
    void rotate_withZeroTtl_neverExpires() {
        Project p = project();
        when(projectRepo.findByTenantAndId(tenant, p.getId())).thenReturn(Optional.of(p));
        when(projectRepo.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        PortalStatusResponse r = service.rotateToken(TenantId.of(tenant), p.getId(), 0);
        assertNull(r.expiresAt());
    }

    @Test
    void adminOperations_areTenantScoped() {
        UUID projectId = UUID.randomUUID();
        when(projectRepo.findByTenantAndId(tenant, projectId)).thenReturn(Optional.empty());
        assertNotFound(assertThrows(HandyFlowException.class,
                () -> service.revoke(TenantId.of(tenant), projectId)));
    }
}