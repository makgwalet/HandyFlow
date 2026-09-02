package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpPortalAccessGrant;
import za.co.handyflow.platform.legalpractice.domain.repository.LpPortalAccessGrantRepository;
import za.co.handyflow.platform.legalpractice.dto.LpClientResponse;
import za.co.handyflow.platform.legalpractice.dto.LpInvoiceResponse;
import za.co.handyflow.platform.legalpractice.dto.LpMatterResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Client-facing reads, gated per request by a live {@link LpPortalAccessGrant}
 * — mirrors {@code HrEmployeePortalDataService.requireAccess()}'s exact
 * shape. Deliberately reuses {@code LpClientService}/{@code LpMatterService}/
 * {@code LpBillingService}'s own read methods (and their response DTOs)
 * rather than duplicating mapping logic — the tenant scope for every call
 * comes from the grant itself, never from the caller, since a portal
 * session carries no {@code TenantContext} (see {@code PortalJwtFilter}'s
 * own Javadoc: a portal user isn't tied to one tenant the way staff are).
 */
@Service
@RequiredArgsConstructor
public class LpPortalDataService {

    private final LpPortalAccessGrantRepository grantRepo;
    private final LpClientService clientService;
    private final LpMatterService matterService;
    private final LpBillingService billingService;

    @Transactional(readOnly = true)
    public LpClientResponse getMyClient(UUID portalUserId, UUID clientId) {
        TenantId tenantId = requireAccess(portalUserId, clientId);
        return clientService.getClient(tenantId, clientId);
    }

    @Transactional(readOnly = true)
    public Page<LpMatterResponse> listMyMatters(UUID portalUserId, UUID clientId, Pageable pageable) {
        TenantId tenantId = requireAccess(portalUserId, clientId);
        return matterService.listForClient(tenantId, clientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LpInvoiceResponse> listMyInvoices(UUID portalUserId, UUID clientId, Pageable pageable) {
        TenantId tenantId = requireAccess(portalUserId, clientId);
        return billingService.listForClient(tenantId, clientId, pageable);
    }

    private TenantId requireAccess(UUID portalUserId, UUID clientId) {
        LpPortalAccessGrant grant = grantRepo.findActiveGrant(portalUserId, clientId)
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this client record", HttpStatus.FORBIDDEN, "NO_ACCESS"));
        return grant.getTenantId();
    }
}
