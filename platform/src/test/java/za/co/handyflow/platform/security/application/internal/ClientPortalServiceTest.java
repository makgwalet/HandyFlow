package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Not run in the authoring sandbox (no Maven jars). */
class ClientPortalServiceTest {

    private final SiteRepository sites = mock(SiteRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ClientPortalService service = new ClientPortalService(sites, jdbc, mock(EmailService.class));
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID siteId = UUID.randomUUID();

    @Test void expiredLinkLooksLikeAnUnknownLink() {
        Site s = mock(Site.class);
        when(s.getPortalExpiresAt()).thenReturn(Instant.now().minusSeconds(5));
        when(sites.findByPortalToken("tok")).thenReturn(Optional.of(s));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.getPortalData("tok"));
        assertEquals("PORTAL_NOT_FOUND", e.getErrorCode());
        verifyNoInteractions(jdbc); // nothing about the site was read
    }

    @Test void generateKeepsALiveLinkAndDoesNotRotate() {
        Site s = mock(Site.class);
        when(s.isPortalEnabled()).thenReturn(true);
        when(s.getPortalToken()).thenReturn("live");
        when(s.getPortalExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(sites.findActiveById(tenant, siteId)).thenReturn(Optional.of(s));
        assertEquals("live", service.generatePortalToken(siteId, "x", false, tenant));
        verify(s, never()).generatePortalToken(any());
    }

    @Test void regenerateRotates() {
        Site s = mock(Site.class);
        when(s.isPortalEnabled()).thenReturn(true);
        when(s.getPortalToken()).thenReturn("live");
        when(s.getPortalExpiresAt()).thenReturn(Instant.now().plusSeconds(3600));
        when(s.generatePortalToken(any())).thenReturn("fresh");
        when(sites.findActiveById(tenant, siteId)).thenReturn(Optional.of(s));
        assertEquals("fresh", service.generatePortalToken(siteId, "x", true, tenant));
    }
}
