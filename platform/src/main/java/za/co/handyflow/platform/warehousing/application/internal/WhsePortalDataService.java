package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipment;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrder;
import za.co.handyflow.platform.warehousing.domain.model.WhsePortalAccessGrant;
import za.co.handyflow.platform.warehousing.domain.repository.WhseBillingInvoiceRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseClientRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInventoryRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseOutboundOrderRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhsePortalAccessGrantRepository;
import za.co.handyflow.platform.warehousing.dto.PortalClientSummaryResponse;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Client-portal-facing read side — direct analog of
 * CollAgencyPortalDataService. A client logged into the portal can see:
 * which clients (of this tenant's operator) they have access to, their
 * live stock position, inbound shipment status, outbound order status,
 * and billing invoice history — the client-facing value this module's
 * own domain analysis called out ("client inventory/order visibility,
 * self-service billing history").
 * <p>
 * Every method funnels through requireAccess() first, same "portal token
 * proves identity, the grant proves scope" split every other portal-data
 * service in this codebase already establishes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhsePortalDataService {

    private final WhsePortalAccessGrantRepository grantRepo;
    private final WhseClientRepository clientRepo;
    private final WhseInventoryRepository inventoryRepo;
    private final WhseInboundShipmentRepository shipmentRepo;
    private final WhseOutboundOrderRepository orderRepo;
    private final WhseBillingInvoiceRepository invoiceRepo;

    @Transactional(readOnly = true)
    public List<PortalClientSummaryResponse> getMyClients(UUID portalUserId) {
        return grantRepo.findActiveGrantsForUser(portalUserId).stream()
                .map(g -> clientRepo.findById(g.getClientId())
                        .map(c -> new PortalClientSummaryResponse(c.getId(), c.getTradingName()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WhseInventory> getMyInventory(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        return inventoryRepo.findAllForClient(resolveTenantId(clientId), clientId);
    }

    @Transactional(readOnly = true)
    public List<WhseInboundShipment> getMyInboundShipments(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        return shipmentRepo.findByClient(resolveTenantId(clientId), clientId,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    @Transactional(readOnly = true)
    public List<WhseOutboundOrder> getMyOutboundOrders(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        return orderRepo.findByClient(resolveTenantId(clientId), clientId,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    @Transactional(readOnly = true)
    public List<WhseBillingInvoice> getMyBillingInvoices(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        return invoiceRepo.findByClient(resolveTenantId(clientId), clientId,
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    private WhsePortalAccessGrant requireAccess(UUID portalUserId, UUID clientId) {
        return grantRepo.findActiveGrant(portalUserId, clientId)
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this client", HttpStatus.FORBIDDEN, "NO_ACCESS"));
    }

    /**
     * The portal side deliberately has no TenantId in scope (the caller is
     * an external client, not staff of this tenant) — same sidestep every
     * other portal-data service in this codebase uses, resolving straight
     * off the client/grant row. clientRepo.findById() here is
     * intentionally NOT tenant-filtered for that reason; requireAccess()
     * above is what actually gates visibility.
     */
    private UUID resolveTenantId(UUID clientId) {
        WhseClient client = clientRepo.findById(clientId)
                .orElseThrow(() -> new HandyFlowException("Client not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return client.getTenantId();
    }
}
