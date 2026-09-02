package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPortalAccessGrant;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyPortalAccessGrantRepository;
import za.co.handyflow.platform.collectionsagency.dto.PortalAccessGrantResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Invite/list/revoke of a creditor client's own portal access — the
 * admin-side half of the portal feature. Direct structural mirror of
 * the portal-grant chunk embedded in RecruitmentAgencyService/
 * BookingAgencyService/PayrollBureauService, pulled out into its own
 * dedicated service here because this module already decomposes each
 * entity into its own service (CollAgencyClientService,
 * CollAgencyCollectorService, etc.) rather than one large per-module
 * God-service — that decomposition choice is this module's own, not a
 * deviation from a confirmed shared pattern.
 * <p>
 * {@code app.frontend.url} is used here, not {@code handyflow.frontend-url}
 * — confirmed as the property RecruitmentAgencyService/BookingAgencyService/
 * MarketingService actually use (2+ independent confirmed sightings);
 * HrEmployeePortalAuthService uses a different property name
 * ({@code handyflow.frontend-url}), which is a pre-existing inconsistency
 * in the real codebase, not something to silently pick a side on and fix
 * here — flagging it, not fixing it, per this session's ground rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollAgencyPortalService {

    private final CollAgencyPortalAccessGrantRepository grantRepo;
    private final CollAgencyClientService clientService;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public PortalAccessGrantResponse invite(TenantId tenantId, UUID clientId, String email, UUID invitedBy) {
        CollAgencyClient client = clientService.findActive(tenantId, clientId);

        boolean alreadyGranted = grantRepo.findByTenantAndClient(tenantId.getValue(), clientId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email.trim())
                        && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new HandyFlowException(
                    "This email already has a pending or active invite for this client",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        CollAgencyPortalAccessGrant grant = CollAgencyPortalAccessGrant.createInvite(
                tenantId.getValue(), clientId, email, invitedBy);
        grantRepo.save(grant);

        try {
            emailService.send(email, client.getTradingName() + " has invited you to their collections portal",
                    EmailTemplates.portalInvite(
                            client.getTradingName(),
                            "Collections Agency",
                            frontendUrl + "/collections-agency/portal/auth/register?token=" + grant.getInviteToken()));
        } catch (Exception e) {
            // Same principle as every other portal invite hookup in this codebase: the
            // grant is already saved — an email failure here must not undo it. Staff
            // can re-check via getPortalAccessGrants() and resend if needed.
            log.error("[CollectionsAgency] Failed to send portal invite email to={} client={}: {}",
                    email, clientId, e.getMessage(), e);
        }

        log.info("[CollectionsAgency] Portal invite sent: {} -> client={}", email, clientId);
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
        CollAgencyPortalAccessGrant grant = grantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("PortalAccessGrant", grantId.toString()));
        if (!grant.getClientId().equals(clientId)) {
            throw new ResourceNotFoundException("PortalAccessGrant", grantId.toString());
        }
        grant.revoke(revokedBy);
        grantRepo.save(grant);
        log.info("[CollectionsAgency] Portal access revoked: grant={} client={}", grantId, clientId);
        return toGrantResponse(grant);
    }

    private PortalAccessGrantResponse toGrantResponse(CollAgencyPortalAccessGrant g) {
        return new PortalAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(), g.getInvitedAt(),
                g.getAcceptedAt(), g.getRevokedAt());
    }
}
