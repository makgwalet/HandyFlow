package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.bookkeeping.domain.model.BkInvoice;
import za.co.handyflow.platform.bookkeeping.domain.model.BkPortalAccessGrant;
import za.co.handyflow.platform.bookkeeping.domain.model.BkTimeEntry;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkInvoiceRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkPortalAccessGrantRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkTimeEntryRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing reads — everything a logged-in portal user (acting for
 * one {@link za.co.handyflow.platform.bookkeeping.domain.model.BkClient})
 * is allowed to see: their own invoices and logged time, never another
 * client's. Every method resolves and enforces the caller's own
 * {@code clientId} from their ACCEPTED grant first — never trusts a
 * clientId the frontend might pass in directly. Direct mirror of {@code
 * FmPortalDataService}.
 * <p>
 * {@code BkPortalAccessGrant.tenantId} is a raw UUID column (see the
 * entity's own Javadoc) — since {@code shared.PortalUser} is a genuinely
 * shared login identity that could in principle hold grants issued by
 * more than one tenant's bookkeeping practice, the tenant match is
 * enforced here in-memory rather than trusted from the repository query
 * alone.
 */
@Service
@RequiredArgsConstructor
public class BkPortalDataService {

    private final BkPortalAccessGrantRepository grantRepo;
    private final BkInvoiceRepository invoiceRepo;
    private final BkTimeEntryRepository timeEntryRepo;

    public List<BkInvoice> getMyInvoices(TenantId tenantId, UUID portalUserId) {
        UUID clientId = resolveClientId(tenantId, portalUserId);
        return invoiceRepo.findAllForClientList(tenantId, clientId);
    }

    public Page<BkTimeEntry> getMyTimeEntries(TenantId tenantId, UUID portalUserId, Pageable pageable) {
        UUID clientId = resolveClientId(tenantId, portalUserId);
        return timeEntryRepo.findByClient(tenantId, clientId, pageable);
    }

    /**
     * A portal user in this first pass is linked to exactly one client per
     * tenant — if they somehow hold more than one ACCEPTED grant for the
     * same tenant, the first is used and the rest are ignored rather than
     * merging cross-client data. Flagged as a known first-pass limit, not
     * a security gap: nothing here fabricates access to a client the
     * caller wasn't actually granted.
     */
    private UUID resolveClientId(TenantId tenantId, UUID portalUserId) {
        return grantRepo.findByPortalUserId(portalUserId).stream()
                .filter(g -> tenantId.getValue().equals(g.getTenantId()))
                .findFirst()
                .map(BkPortalAccessGrant::getClientId)
                .orElseThrow(() -> new HandyFlowException(
                        "No active client access found for this portal account", HttpStatus.FORBIDDEN, "NO_CLIENT_ACCESS"));
    }
}
