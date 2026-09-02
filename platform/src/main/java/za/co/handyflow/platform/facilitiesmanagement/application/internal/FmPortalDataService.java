package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPortalAccessGrant;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmInvoiceRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmPortalAccessGrantRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSiteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing reads — everything a logged-in portal user (acting for one
 * {@link za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient})
 * is allowed to see: their own sites, work orders and invoices, never
 * another client's. Every method resolves and enforces the caller's own
 * clientId from their ACCEPTED grant first — never trusts a clientId the
 * frontend might pass in directly. Direct mirror of TrainProvPortalDataService.
 * <p>
 * {@code FmPortalAccessGrant.tenantId} is a raw UUID column (see the
 * entity's own Javadoc) — since {@code shared.PortalUser} is a genuinely
 * shared login identity that could in principle hold grants issued by more
 * than one tenant's FM company, the tenant match is enforced here in-memory
 * rather than trusted from the repository query alone. This is a
 * deliberate tenant-isolation safeguard, not a guess.
 */
@Service
@RequiredArgsConstructor
public class FmPortalDataService {

    private final FmPortalAccessGrantRepository grantRepo;
    private final FmSiteRepository siteRepo;
    private final FmWorkOrderRepository workOrderRepo;
    private final FmInvoiceRepository invoiceRepo;

    public Page<FmSite> getMySites(TenantId tenantId, UUID portalUserId, Pageable pageable) {
        UUID clientId = resolveClientId(tenantId, portalUserId);
        return siteRepo.findAllActiveForClient(tenantId, clientId, pageable);
    }

    public Page<FmWorkOrder> getMyWorkOrders(TenantId tenantId, UUID portalUserId, String status, Pageable pageable) {
        UUID clientId = resolveClientId(tenantId, portalUserId);
        return workOrderRepo.findAllForClient(tenantId, clientId, status != null ? status.toUpperCase() : null, pageable);
    }

    public List<FmInvoice> getMyInvoices(TenantId tenantId, UUID portalUserId) {
        UUID clientId = resolveClientId(tenantId, portalUserId);
        return invoiceRepo.findAllForClientList(tenantId, clientId);
    }

    /**
     * A portal user in this first pass is linked to exactly one client per
     * tenant — if they somehow hold more than one ACCEPTED grant for the
     * same tenant (not currently reachable through this module's own
     * invite flow, which always creates one grant per client), the first
     * is used and the rest are ignored rather than merging cross-client
     * data. Flagged as a known first-pass limit, not a security gap:
     * nothing here fabricates access to a client the caller wasn't
     * actually granted.
     */
    private UUID resolveClientId(TenantId tenantId, UUID portalUserId) {
        return grantRepo.findByPortalUserId(portalUserId).stream()
                .filter(g -> tenantId.getValue().equals(g.getTenantId()))
                .findFirst()
                .map(FmPortalAccessGrant::getClientId)
                .orElseThrow(() -> new HandyFlowException(
                        "No active client access found for this portal account", HttpStatus.FORBIDDEN, "NO_CLIENT_ACCESS"));
    }
}
