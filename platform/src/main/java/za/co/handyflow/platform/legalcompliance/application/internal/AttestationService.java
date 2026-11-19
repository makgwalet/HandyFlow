package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.AttestationCampaignRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.AttestationRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.PolicyRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.PolicyVersionRepository;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.GovernanceDtos.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Attestation campaigns. Launching snapshots the audience (active users, optionally one department) into one attestation row each; people who join
 * later are not added. A person answers only their own row, only while the campaign is open, and may change their answer until it closes.
 * Each answer is stored with the campaign's content hash and written to the event log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttestationService {

    static final String CAMPAIGN = "ATTESTATION_CAMPAIGN";
    static final String ATTESTATION = "ATTESTATION";

    private final AttestationCampaignRepository campaigns;
    private final AttestationRepository attestations;
    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final UserDirectory directory;
    private final NotificationService notificationService;
    private final RegulatoryAuditService trail;

    @Transactional
    public CampaignResponse launch(TenantId tenantId, CampaignRequest req, UUID userId, String userName) {
        PolicyVersion v = versions.findForTenant(tenantId, req.versionId()).orElseThrow(() -> new ResourceNotFoundException("Policy version", req.versionId().toString()));
        Policy p = policies.findForTenant(tenantId, v.getPolicyId()).orElseThrow(() -> new ResourceNotFoundException("Policy", v.getPolicyId().toString()));
        if (!p.isActive()) throw new HandyFlowException("The policy is retired", HttpStatus.CONFLICT, "INVALID_STATE");
        if (campaigns.findOpenForVersion(tenantId, v.getId()).isPresent())
            throw new HandyFlowException("There is already an open campaign for this version", HttpStatus.CONFLICT, "CAMPAIGN_OPEN");
        AttestationCampaign c = guarded(() -> AttestationCampaign.launch(tenantId, String.format("ATT-%05d", campaigns.nextRef()), v, req.title(), req.instructions(), req.dueDate(),
                req.department(), LocalDate.now(), userId, userName));
        List<UserDirectory.Person> audience = directory.activeUsers(tenantId, c.getDepartmentFilter());
        if (audience.isEmpty()) throw new HandyFlowException("No active users match this audience", HttpStatus.BAD_REQUEST, "EMPTY_AUDIENCE");
        campaigns.save(c);
        attestations.saveAll(audience.stream().map(a -> Attestation.assign(tenantId, c.getId(), a.id(), a.name(), a.email())).toList());
        trail.record(tenantId.getValue(), CAMPAIGN, c.getId(), "LAUNCHED", c.getReference() + " for " + p.getReference() + " v" + v.getVersionNumber() + " to " + audience.size() + " user(s), due " + c.getDueDate(), userId, userName);
        notifyLaunch(tenantId, c, p, audience);
        return view(tenantId, c, p, v, attestations.findByCampaign(tenantId, c.getId()));
    }

    @Transactional
    public CampaignResponse close(TenantId tenantId, UUID id, ReasonRequest req, UUID userId, String userName) {
        AttestationCampaign c = load(tenantId, id);
        guarded(() -> { c.close(userName, req.reason()); return null; });
        campaigns.save(c);
        trail.record(tenantId.getValue(), CAMPAIGN, id, "CLOSED", req.reason().trim(), userId, userName);
        return view(tenantId, c);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> list(TenantId tenantId, CampaignStatus status) { return campaigns.search(tenantId, status).stream().map(c -> view(tenantId, c)).toList(); }

    @Transactional(readOnly = true)
    public CampaignDetailResponse detail(TenantId tenantId, UUID id) {
        AttestationCampaign c = load(tenantId, id);
        List<Attestation> rows = attestations.findByCampaign(tenantId, id);
        return new CampaignDetailResponse(view(tenantId, c, rows), rows.stream().map(AttestationRow::of).toList());
    }

    @Transactional(readOnly = true)
    public List<MyAttestation> mine(TenantId tenantId, UUID userId) {
        LocalDate today = LocalDate.now();
        return attestations.findForUser(tenantId, userId).stream().map(a -> {
            AttestationCampaign c = load(tenantId, a.getCampaignId());
            PolicyVersion v = versions.findForTenant(tenantId, c.getPolicyVersionId()).orElseThrow(() -> new ResourceNotFoundException("Policy version", c.getPolicyVersionId().toString()));
            String policyTitle = policies.findForTenant(tenantId, c.getPolicyId()).map(Policy::getTitle).orElse("");
            return new MyAttestation(a.getId(), c.getId(), c.getTitle(), c.getInstructions(), policyTitle, v.getVersionNumber(), v.getContent(), c.getDueDate(),
                    a.isPending() && c.isOverdue(today), c.isOpen(), a.getStatus(), a.getRespondedAt(), a.getComment());
        }).toList();
    }

    @Transactional
    public MyAttestation respond(TenantId tenantId, UUID attestationId, RespondRequest req, UUID userId, String userName) {
        Attestation a = attestations.findForTenant(tenantId, attestationId).orElseThrow(() -> new ResourceNotFoundException("Attestation", attestationId.toString()));
        if (userId == null || !userId.equals(a.getUserId())) throw new ResourceNotFoundException("Attestation", attestationId.toString());
        AttestationCampaign c = load(tenantId, a.getCampaignId());
        if (!c.isOpen()) throw new HandyFlowException("This campaign is closed", HttpStatus.CONFLICT, "CAMPAIGN_CLOSED");
        guarded(() -> { a.respond(req.accept(), req.comment(), c.getContentHash()); return null; });
        attestations.save(a);
        trail.record(tenantId.getValue(), ATTESTATION, a.getId(), req.accept() ? "ACCEPTED" : "DECLINED",
                c.getReference() + " hash " + c.getContentHash().substring(0, 12) + (req.comment() == null || req.comment().isBlank() ? "" : ": " + req.comment().trim()), userId, userName);
        return mine(tenantId, userId).stream().filter(m -> m.id().equals(attestationId)).findFirst().orElseThrow();
    }

    // ── internals ──

    AttestationCampaign load(TenantId tenantId, UUID id) { return campaigns.findForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Attestation campaign", id.toString())); }

    private CampaignResponse view(TenantId tenantId, AttestationCampaign c) { return view(tenantId, c, attestations.findByCampaign(tenantId, c.getId())); }

    private CampaignResponse view(TenantId tenantId, AttestationCampaign c, List<Attestation> rows) {
        Policy p = policies.findForTenant(tenantId, c.getPolicyId()).orElse(null);
        PolicyVersion v = versions.findForTenant(tenantId, c.getPolicyVersionId()).orElse(null);
        return view(tenantId, c, p, v, rows);
    }

    private CampaignResponse view(TenantId tenantId, AttestationCampaign c, Policy p, PolicyVersion v, List<Attestation> rows) {
        int accepted = (int) rows.stream().filter(a -> a.getStatus() == AttestationStatus.ACCEPTED).count();
        int declined = (int) rows.stream().filter(a -> a.getStatus() == AttestationStatus.DECLINED).count();
        int pending = rows.size() - accepted - declined;
        return new CampaignResponse(c.getId(), c.getReference(), c.getPolicyId(), p == null ? null : p.getTitle(), v == null ? 0 : v.getVersionNumber(), c.getTitle(), c.getInstructions(),
                c.getDueDate(), c.isOverdue(LocalDate.now()), c.getDepartmentFilter(), c.getStatus(), c.getLaunchedByName(), c.getClosedAt(), c.getCloseReason(), rows.size(), accepted,
                declined, pending, GovernanceRules.percent(accepted + declined, rows.size()));
    }

    private void notifyLaunch(TenantId tenantId, AttestationCampaign c, Policy p, List<UserDirectory.Person> audience) {
        try {
            List<Recipient> recipients = audience.stream().map(a -> Recipient.user(a.id(), a.name(), a.email(), null)).toList();
            notificationService.send(NotificationRequest.builder().tenantId(tenantId).type(NotificationType.LEGALCOMPLIANCE_ATTESTATION_REMINDER)
                    .title("Please read and accept: " + p.getTitle()).message(c.getTitle() + " — due " + c.getDueDate()).actionUrl("/legalcompliance/policies")
                    .sourceModule("legalcompliance").recipients(recipients).build());
        } catch (Exception e) {
            log.warn("[Legal/Compliance] Could not send launch notifications for {}: {}", c.getReference(), e.getMessage());
        }
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
