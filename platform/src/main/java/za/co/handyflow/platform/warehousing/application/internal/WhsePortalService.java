package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhsePortalAccessGrant;
import za.co.handyflow.platform.warehousing.domain.repository.WhsePortalAccessGrantRepository;
import za.co.handyflow.platform.warehousing.dto.PortalAccessGrantResponse;

import java.util.List;
import java.util.UUID;

/**
 * Invite/list/revoke of a client's own portal access — direct structural
 * mirror of CollAgencyPortalService (see that class's own Javadoc for why
 * {@code app.frontend.url} is used here rather than
 * {@code handyflow.frontend-url}: the majority-used property across this
 * codebase's provider modules, a pre-existing inconsistency flagged, not
 * silently resolved).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhsePortalService {

    private final WhsePortalAccessGrantRepository grantRepo;
    private final WhseClientService clientService;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public PortalAccessGrantResponse invite(TenantId tenantId, UUID clientId, String email, UUID invitedBy) {
        WhseClient client = clientService.findActive(tenantId, clientId);

        boolean alreadyGranted = grantRepo.findByTenantAndClient(tenantId.getValue(), clientId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email.trim())
                        && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new HandyFlowException(
                    "This email already has a pending or active invite for this client",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        WhsePortalAccessGrant grant = WhsePortalAccessGrant.createInvite(tenantId.getValue(), clientId, email, invitedBy);
        grantRepo.save(grant);

        try {
            emailService.send(email, client.getTradingName() + " has invited you to their warehousing portal",
                    EmailTemplates.portalInvite(
                            client.getTradingName(),
                            "Warehousing",
                            frontendUrl + "/warehousing/portal/auth/register?token=" + grant.getInviteToken()));
        } catch (Exception e) {
            log.error("[Warehousing] Failed to send portal invite email to={} client={}: {}", email, clientId,
                    e.getMessage(), e);
        }

        log.info("[Warehousing] Portal invite sent: {} -> client={}", email, clientId);
        return toGrantResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<PortalAccessGrantResponse> getPortalAccessGrants(TenantId tenantId, UUID clientId) {
        clientService.findActive(tenantId, clientId);
        return grantRepo.findByTenantAndClient(tenantId.getValue(), clientId).stream()
                .map(this::toGrantResponse).toList();
    }

    @Transactional
    public PortalAccessGrantResponse revoke(TenantId tenantId, UUID clientId, UUID grantId, UUID revokedBy) {
        WhsePortalAccessGrant grant = grantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("PortalAccessGrant", grantId.toString()));
        if (!grant.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("PortalAccessGrant", grantId.toString());
        }
        grant.revoke(revokedBy);
        grantRepo.save(grant);
        log.info("[Warehousing] Portal access revoked: grant={} client={}", grantId, clientId);
        return toGrantResponse(grant);
    }

    private PortalAccessGrantResponse toGrantResponse(WhsePortalAccessGrant g) {
        return new PortalAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(), g.getInvitedAt(),
                g.getAcceptedAt(), g.getRevokedAt());
    }
}
