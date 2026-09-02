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
 * Staff-facing invite/list/revoke for this module's own client portal
 * grants — direct mirror of TrainProvPortalService. {@code app.frontend.url}
 * is the confirmed majority-used property for this purpose across
 * independent sightings (RecruitmentAgencyService, MarketingService,
 * TrainProvPortalService) in this codebase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmPortalService {

    private static final String MODULE_LABEL = "Facilities Management";

    private final FmPortalAccessGrantRepository grantRepo;
    private final FmClientRepository clientRepo;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public FmPortalAccessGrant invite(TenantId tenantId, UUID clientId, UUID invitedBy, String inviteEmail) {
        FmClient client = clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", clientId.toString()));

        FmPortalAccessGrant grant = FmPortalAccessGrant.createInvite(tenantId.getValue(), clientId, inviteEmail, invitedBy);
        grant = grantRepo.save(grant);

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
    public void revoke(TenantId tenantId, UUID grantId, UUID revokedBy) {
        FmPortalAccessGrant grant = grantRepo.findByTenantAndId(tenantId, grantId)
                .orElseThrow(() -> new ResourceNotFoundException("FmPortalAccessGrant", grantId.toString()));
        grant.revoke(revokedBy);
        grantRepo.save(grant);
        log.info("FM portal access revoked grantId={} tenant={}", grantId, tenantId.getValue());
    }

    public List<FmPortalAccessGrant> listForClient(TenantId tenantId, UUID clientId) {
        return grantRepo.findAllForClient(tenantId, clientId);
    }
}
