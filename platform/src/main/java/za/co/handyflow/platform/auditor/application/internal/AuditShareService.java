package za.co.handyflow.platform.auditor.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.auditor.domain.model.AuditShare;
import za.co.handyflow.platform.auditor.domain.repository.AuditShareRepository;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Deciding what an external auditor may see. Evidence and control exceptions reach the auditor portal only through a live
 * share recorded here. Findings keep their own sharing columns for now (see backlog A3).
 */
@Service
@RequiredArgsConstructor
public class AuditShareService {

    private final AuditShareRepository repo;
    private final EvidenceFacade evidenceFacade;
    private final ControlExceptionFacade controlExceptionFacade;

    @Transactional
    public AuditShare share(TenantId tenantId, String itemType, UUID itemId, String reason, UUID by, String byName) {
        if (AuditShare.FINDING.equals(itemType)) {
            throw new HandyFlowException("Findings are shared from the finding itself", HttpStatus.BAD_REQUEST, "USE_FINDING_SHARE");
        }
        if (!AuditShare.EVIDENCE.equals(itemType) && !AuditShare.CONTROL_EXCEPTION.equals(itemType)) {
            throw new HandyFlowException("Unknown item type", HttpStatus.BAD_REQUEST, "BAD_ITEM_TYPE");
        }
        if (reason == null || reason.isBlank()) {
            throw new HandyFlowException("Say why this is being shared with the auditor", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }
        boolean exists = AuditShare.EVIDENCE.equals(itemType)
                ? !evidenceFacade.listByIds(tenantId, List.of(itemId)).isEmpty()
                : !controlExceptionFacade.listByIds(tenantId, List.of(itemId)).isEmpty();
        if (!exists) throw new ResourceNotFoundException(itemType, itemId.toString());
        if (repo.findLiveFor(tenantId.getValue(), itemType, itemId).isPresent()) {
            throw new HandyFlowException("This item is already shared with auditors", HttpStatus.CONFLICT, "ALREADY_SHARED");
        }
        return repo.save(AuditShare.share(tenantId.getValue(), itemType, itemId, reason, by, byName));
    }

    @Transactional
    public AuditShare withdraw(TenantId tenantId, UUID shareId, String why, UUID by, String byName) {
        AuditShare s = repo.findByTenantIdAndId(tenantId.getValue(), shareId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditShare", shareId.toString()));
        try {
            s.withdraw(by, byName, why);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "ALREADY_WITHDRAWN");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        }
        return repo.save(s);
    }

    @Transactional(readOnly = true)
    public List<AuditShare> list(TenantId tenantId) {
        return repo.findAllForTenant(tenantId.getValue());
    }
}
