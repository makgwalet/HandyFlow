package za.co.handyflow.platform.auditor.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;
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

    public record AuditorTenantAccess(UUID tenantId, java.time.Instant acceptedAt) {}

    public List<AuditorTenantAccess> getMyTenants(UUID portalUserId) {
        return grantRepo.findByPortalUserId(portalUserId).stream()
                .filter(AuditorAccessGrant::isActive)
                .map(g -> new AuditorTenantAccess(g.getTenantId(), g.getAcceptedAt()))
                .toList();
    }

    public List<EvidenceResponse> getEvidence(UUID portalUserId, UUID tenantId) {
        requireActiveGrant(portalUserId, tenantId);
        return evidenceFacade.listAllForTenant(TenantId.of(tenantId));
    }

    public List<ControlExceptionResponse> getControlExceptions(UUID portalUserId, UUID tenantId) {
        requireActiveGrant(portalUserId, tenantId);
        return controlExceptionFacade.listAll(TenantId.of(tenantId));
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

    private void requireActiveGrant(UUID portalUserId, UUID tenantId) {
        boolean hasAccess = grantRepo.findByTenantId(tenantId).stream()
                .anyMatch(g -> portalUserId.equals(g.getPortalUserId()) && g.isActive());
        if (!hasAccess) {
            throw new HandyFlowException(
                    "You don't have access to this business's records", HttpStatus.FORBIDDEN, "NO_ACCESS");
        }
    }
}