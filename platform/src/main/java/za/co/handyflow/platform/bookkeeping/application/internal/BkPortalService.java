package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.domain.model.BkClient;
import za.co.handyflow.platform.bookkeeping.domain.model.BkPortalAccessGrant;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkClientRepository;
import za.co.handyflow.platform.bookkeeping.domain.repository.BkPortalAccessGrantRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Staff-facing invite/list/revoke for this module's own client portal
 * grants — direct mirror of {@code FmPortalService}. {@code
 * app.frontend.url} is the confirmed majority-used property for this
 * purpose across independent sightings in this codebase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BkPortalService {

    private static final String MODULE_LABEL = "Bookkeeping";

    private final BkPortalAccessGrantRepository grantRepo;
    private final BkClientRepository clientRepo;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public BkPortalAccessGrant invite(TenantId tenantId, UUID clientId, UUID invitedBy, String inviteEmail) {
        BkClient client = clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", clientId.toString()));

        BkPortalAccessGrant grant = BkPortalAccessGrant.createInvite(tenantId.getValue(), clientId, inviteEmail, invitedBy);
        grant = grantRepo.save(grant);

        String url = frontendUrl + "/bookkeeping/portal/auth/accept-invite?token=" + grant.getInviteToken();
        try {
            emailService.send(inviteEmail, "You've been invited to the " + client.getTradingName() + " bookkeeping portal",
                    EmailTemplates.portalInvite(client.getTradingName(), MODULE_LABEL, url));
        } catch (Exception e) {
            log.warn("Failed to send bookkeeping portal invite email to {} for client={}: {}", inviteEmail, clientId, e.getMessage());
        }

        return grant;
    }

    @Transactional
    public void revoke(TenantId tenantId, UUID grantId, UUID revokedBy) {
        BkPortalAccessGrant grant = grantRepo.findByTenantAndId(tenantId, grantId)
                .orElseThrow(() -> new ResourceNotFoundException("BkPortalAccessGrant", grantId.toString()));
        grant.revoke(revokedBy);
        grantRepo.save(grant);
        log.info("Bookkeeping portal access revoked grantId={} tenant={}", grantId, tenantId.getValue());
    }

    public List<BkPortalAccessGrant> listForClient(TenantId tenantId, UUID clientId) {
        return grantRepo.findAllForClient(tenantId, clientId);
    }
}
