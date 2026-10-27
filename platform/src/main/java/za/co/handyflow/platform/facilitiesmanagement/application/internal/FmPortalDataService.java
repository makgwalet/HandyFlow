package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Set;
import java.util.UUID;

/**
 * Client-facing reads — everything a logged-in portal user (acting for one client) may see: their own
 * sites, work orders and invoices, never another client's. Every method resolves the caller's clientId from
 * their ACTIVE grant first and never trusts a clientId from the frontend. The tenant match is enforced in
 * memory because {@code PortalUser} is a shared identity that can hold grants from several tenants.
 * <p>
 * Chunk 2: DRAFT invoices are never returned. Chunk 6: internal work-order states stay internal —
 * REQUESTED and REJECTED are never listed, and PENDING_VERIFICATION is presented to clients as IN_PROGRESS
 * (see {@code FmPortalDataController}); a client filtering by IN_PROGRESS gets both.
 */
@Service
@RequiredArgsConstructor
public class FmPortalDataService {

    private static final List<String> CLIENT_VISIBLE_STATUSES = List.of(
            "OPEN", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "PENDING_VERIFICATION", "COMPLETED", "CANCELLED");

    /** Chunk 7: a client only ever sees an invoice once it has been approved AND sent — never DRAFT, APPROVED or CANCELLED. */
    private static final Set<String> CLIENT_VISIBLE_INVOICE_STATUSES = Set.of("SENT", "PARTIAL", "PAID");

    private final FmPortalAccessGrantRepository grantRepo;
    private final FmSiteRepository siteRepo;
    private final FmWorkOrderRepository workOrderRepo;
    private final FmInvoiceRepository invoiceRepo;

    @Transactional(readOnly = true)
    public Page<FmSite> getMySites(TenantId tenantId, UUID portalUserId, Pageable pageable) {
        UUID clientId = resolveClientId(tenantId, portalUserId);
        return siteRepo.findAllActiveForClient(tenantId, clientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<FmWorkOrder> getMyWorkOrders(TenantId tenantId, UUID portalUserId, String status, Pageable pageable) {
        UUID clientId = resolveClientId(tenantId, portalUserId);
        return workOrderRepo.findAllForClientPortal(tenantId, clientId, portalStatusFilter(status), pageable);
    }

    /** Only invoices the FM company has actually sent: SENT, PARTIAL or PAID. */
    @Transactional(readOnly = true)
    public List<FmInvoice> getMyInvoices(TenantId tenantId, UUID portalUserId) {
        UUID clientId = resolveClientId(tenantId, portalUserId);
        return invoiceRepo.findAllForClientList(tenantId, clientId).stream()
                .filter(i -> CLIENT_VISIBLE_INVOICE_STATUSES.contains(i.getStatus()))
                .toList();
    }

    /** Translates a client-facing status filter into the internal statuses it covers. Unknown/internal filters match nothing. */
    private List<String> portalStatusFilter(String status) {
        if (status == null || status.isBlank()) return CLIENT_VISIBLE_STATUSES;
        String s = status.trim().toUpperCase();
        if ("IN_PROGRESS".equals(s)) return List.of("IN_PROGRESS", "PENDING_VERIFICATION");
        if (CLIENT_VISIBLE_STATUSES.contains(s) && !"PENDING_VERIFICATION".equals(s)) return List.of(s);
        return List.of("NONE");
    }

    /**
     * A portal user is linked to exactly one client per tenant in this pass. If they somehow hold more than
     * one ACTIVE grant for the same tenant, the first is used rather than merging cross-client data.
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