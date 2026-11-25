package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.model.CompliancePortalGrant;
import za.co.handyflow.platform.complianceservices.domain.model.PortalRules;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.CompliancePortalGrantRepository;
import za.co.handyflow.platform.complianceservices.dto.PortalDtos.GrantView;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff side of the client portal: who can sign in to see a client's quotes, agreements and invoices. The invite link
 * goes to the person by email and is never shown to staff, so a member of staff cannot use it to sign in as the client.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompliancePortalAdminService {

    private final ComplianceClientRepository clients;
    private final CompliancePortalGrantRepository grants;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<GrantView> list(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        List<GrantView> out = new ArrayList<>();
        for (CompliancePortalGrant g : grants.findForClient(tenantId, clientId)) out.add(view(g));
        return out;
    }

    @Transactional
    public GrantView invite(TenantId tenantId, UUID clientId, String email, UUID by) {
        ComplianceClient c = requireClient(tenantId, clientId);
        String problem = PortalRules.emailProblem(email);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "PORTAL_INVALID");
        Optional<CompliancePortalGrant> live = grants.findLive(tenantId, clientId, PortalRules.normaliseEmail(email));
        if (live.isPresent()) {
            throw new HandyFlowException(live.get().isActive() ? "This person already has access" : "This person has already been invited. Revoke that invite to send another",
                    HttpStatus.CONFLICT, "PORTAL_ALREADY_INVITED");
        }
        CompliancePortalGrant g = CompliancePortalGrant.invite(tenantId.getValue(), clientId, email, by, Instant.now());
        String url = frontendUrl + "/compliance-services/portal/auth/accept-invite?token=" + g.getInviteToken();
        boolean sent;
        try {
            emailService.send(g.getInviteEmail(), "You've been invited to the " + c.getName() + " compliance portal",
                    EmailTemplates.portalInvite(c.getName(), "Compliance Services", url));
            sent = true;
        } catch (Exception e) {
            log.warn("Compliance portal invite email to {} failed for client {}: {}", g.getInviteEmail(), clientId, e.getMessage());
            sent = false;
        }
        g.emailWasSent(sent);
        grants.save(g);
        return view(g);
    }

    @Transactional
    public GrantView revoke(TenantId tenantId, UUID clientId, UUID grantId, UUID by) {
        requireClient(tenantId, clientId);
        CompliancePortalGrant g = grants.findOne(tenantId, grantId).filter(x -> x.getClientId().equals(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("CompliancePortalGrant", grantId.toString()));
        try {
            g.revoke(by, Instant.now());
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "PORTAL_STATE");
        }
        grants.save(g);
        log.info("Compliance portal access revoked grant={} client={} tenant={}", grantId, clientId, tenantId.getValue());
        return view(g);
    }

    private static GrantView view(CompliancePortalGrant g) {
        return new GrantView(g.getId(), g.getClientId(), g.getInviteEmail(), g.getStatus(), g.isEmailSent(), g.getInvitedAt(), g.getAcceptedAt(), g.getRevokedAt(),
                PortalRules.PENDING.equals(g.getStatus()) ? g.getInviteTokenExpiresAt() : null);
    }

    private ComplianceClient requireClient(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }
}
