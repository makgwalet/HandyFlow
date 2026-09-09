package za.co.handyflow.platform.property.application.internal;

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
import za.co.handyflow.platform.property.domain.model.Lease;
import za.co.handyflow.platform.property.domain.model.PropPortalAccessGrant;
import za.co.handyflow.platform.property.domain.repository.LeaseRepository;
import za.co.handyflow.platform.property.domain.repository.PropPortalAccessGrantRepository;
import za.co.handyflow.platform.property.dto.PortalAccessGrantResponse;

import java.util.List;
import java.util.UUID;

/**
 * Invite/list/revoke of a tenant's own portal access — direct
 * structural mirror of WhsePortalService/CollAgencyPortalService (see
 * that class's own Javadoc for why app.frontend.url is used here rather
 * than handyflow.frontend-url — a pre-existing inconsistency flagged,
 * not silently resolved, matching how every sibling module handles it).
 * Scoped to a lease rather than a client — see PropPortalAccessGrant's
 * own class comment for why.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropPortalService {

    private final PropPortalAccessGrantRepository grantRepo;
    private final LeaseRepository leaseRepository;

    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public PortalAccessGrantResponse invite(TenantId tenantId, UUID leaseId, String email, UUID invitedBy) {
        Lease lease = leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));

        boolean alreadyGranted = grantRepo.findByTenantAndLease(tenantId.getValue(), leaseId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email.trim())
                        && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new HandyFlowException(
                    "This email already has a pending or active invite for this lease",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        PropPortalAccessGrant grant = PropPortalAccessGrant.createInvite(tenantId.getValue(), leaseId, email, invitedBy);
        grantRepo.save(grant);

        try {
            emailService.send(email, "You've been invited to your tenant portal",
                    EmailTemplates.portalInvite(
                            lease.getLesseeName(),
                            "Property",
                            frontendUrl + "/property/portal/auth/register?token=" + grant.getInviteToken()));
        } catch (Exception e) {
            log.error("[Property] Failed to send portal invite email to={} lease={}: {}", email, leaseId,
                    e.getMessage(), e);
        }

        log.info("[Property] Portal invite sent: {} -> lease={}", email, leaseId);
        return toGrantResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<PortalAccessGrantResponse> getPortalAccessGrants(TenantId tenantId, UUID leaseId) {
        leaseRepository.findActiveById(tenantId, leaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Lease", leaseId.toString()));
        return grantRepo.findByTenantAndLease(tenantId.getValue(), leaseId).stream()
                .map(this::toGrantResponse).toList();
    }

    @Transactional
    public PortalAccessGrantResponse revoke(TenantId tenantId, UUID leaseId, UUID grantId, UUID revokedBy) {
        PropPortalAccessGrant grant = grantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("PortalAccessGrant", grantId.toString()));
        if (!grant.getLeaseId().equals(leaseId)) {
            throw new ResourceNotFoundException("PortalAccessGrant", grantId.toString());
        }
        grant.revoke(revokedBy);
        grantRepo.save(grant);
        log.info("[Property] Portal access revoked: grant={} lease={}", grantId, leaseId);
        return toGrantResponse(grant);
    }

    private PortalAccessGrantResponse toGrantResponse(PropPortalAccessGrant g) {
        return new PortalAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(), g.getInvitedAt(),
                g.getAcceptedAt(), g.getRevokedAt());
    }
}
