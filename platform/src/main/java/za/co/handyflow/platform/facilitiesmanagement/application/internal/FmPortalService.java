package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPortalAccessGrant;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmPortalAccessGrantRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Staff-facing invite/list/revoke for this module's own client portal grants.
 * <p>
 * Chunk 2: revoke requires the grant to belong to the client in the URL. Chunk 5: staff may only manage
 * portal access for clients within their {@link FmAccessScope}, and inviting or revoking portal access —
 * who from the client's side can see jobs and invoices — is audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmPortalService {

    private static final String MODULE_LABEL = "Facilities Management";

    private final FmPortalAccessGrantRepository grantRepo;
    private final FmClientRepository clientRepo;
    private final EmailService emailService;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public FmPortalAccessGrant invite(TenantId tenantId, UUID clientId, UUID invitedBy, String inviteEmail) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        FmClient client = clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", clientId.toString()));

        FmPortalAccessGrant grant = FmPortalAccessGrant.createInvite(tenantId.getValue(), clientId, inviteEmail, invitedBy);
        grant = grantRepo.save(grant);
        auditService.record(tenantId, "PORTAL_INVITED", "CLIENT", clientId,
                "email=" + grant.getInviteEmail() + " grant=" + grant.getId());

        String url = frontendUrl + "/facilitiesmanagement/portal/auth/accept-invite?token=" + grant.getInviteToken();
        try {
            emailService.send(inviteEmail, "You've been invited to the " + client.getTradingName() + " facilities portal",
                    EmailTemplates.portalInvite(client.getTradingName(), MODULE_LABEL, url));
        } catch (Exception e) {
            log.warn("Failed to send FM portal invite email to {} for client={}: {}", inviteEmail, clientId, e.getMessage());
        }

        return grant;
    }

    @Transactional
    public void revoke(TenantId tenantId, UUID clientId, UUID grantId, UUID revokedBy) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        FmPortalAccessGrant grant = grantRepo.findByTenantAndId(tenantId, grantId)
                .filter(g -> g.getClientId().equals(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("FmPortalAccessGrant", grantId.toString()));
        grant.revoke(revokedBy);
        grantRepo.save(grant);
        auditService.record(tenantId, "PORTAL_REVOKED", "CLIENT", clientId,
                "email=" + grant.getInviteEmail() + " grant=" + grantId);
        log.info("FM portal access revoked grantId={} client={} tenant={}", grantId, clientId, tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public List<FmPortalAccessGrant> listForClient(TenantId tenantId, UUID clientId) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        return grantRepo.findAllForClient(tenantId, clientId);
    }
}