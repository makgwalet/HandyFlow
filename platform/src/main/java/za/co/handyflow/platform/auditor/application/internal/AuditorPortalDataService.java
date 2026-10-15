package za.co.handyflow.platform.auditor.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;
import za.co.handyflow.platform.auditor.domain.model.AuditShare;
import za.co.handyflow.platform.auditor.domain.repository.AuditShareRepository;
import za.co.handyflow.platform.auditor.domain.repository.AuditorAccessGrantRepository;
import za.co.handyflow.platform.auditor.dto.SharedFindingResponse;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.controls.dto.ControlExceptionResponse;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFinding;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditorPortalDataService {

    private final AuditorAccessGrantRepository grantRepo;
    private final EvidenceFacade evidenceFacade;
    private final ControlExceptionFacade controlExceptionFacade;
    // FIX: closes the confirmed "no internal-audit engine reachable by
    // an external auditor" gap.
    private final AuditFindingRepository findingRepo;
    private final AuditShareRepository shareRepo;
    private final za.co.handyflow.platform.auditor.domain.repository.ExternalAuditEngagementRepository engagementRepo;
    private final za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail;

    public record AuditorTenantAccess(UUID tenantId, java.time.Instant acceptedAt) {}

    public List<AuditorTenantAccess> getMyTenants(UUID portalUserId) {
        return grantRepo.findByPortalUserId(portalUserId).stream()
                .filter(this::usable)
                .map(g -> new AuditorTenantAccess(g.getTenantId(), g.getAcceptedAt()))
                .toList();
    }

    public List<EvidenceResponse> getEvidence(UUID portalUserId, UUID tenantId) {
        requireActiveGrant(portalUserId, tenantId);
        trail.recordAs(TenantId.of(tenantId), "AUDITOR", portalUserId, null, "AUDITOR_VIEWED_EVIDENCE", "TENANT", tenantId, null, null);
        return evidenceFacade.listByIds(TenantId.of(tenantId), sharedIds(tenantId, AuditShare.EVIDENCE));
    }

    public List<ControlExceptionResponse> getControlExceptions(UUID portalUserId, UUID tenantId) {
        requireActiveGrant(portalUserId, tenantId);
        trail.recordAs(TenantId.of(tenantId), "AUDITOR", portalUserId, null, "AUDITOR_VIEWED_EXCEPTIONS", "TENANT", tenantId, null, null);
        return controlExceptionFacade.listByIds(TenantId.of(tenantId), sharedIds(tenantId, AuditShare.CONTROL_EXCEPTION));
    }

    // FIX: closes the confirmed "no internal-audit engine reachable by
    // an external auditor" gap. findingRepo.findSharedForTenant()
    // already filters to externalVisibility = 'SHARED' at the query
    // level — this method's own job is just the access-grant check and
    // mapping to the curated, narrower SharedFindingResponse shape (no
    // owner/createdBy/sourceExceptionId — see that record's own
    // comment for why).
    public List<SharedFindingResponse> getSharedFindings(UUID portalUserId, UUID tenantId) {
        requireActiveGrant(portalUserId, tenantId);
        trail.recordAs(TenantId.of(tenantId), "AUDITOR", portalUserId, null, "AUDITOR_VIEWED_FINDINGS", "TENANT", tenantId, null, null);
        return findingRepo.findSharedForTenant(tenantId).stream()
                .map(this::toSharedFindingResponse)
                .toList();
    }

    private SharedFindingResponse toSharedFindingResponse(AuditFinding f) {
        return new SharedFindingResponse(f.getId(), f.getTitle(), f.getDescription(), f.getRootCause(),
                f.getRecommendation(), f.getManagementResponse(), f.getSeverity(), f.getStatus(), f.getDueDate(),
                f.getClosedAt(), f.getPreviouslyClosedAt(), f.getReopenedAt(), f.getReopenReason(),
                f.getSharedAt(), f.getSharingReason());
    }

    private java.util.Set<UUID> sharedIds(UUID tenantId, String itemType) {
        return shareRepo.findLive(tenantId, itemType).stream().map(AuditShare::getItemId).collect(java.util.stream.Collectors.toSet());
    }

    /** A grant works if it is live and not past its date, and, when it belongs to an engagement, that engagement is running. */
    private boolean usable(za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant g) {
        if (!g.isUsableAt(java.time.Instant.now())) return false;
        if (g.getEngagementId() == null) return true;
        return engagementRepo.findById(g.getEngagementId()).map(e -> e.grantsAccess()).orElse(false);
    }

    /** The auditor's usable grant for this business, or a refusal. Callers that need the engagement read it from the grant. */
    public za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant requireGrant(UUID portalUserId, UUID tenantId) {
        return grantRepo.findByTenantId(tenantId).stream()
                .filter(g -> portalUserId.equals(g.getPortalUserId()) && usable(g))
                .findFirst()
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this business's records (it may have expired)", HttpStatus.FORBIDDEN, "NO_ACCESS"));
    }

    private void requireActiveGrant(UUID portalUserId, UUID tenantId) {
        boolean hasAccess = grantRepo.findByTenantId(tenantId).stream()
                .anyMatch(g -> portalUserId.equals(g.getPortalUserId()) && usable(g));
        if (!hasAccess) {
            throw new HandyFlowException(
                    "You don't have access to this business's records (it may have expired)", HttpStatus.FORBIDDEN, "NO_ACCESS");
        }
    }
}