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

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditorService {

    private final AuditorAccessGrantRepository grantRepo;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public AuditorAccessGrantResponse inviteAuditor(TenantId tenantId, String email,
                                                    String businessName, UUID invitedBy) {
        boolean alreadyGranted = grantRepo.findByTenantId(tenantId.getValue()).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email) && !"REVOKED".equals(g.getStatus()));
        if (alreadyGranted) {
            throw new HandyFlowException(
                    "This email already has a pending or active invite",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        AuditorAccessGrant grant = AuditorAccessGrant.createInvite(tenantId.getValue(), email, invitedBy);
        grantRepo.save(grant);

        emailService.send(email, businessName + " has invited you to review their financial records",
                EmailTemplates.portalInvite(businessName, "Auditor Access",
                        frontendUrl + "/auditor/portal/auth/accept-invite?token=" + grant.getInviteToken()));

        log.info("Auditor invite sent: {} -> tenant={}", email, tenantId.getValue());
        return toResponse(grant);
    }

    @Transactional
    public AuditorAccessGrantResponse revokeAuditorAccess(TenantId tenantId, UUID grantId, UUID revokedBy) {
        AuditorAccessGrant grant = grantRepo.findByTenantIdAndId(tenantId.getValue(), grantId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditorAccessGrant", grantId.toString()));
        grant.revoke(revokedBy);
        grantRepo.save(grant);
        log.info("Auditor access revoked: grant={} tenant={}", grantId, tenantId.getValue());
        return toResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<AuditorAccessGrantResponse> listAuditorGrants(TenantId tenantId) {
        return grantRepo.findByTenantId(tenantId.getValue()).stream().map(this::toResponse).toList();
    }

    private AuditorAccessGrantResponse toResponse(AuditorAccessGrant g) {
        return new AuditorAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(),
                g.getInvitedAt(), g.getAcceptedAt(), g.getRevokedAt());
    }
}