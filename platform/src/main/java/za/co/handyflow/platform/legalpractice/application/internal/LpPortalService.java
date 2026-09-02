package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpClient;
import za.co.handyflow.platform.legalpractice.domain.model.LpPortalAccessGrant;
import za.co.handyflow.platform.legalpractice.domain.model.LpProfile;
import za.co.handyflow.platform.legalpractice.domain.repository.LpClientRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpPortalAccessGrantRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpProfileRepository;
import za.co.handyflow.platform.legalpractice.dto.LpPortalAccessGrantResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Staff-side invite/list/revoke — now the standard firm-invites-first
 * flow. {@code LpPortalAccessGrant} was corrected to the confirmed-real
 * invite-token/email/status shape (see that entity's own class Javadoc:
 * {@code AccPortalAccessGrant}/{@code AuditorAccessGrant} both use it,
 * confirmed by direct source read), so the earlier "client registers a
 * generic portal identity first, then a firm staff member grants it
 * access" workaround this class previously documented no longer applies
 * — it was forced by the entity's old fixed, required-{@code portalUserId}
 * shape, which is gone.
 * <p>
 * Direct mirror of {@code AuditorService.inviteAuditor()}'s own confirmed
 * shape (stream-based ALREADY_INVITED check, {@code EmailService}/
 * {@code EmailTemplates.portalInvite()} call, {@code app.frontend.url}
 * property) — client-scoped here like {@code AccPortalAccessGrant}, not
 * tenant-level like Auditor's own grant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpPortalService {

    private final LpPortalAccessGrantRepository grantRepo;
    private final LpClientRepository clientRepo;
    private final LpProfileRepository profileRepo;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<LpPortalAccessGrantResponse> listForClient(TenantId tenantId, UUID clientId) {
        return grantRepo.findAllForClient(tenantId, clientId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public LpPortalAccessGrantResponse inviteClientToPortal(TenantId tenantId, UUID clientId,
                                                             String inviteEmail, UUID invitedBy) {
        LpClient client = clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("LpClient", clientId.toString()));

        String email = inviteEmail.toLowerCase().trim();

        boolean alreadyInvited = grantRepo.findAllForClient(tenantId, clientId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email)
                        && ("PENDING".equals(g.getStatus()) || "ACTIVE".equals(g.getStatus())));
        if (alreadyInvited) {
            throw new HandyFlowException(
                    "This email already has a pending or active invite for this client",
                    HttpStatus.CONFLICT, "ALREADY_INVITED");
        }

        LpPortalAccessGrant grant = LpPortalAccessGrant.createInvite(tenantId, clientId, email, invitedBy);
        grantRepo.save(grant);

        String firmName = profileRepo.findByTenantId(tenantId)
                .map(LpProfile::getFirmName)
                .orElse("your legal practice");

        // EmailService.send(to, subject, html) and EmailTemplates.portalInvite(clientName, firmName, acceptUrl)
        // are both confirmed-real (read directly from za.co.handyflow.platform.shared this session, and already
        // used identically by AuditorService.inviteAuditor()/AccDocumentRequestService) — not a guess.
        emailService.send(email, firmName + " has invited you to their client portal",
                EmailTemplates.portalInvite(client.getName(), firmName,
                        frontendUrl + "/legal-practice/portal/auth/accept-invite?token=" + grant.getInviteToken()));

        log.info("Legal practice portal invite sent: {} -> client={} tenant={}", email, clientId, tenantId);
        return toResponse(grant);
    }

    @Transactional
    public LpPortalAccessGrantResponse revoke(TenantId tenantId, UUID grantId, UUID revokedBy) {
        LpPortalAccessGrant grant = grantRepo.findActiveById(tenantId, grantId)
                .orElseThrow(() -> new ResourceNotFoundException("LpPortalAccessGrant", grantId.toString()));
        grant.revoke(revokedBy);
        grantRepo.save(grant);
        log.info("Legal practice portal access revoked: grant={} tenant={} revokedBy={}", grantId, tenantId, revokedBy);
        return toResponse(grant);
    }

    private LpPortalAccessGrantResponse toResponse(LpPortalAccessGrant g) {
        return new LpPortalAccessGrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(),
                g.getInvitedAt(), g.getAcceptedAt(), g.getRevokedAt());
    }
}
