// security/application/internal/GuardScreeningEvidenceService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Evidence files on a screening record (criminal clearance certificate, verification response, lab report...).
 * Bytes live in the shared evidence module; every call first proves the screening belongs to this tenant and
 * guard, and download/remove prove the file belongs to that screening, so one record's files cannot be reached
 * through another's URL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardScreeningEvidenceService {

    static final String SOURCE_MODULE = "security";
    static final String ENTITY_TYPE = "GuardScreeningRecord";

    private final GuardScreeningService screeningService;
    private final EvidenceFacade evidenceFacade;

    @Transactional
    public EvidenceResponse attach(TenantId tenantId, UUID guardId, UUID screeningId, MultipartFile file,
                                   String label, UUID by, String byName) {
        screeningService.findForGuard(tenantId, guardId, screeningId);
        if (file == null || file.isEmpty()) {
            throw new HandyFlowException("Choose a file to attach", HttpStatus.BAD_REQUEST, "MISSING_FILE");
        }
        String type = label == null || label.isBlank() ? "Screening evidence" : label.trim();
        EvidenceResponse saved = evidenceFacade.attach(tenantId, file, type, SOURCE_MODULE, ENTITY_TYPE,
                screeningId, null, by, byName);
        log.info("[Security] Screening evidence attached screeningId={} evidenceId={} by={}", screeningId, saved.id(), by);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> list(TenantId tenantId, UUID guardId, UUID screeningId) {
        screeningService.findForGuard(tenantId, guardId, screeningId);
        return evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, screeningId);
    }

    /** Files for a screening already known to belong to the caller's tenant (used when building the overview). */
    @Transactional(readOnly = true)
    public List<EvidenceResponse> filesFor(TenantId tenantId, UUID screeningId) {
        return evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, screeningId);
    }

    @Transactional(readOnly = true)
    public EvidenceFacade.DownloadedEvidence download(TenantId tenantId, UUID guardId, UUID screeningId, UUID evidenceId) {
        requireOwned(tenantId, guardId, screeningId, evidenceId);
        return evidenceFacade.download(tenantId, evidenceId);
    }

    @Transactional
    public void remove(TenantId tenantId, UUID guardId, UUID screeningId, UUID evidenceId) {
        requireOwned(tenantId, guardId, screeningId, evidenceId);
        evidenceFacade.detach(tenantId, evidenceId);
        log.warn("[Security] Screening evidence removed screeningId={} evidenceId={}", screeningId, evidenceId);
    }

    private void requireOwned(TenantId tenantId, UUID guardId, UUID screeningId, UUID evidenceId) {
        screeningService.findForGuard(tenantId, guardId, screeningId);
        boolean owned = evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, screeningId).stream()
                .anyMatch(e -> e.id().equals(evidenceId));
        if (!owned) throw new ResourceNotFoundException("Evidence", evidenceId.toString());
    }
}
