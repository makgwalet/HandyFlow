package za.co.handyflow.platform.auditor.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;
import za.co.handyflow.platform.auditor.domain.repository.AuditorAccessGrantRepository;
import za.co.handyflow.platform.auditor.dto.AuditorAccessGrantResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditorService {

    private final AuditorAccessGrantRepository grantRepo;
    private final za.co.handyflow.platform.auditor.domain.repository.ExternalAuditEngagementRepository engagementRepo;
    private final EmailService emailService;
    private final za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public AuditorAccessGrantResponse inviteAuditor(TenantId tenantId, String email,
                                                    String businessName, UUID invitedBy) {
        return inviteAuditor(tenantId, email, businessName, invitedBy, null, null, null);
    }

    @Transactional
    public AuditorAccessGrantResponse inviteAuditor(TenantId tenantId, String email, String businessName, UUID invitedBy,
                                                    LocalDate expiresOn, String scopeNote) {
        return inviteAuditor(tenantId, email, businessName, invitedBy, expiresOn, scopeNote, null);
    }

    @Transactional
    public AuditorAccessGrantResponse inviteAuditor(TenantId tenantId, String email, String businessName, UUID invitedBy,
                                                    LocalDate expiresOn, String scopeNote, UUID engagementId) {
        if (engagementId != null) {
            var eng = engagementRepo.findByTenantIdAndId(tenantId.getValue(), engagementId)
                    .orElseThrow(() -> new ResourceNotFoundException("ExternalAuditEngagement", engagementId.toString()));
            if (!eng.acceptsInvites()) {
                throw new HandyFlowException("This engagement is " + eng.getStatus().toLowerCase() + "; auditors can no longer be added to it",
                        HttpStatus.CONFLICT, "ENGAGEMENT_CLOSED");
            }
        }
        Instant expiry = expiryFrom(expiresOn);
        boolean alreadyGranted = grantRepo.findByTenantId(tenantId.getValue()).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email) && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new HandyFlowException(
                    "This email already has a pending or active invite",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        AuditorAccessGrant grant = AuditorAccessGrant.createInvite(tenantId.getValue(), email, invitedBy, expiry, scopeNote);
        grant.attachToEngagement(engagementId);
        grantRepo.save(grant);
        trail.record(tenantId, "AUDITOR_INVITED", "AUDITOR_GRANT", grant.getId(), null,
                java.util.Map.of("email", grant.getInviteEmail(), "expiresAt", String.valueOf(grant.getExpiresAt()), "scope", String.valueOf(grant.getScopeNote()), "engagementId", String.valueOf(engagementId)));

        emailService.send(email, businessName + " has invited you to review their financial records",
                EmailTemplates.portalInvite(businessName, "Auditor Access",
                        frontendUrl + "/auditor/portal/auth/accept-invite?token=" + grant.getInviteToken()));

        log.info("Auditor invite sent: {} -> tenant={}", email, tenantId.getValue());
        return toResponse(grant);
    }

    /** The last moment of the chosen day (South African time). Null means the default period. */
    static Instant expiryFrom(LocalDate expiresOn) {
        if (expiresOn == null) return null;
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        if (!expiresOn.isAfter(today)) {
            throw new HandyFlowException("The expiry date must be in the future", HttpStatus.BAD_REQUEST, "EXPIRY_IN_PAST");
        }
        if (expiresOn.isAfter(today.plusDays(AuditorAccessGrant.MAX_GRANT_DAYS))) {
            throw new HandyFlowException("Access can be granted for at most " + AuditorAccessGrant.MAX_GRANT_DAYS
                    + " days at a time; re-date it later if the audit runs longer", HttpStatus.BAD_REQUEST, "EXPIRY_TOO_FAR");
        }
        return expiresOn.plusDays(1).atStartOfDay(ZoneId.of("Africa/Johannesburg")).toInstant();
    }

    @Transactional
    public AuditorAccessGrantResponse changeExpiry(TenantId tenantId, UUID grantId, LocalDate expiresOn) {
        if (expiresOn == null) throw new HandyFlowException("An expiry date is required", HttpStatus.BAD_REQUEST, "EXPIRY_REQUIRED");
        AuditorAccessGrant grant = grantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditorAccessGrant", grantId.toString()));
        String beforeExpiry = String.valueOf(grant.getExpiresAt());
        try {
            grant.changeExpiry(expiryFrom(expiresOn));
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "GRANT_REVOKED");
        }
        grantRepo.save(grant);
        trail.record(tenantId, "AUDITOR_REDATED", "AUDITOR_GRANT", grantId, java.util.Map.of("expiresAt", beforeExpiry),
                java.util.Map.of("expiresAt", String.valueOf(grant.getExpiresAt())));
        return toResponse(grant);
    }

    @Transactional
    public AuditorAccessGrantResponse revokeAuditorAccess(TenantId tenantId, UUID grantId, UUID revokedBy) {
        AuditorAccessGrant grant = grantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditorAccessGrant", grantId.toString()));
        String before = grant.getStatus();
        grant.revoke(revokedBy);
        grantRepo.save(grant);
        trail.record(tenantId, "AUDITOR_REVOKED", "AUDITOR_GRANT", grantId, java.util.Map.of("status", before), java.util.Map.of("status", "REVOKED"));
        log.info("Auditor access revoked: grant={} tenant={}", grantId, tenantId.getValue());
        return toResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<AuditorAccessGrantResponse> listAuditorGrants(TenantId tenantId) {
        return grantRepo.findByTenantId(tenantId.getValue()).stream().map(this::toResponse).toList();
    }

    private AuditorAccessGrantResponse toResponse(AuditorAccessGrant g) {
        return new AuditorAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(),
                g.getInvitedAt(), g.getAcceptedAt(), g.getRevokedAt(), g.getExpiresAt(), g.getScopeNote(),
                g.getExpiresAt() != null && !Instant.now().isBefore(g.getExpiresAt()), g.getEngagementId());
    }
}