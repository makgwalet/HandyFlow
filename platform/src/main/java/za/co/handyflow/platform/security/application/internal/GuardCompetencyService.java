// security/application/internal/GuardCompetencyService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.domain.model.GuardCompetency;
import za.co.handyflow.platform.security.domain.repository.GuardCompetencyRepository;
import za.co.handyflow.platform.security.dto.GuardCompetencyResponse;
import za.co.handyflow.platform.security.dto.GuardOverviewResponse.EvidenceItem;
import za.co.handyflow.platform.security.dto.SaveGuardCompetencyRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Skills and certifications on a guard's file. A competency is "met" when it is in date, has a certificate
 * attached and has been verified (see GuardReadinessCalculator.competency); only those marked required count
 * towards deployment readiness. Evidence files go through the shared evidence module and every file call
 * proves the file belongs to that competency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardCompetencyService {

    static final String SOURCE_MODULE = "security";
    static final String ENTITY_TYPE = "GuardCompetency";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final GuardCompetencyRepository repository;
    private final GuardService guardService;
    private final EvidenceFacade evidenceFacade;

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GuardCompetencyResponse> list(TenantId tenantId, UUID guardId) {
        guardService.getGuard(tenantId, guardId);
        return listForGuard(tenantId, guardId, LocalDate.now(SAST));
    }

    /** For a guard already known to belong to the tenant (the overview has just loaded it). */
    @Transactional(readOnly = true)
    public List<GuardCompetencyResponse> listForGuard(TenantId tenantId, UUID guardId, LocalDate today) {
        return repository.findActiveForGuard(tenantId, guardId).stream().map(c -> toResponse(c, today)).toList();
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public GuardCompetencyResponse create(TenantId tenantId, UUID guardId, SaveGuardCompetencyRequest req, UUID by) {
        return create(tenantId, guardId, req, by, LocalDate.now(SAST));
    }

    @Transactional
    GuardCompetencyResponse create(TenantId tenantId, UUID guardId, SaveGuardCompetencyRequest req, UUID by, LocalDate today) {
        guardService.getGuard(tenantId, guardId);
        var type = parseAndCheck(req, today);
        GuardCompetency c;
        try {
            c = GuardCompetency.create(tenantId, guardId, type, req.title(), req.issuedBy(), req.issueDate(),
                    req.expiryDate(), req.certificateRef(), req.required(), req.notes(), by);
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage(), "INVALID_COMPETENCY");
        }
        repository.save(c);
        log.info("[Security] Competency added guardId={} type={} by={}", guardId, type, by);
        return toResponse(c, today);
    }

    @Transactional
    public GuardCompetencyResponse update(TenantId tenantId, UUID guardId, UUID id, SaveGuardCompetencyRequest req) {
        LocalDate today = LocalDate.now(SAST);
        GuardCompetency c = find(tenantId, guardId, id);
        var type = parseAndCheck(req, today);
        try {
            c.update(type, req.title(), req.issuedBy(), req.issueDate(), req.expiryDate(), req.certificateRef(), req.required(), req.notes());
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage(), "INVALID_COMPETENCY");
        }
        repository.save(c);
        return toResponse(c, today);
    }

    /** Verification needs a certificate on file: there is nothing to verify otherwise. */
    @Transactional
    public GuardCompetencyResponse verify(TenantId tenantId, UUID guardId, UUID id, String note, UUID by, String byName) {
        GuardCompetency c = find(tenantId, guardId, id);
        if (evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, id).isEmpty()) {
            throw bad("Attach the certificate before verifying", "EVIDENCE_REQUIRED");
        }
        c.verify(by, byName, note);
        repository.save(c);
        log.info("[Security] Competency verified id={} by={}", id, by);
        return toResponse(c, LocalDate.now(SAST));
    }

    @Transactional
    public void remove(TenantId tenantId, UUID guardId, UUID id, UUID by) {
        GuardCompetency c = find(tenantId, guardId, id);
        c.softDelete(by);
        repository.save(c);
        log.warn("[Security] Competency removed id={} by={}", id, by);
    }

    // ── Evidence ──────────────────────────────────────────────────────────────

    @Transactional
    public EvidenceResponse attach(TenantId tenantId, UUID guardId, UUID id, MultipartFile file, String label, UUID by, String byName) {
        GuardCompetency c = find(tenantId, guardId, id);
        if (file == null || file.isEmpty()) throw bad("Choose a file to attach", "MISSING_FILE");
        String type = label == null || label.isBlank() ? "Certificate" : label.trim();
        EvidenceResponse saved = evidenceFacade.attach(tenantId, file, type, SOURCE_MODULE, ENTITY_TYPE, id, null, by, byName);
        // New evidence changes what the verifier saw only if they had already signed off an empty file set,
        // which verify() forbids, so the verification stands.
        log.info("[Security] Competency evidence attached id={} evidenceId={} type={}", id, saved.id(), c.getType());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> files(TenantId tenantId, UUID guardId, UUID id) {
        find(tenantId, guardId, id);
        return evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, id);
    }

    @Transactional(readOnly = true)
    public EvidenceFacade.DownloadedEvidence download(TenantId tenantId, UUID guardId, UUID id, UUID evidenceId) {
        requireOwned(tenantId, guardId, id, evidenceId);
        return evidenceFacade.download(tenantId, evidenceId);
    }

    /** Removing a certificate removes the verification too: what was verified is no longer on file. */
    @Transactional
    public void removeFile(TenantId tenantId, UUID guardId, UUID id, UUID evidenceId) {
        requireOwned(tenantId, guardId, id, evidenceId);
        evidenceFacade.detach(tenantId, evidenceId);
        GuardCompetency c = find(tenantId, guardId, id);
        if (evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, id).isEmpty() && c.isVerified()) {
            c.update(c.getType(), c.getTitle(), c.getIssuedBy(), c.getIssueDate(), c.getExpiryDate(),
                    c.getCertificateRef(), c.isRequired(), c.getNotes());
            repository.save(c);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GuardCompetency find(TenantId tenantId, UUID guardId, UUID id) {
        return repository.findActiveForGuardById(tenantId, guardId, id)
                .orElseThrow(() -> new ResourceNotFoundException("GuardCompetency", id.toString()));
    }

    private void requireOwned(TenantId tenantId, UUID guardId, UUID id, UUID evidenceId) {
        find(tenantId, guardId, id);
        boolean owned = evidenceFacade.listFor(tenantId, SOURCE_MODULE, ENTITY_TYPE, id).stream()
                .anyMatch(e -> e.id().equals(evidenceId));
        if (!owned) throw new ResourceNotFoundException("Evidence", evidenceId.toString());
    }

    private GuardCompetency.Type parseAndCheck(SaveGuardCompetencyRequest req, LocalDate today) {
        GuardCompetency.Type type;
        try {
            type = GuardCompetency.Type.valueOf(req.competencyType() == null ? "" : req.competencyType().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw bad("Unknown competency type: " + req.competencyType(), "INVALID_COMPETENCY_TYPE");
        }
        if (type == GuardCompetency.Type.OTHER && (req.title() == null || req.title().isBlank())) {
            throw bad("Give the competency a name", "TITLE_REQUIRED");
        }
        if (req.issueDate() != null && req.issueDate().isAfter(today)) {
            throw bad("The issue date cannot be in the future", "INVALID_ISSUE_DATE");
        }
        return type;
    }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
    }

    private GuardCompetencyResponse toResponse(GuardCompetency c, LocalDate today) {
        List<EvidenceItem> files = evidenceFacade.listFor(c.getTenantId(), SOURCE_MODULE, ENTITY_TYPE, c.getId()).stream()
                .map(e -> new EvidenceItem(e.id(), e.fileName(), e.evidenceType(), e.fileSizeBytes(), e.uploadedByName(), e.createdAt()))
                .toList();
        var item = GuardReadinessCalculator.competency(facts(c, files.size()), today);
        return new GuardCompetencyResponse(c.getId(), c.getGuardId(), c.getType().name(),
                GuardReadinessCalculator.labelOfCompetency(c.getType().name()), c.getTitle(), c.getIssuedBy(),
                c.getIssueDate(), c.getExpiryDate(), c.getCertificateRef(), c.isRequired(), c.getNotes(),
                item.state().name(), item.detail(), c.getVerifiedByName(), c.getVerifiedAt(), c.getVerificationNote(),
                files, c.getCreatedAt());
    }

    static GuardReadinessCalculator.CompetencyFacts facts(GuardCompetency c, int evidenceCount) {
        return new GuardReadinessCalculator.CompetencyFacts(c.getId().toString(), c.getType().name(), c.getTitle(),
                c.isRequired(), c.getExpiryDate(), evidenceCount, c.isVerified());
    }
}
