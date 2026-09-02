package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPortalAccessGrant;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyTrustTransaction;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyClientRepository;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyDebtorAccountRepository;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyPortalAccessGrantRepository;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyTrustTransactionRepository;
import za.co.handyflow.platform.collectionsagency.dto.PortalClientSummaryResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Client-portal-facing read side — the direct analog of
 * PayrollBureauPortalDataService/RecruitmentAgencyPortalDataController's
 * service. A creditor client logged into the portal can see: which
 * clients (of this tenant's agency) they have access to, their placed
 * debtor accounts and recovery status, and their trust/remittance
 * transaction history — exactly the three things this module's own
 * domain analysis called out as the client-facing value ("client
 * portfolio/recovery reporting").
 * <p>
 * Every method funnels through requireAccess() first, same
 * "portal token proves identity, the grant proves scope" split every
 * other portal-data service in this codebase already establishes
 * (PortalJwtService's own Javadoc explains why: the JWT is deliberately
 * NOT client-scoped, so authorization must be re-checked per request
 * against the grant table, not trusted from the token).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollAgencyPortalDataService {

    private final CollAgencyPortalAccessGrantRepository grantRepo;
    private final CollAgencyClientRepository clientRepo;
    private final CollAgencyDebtorAccountRepository debtorAccountRepo;
    private final CollAgencyTrustTransactionRepository trustRepo;

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
    public List<CollAgencyDebtorAccount> getMyDebtorAccounts(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        return debtorAccountRepo.findAllActiveForClient(resolveTenantId(clientId), clientId);
    }

    @Transactional(readOnly = true)
    public List<CollAgencyTrustTransaction> getMyTrustStatement(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        return trustRepo.findByClient(resolveTenantId(clientId), clientId);
    }

    private CollAgencyPortalAccessGrant requireAccess(UUID portalUserId, UUID clientId) {
        return grantRepo.findActiveGrant(portalUserId, clientId)
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this client", HttpStatus.FORBIDDEN, "NO_ACCESS"));
    }

    /**
     * The portal side deliberately has no TenantId in scope (the caller is
     * an external client, not staff of this tenant) — every other portal-
     * data service in this codebase sidesteps this the same way, by
     * resolving straight off the client/grant row rather than requiring a
     * tenant context the caller can't supply. clientRepo.findById() here
     * is intentionally NOT tenant-filtered for that reason; the access
     * check above (requireAccess) is what actually gates visibility, not
     * this lookup.
     */
    private UUID resolveTenantId(UUID clientId) {
        CollAgencyClient client = clientRepo.findById(clientId)
                .orElseThrow(() -> new HandyFlowException("Client not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return client.getTenantId();
    }
}
