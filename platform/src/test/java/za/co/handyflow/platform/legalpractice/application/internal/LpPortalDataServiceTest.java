package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.legalpractice.domain.model.LpPortalAccessGrant;
import za.co.handyflow.platform.legalpractice.domain.repository.LpPortalAccessGrantRepository;
import za.co.handyflow.platform.legalpractice.dto.LpClientResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The portal asks "which client records are mine?" before it can ask for matters or invoices. */
@ExtendWith(MockitoExtension.class)
class LpPortalDataServiceTest {

    @Mock LpPortalAccessGrantRepository grantRepo;
    @Mock LpClientService clientService;
    @Mock LpMatterService matterService;
    @Mock LpBillingService billingService;
    @InjectMocks LpPortalDataService service;

    @Test
    @DisplayName("listMyClients returns each client the portal user has an active grant for, read in the grant's own tenant")
    void listsClientsFromActiveGrants() {
        UUID portalUser = UUID.randomUUID(), clientId = UUID.randomUUID();
        TenantId tenant = TenantId.of(UUID.randomUUID());
        LpPortalAccessGrant grant = mock(LpPortalAccessGrant.class);
        when(grant.getTenantId()).thenReturn(tenant);
        when(grant.getClientId()).thenReturn(clientId);
        LpClientResponse client = new LpClientResponse(clientId, "Estate Late Mokoena", null, null, "INDIVIDUAL", null, BigDecimal.ZERO, "ACTIVE", null, null, null);
        when(grantRepo.findAllActiveForPortalUser(portalUser)).thenReturn(List.of(grant));
        when(clientService.getClient(tenant, clientId)).thenReturn(client);

        List<LpClientResponse> result = service.listMyClients(portalUser);

        assertEquals(List.of(client), result);
    }

    @Test
    @DisplayName("listMyClients is empty, and reads no client, when the portal user has no active grant")
    void emptyWithoutGrants() {
        UUID portalUser = UUID.randomUUID();
        when(grantRepo.findAllActiveForPortalUser(portalUser)).thenReturn(List.of());

        assertTrue(service.listMyClients(portalUser).isEmpty());
        verify(clientService, never()).getClient(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
