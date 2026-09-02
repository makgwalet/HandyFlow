package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvPortalAccessGrant;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvClientRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvPortalAccessGrantRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Invite/list/revoke for this module's own client portal grants.
 * {@code app.frontend.url} — confirmed the majority-used property
 * across independent sightings (RecruitmentAgencyService,
 * MarketingService) in this codebase, used here for the same reason.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainProvPortalService {

    private static final int INVITE_VALIDITY_DAYS = 7;
    private static final String MODULE_LABEL = "Training Provider";

    private final TrainProvPortalAccessGrantRepository grantRepo;
    private final TrainProvClientRepository clientRepo;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional
    public TrainProvPortalAccessGrant invite(TenantId tenantId, UUID clientId, String inviteEmail) {
        TrainProvClient client = clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", clientId.toString()));

        String token = UUID.randomUUID().toString();
        TrainProvPortalAccessGrant grant = TrainProvPortalAccessGrant.create(tenantId, clientId, inviteEmail, token,
                Instant.now().plus(INVITE_VALIDITY_DAYS, ChronoUnit.DAYS));
        grant = grantRepo.save(grant);

        String url = frontendUrl + "/training-provider/portal/auth/accept-invite?token=" + token;
        try {
            emailService.send(inviteEmail, "You've been invited to the " + client.getTradingName() + " training portal",
                    EmailTemplates.portalInvite(client.getTradingName(), MODULE_LABEL, url));
        } catch (Exception e) {
            log.warn("Failed to send portal invite email to {} for client={}: {}", inviteEmail, clientId, e.getMessage());
        }

        return grant;
    }

    @Transactional
    public void revoke(TenantId tenantId, UUID grantId) {
        TrainProvPortalAccessGrant grant = grantRepo.findByTenantAndId(tenantId, grantId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvPortalAccessGrant", grantId.toString()));
        grant.revoke();
        grantRepo.save(grant);
    }

    public List<TrainProvPortalAccessGrant> listForClient(TenantId tenantId, UUID clientId) {
        return grantRepo.findAllForClient(tenantId, clientId);
    }
}
