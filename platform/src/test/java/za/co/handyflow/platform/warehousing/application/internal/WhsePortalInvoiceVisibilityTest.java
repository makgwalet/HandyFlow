package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhsePortalAccessGrant;
import za.co.handyflow.platform.warehousing.domain.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhsePortalInvoiceVisibilityTest {

    private WhseBillingInvoice invoice(UUID tenant, UUID client, String number) {
        return WhseBillingInvoice.create(tenant, client, number, LocalDate.now().minusDays(30), LocalDate.now(), LocalDate.now(),
                LocalDate.now().plusDays(30), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a client never sees an invoice that is awaiting approval or was rejected")
    void clientSeesIssuedInvoicesOnly() {
        UUID tenant = UUID.randomUUID(), clientId = UUID.randomUUID(), portalUser = UUID.randomUUID();
        WhseBillingInvoice waiting = invoice(tenant, clientId, "WHI-DRAFT");
        WhseBillingInvoice rejected = invoice(tenant, clientId, "WHI-REJ");
        rejected.reject(UUID.randomUUID(), "wrong period");
        WhseBillingInvoice issued = invoice(tenant, clientId, "WHI-SENT");
        issued.markSent();

        WhsePortalAccessGrantRepository grants = mock(WhsePortalAccessGrantRepository.class);
        WhseClientRepository clients = mock(WhseClientRepository.class);
        WhseBillingInvoiceRepository invoices = mock(WhseBillingInvoiceRepository.class);
        when(grants.findActiveGrant(portalUser, clientId)).thenReturn(Optional.of(mock(WhsePortalAccessGrant.class)));
        WhseClient client = mock(WhseClient.class);
        when(client.getTenantId()).thenReturn(tenant);
        when(clients.findById(clientId)).thenReturn(Optional.of(client));
        when(invoices.findByClient(eq(tenant), eq(clientId), any())).thenReturn(new PageImpl<>(List.of(waiting, rejected, issued)));

        WhsePortalDataService service = new WhsePortalDataService(grants, clients, mock(WhseInventoryRepository.class),
                mock(WhseInboundShipmentRepository.class), mock(WhseOutboundOrderRepository.class), invoices);

        List<WhseBillingInvoice> seen = service.getMyBillingInvoices(portalUser, clientId);

        assertEquals(1, seen.size());
        assertEquals("WHI-SENT", seen.get(0).getInvoiceNumber());
    }
}
