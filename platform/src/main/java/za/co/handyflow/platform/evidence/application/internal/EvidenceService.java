package za.co.handyflow.platform.evidence.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.domain.model.Evidence;
import za.co.handyflow.platform.evidence.domain.repository.EvidenceRepository;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Stage 0 of the Financial Control & Assurance adoption plan.
 * <p>
 * Built directly on the CONFIRMED-real FileStorageService pattern —
 * mirrors TasksService.uploadAttachment()/downloadAttachment() and
 * RecruitmentAgencyService.uploadCv()/downloadCv() exactly. Deliberately
 * does NOT follow AccFicaDocument's file_content_base64 approach — that
 * was a documented workaround for that feature's own history, not the
 * pattern a new shared module should copy.
 * <p>
 * UNVERIFIED: HandyFlowException's exact constructor signature is
 * assumed from its confirmed usage elsewhere tonight
 * (message, HttpStatus, errorCode) — not independently re-checked here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceService implements EvidenceFacade {

    private static final long MAX_EVIDENCE_BYTES = 20L * 1024 * 1024;

    private final EvidenceRepository evidenceRepo;
    private final FileStorageService fileStorageService;
    private final za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail;

    @Override
    @Transactional
    public EvidenceResponse attach(TenantId tenantId, MultipartFile file, String evidenceType,
                                   String sourceModule, String relatedEntityType, UUID relatedEntityId,
                                   UUID periodId, UUID uploadedBy, String uploadedByName) {
        if (EvidenceFacade.isRestricted(sourceModule)) {
            throw new HandyFlowException("That kind of evidence can only be attached from its own screen", HttpStatus.FORBIDDEN, "EVIDENCE_RESTRICTED");
        }
        return store(tenantId, file, evidenceType, sourceModule, relatedEntityType, relatedEntityId, periodId, uploadedBy, uploadedByName);
    }

    @Override
    @Transactional
    public EvidenceResponse attachRestricted(TenantId tenantId, MultipartFile file, String evidenceType,
                                             String sourceModule, String relatedEntityType, UUID relatedEntityId,
                                             UUID uploadedBy, String uploadedByName) {
        requireRestricted(sourceModule);
        return store(tenantId, file, evidenceType, sourceModule, relatedEntityType, relatedEntityId, null, uploadedBy, uploadedByName);
    }

    private EvidenceResponse store(TenantId tenantId, MultipartFile file, String evidenceType,
                                   String sourceModule, String relatedEntityType, UUID relatedEntityId,
                                   UUID periodId, UUID uploadedBy, String uploadedByName) {
        if (file == null || file.isEmpty()) {
            throw new HandyFlowException("A file is required", HttpStatus.BAD_REQUEST, "FILE_REQUIRED");
        }
        if (file.getSize() > MAX_EVIDENCE_BYTES) {
            throw new HandyFlowException(
                    "File is too large — maximum is " + (MAX_EVIDENCE_BYTES / (1024 * 1024)) + "MB",
                    HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded evidence file: {}", e.getMessage(), e);
            throw new HandyFlowException("Failed to read uploaded file", HttpStatus.INTERNAL_SERVER_ERROR, "READ_FAILED");
        }

        String hash = sha256Hex(content);
        String pathPrefix = "evidence/" + sourceModule + "/" + tenantId.getValue();

        String storageKey;
        try {
            storageKey = fileStorageService.store(pathPrefix, file.getOriginalFilename(), file.getContentType(), content);
        } catch (IOException e) {
            log.error("Failed to store evidence for {}={}: {}", relatedEntityType, relatedEntityId, e.getMessage(), e);
            throw new HandyFlowException("Failed to store file", HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED");
        }

        Evidence evidence = Evidence.attach(tenantId.getValue(),
                file.getOriginalFilename(), file.getContentType(), file.getSize(), storageKey,
                evidenceType, sourceModule, relatedEntityType, relatedEntityId, periodId,
                hash, uploadedBy, uploadedByName);
        evidenceRepo.save(evidence);
        // A restricted file's name can name a person, so the general audit trail carries only what proves the file.
        trail.record(tenantId, "EVIDENCE_ATTACHED", "EVIDENCE", evidence.getId(), null, EvidenceFacade.isRestricted(sourceModule)
                ? java.util.Map.of("sourceModule", sourceModule, "sha256", hash)
                : java.util.Map.of("fileName", String.valueOf(file.getOriginalFilename()), "sourceModule", sourceModule, "sha256", hash));

        log.info("Evidence attached: type={} source={} entity={}:{} tenant={}",
                evidenceType, sourceModule, relatedEntityType, relatedEntityId, tenantId.getValue());

        return toResponse(evidence);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponse> listFor(TenantId tenantId, String sourceModule,
                                          String relatedEntityType, UUID relatedEntityId) {
        if (EvidenceFacade.isRestricted(sourceModule)) return List.of();
        return evidenceRepo.findActiveForEntity(tenantId.getValue(), sourceModule, relatedEntityType, relatedEntityId)
                .stream()
                .map(p -> new EvidenceResponse(p.getId(), p.getFileName(), p.getContentType(), p.getFileSizeBytes(),
                        p.getEvidenceType(), p.getStatus(), p.getUploadedByName(), p.getCreatedAt()))
                .toList();
    }

    // NEW: Stage 3 — see EvidenceFacade's own Javadoc for why this exists.
    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponse> listAllForTenant(TenantId tenantId) {
        return evidenceRepo.findAllActiveForTenant(tenantId.getValue())
                .stream()
                .map(p -> new EvidenceResponse(p.getId(), p.getFileName(), p.getContentType(), p.getFileSizeBytes(),
                        p.getEvidenceType(), p.getStatus(), p.getUploadedByName(), p.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponse> listByIds(TenantId tenantId, java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return evidenceRepo.findAllActiveForTenant(tenantId.getValue()).stream()
                .filter(p -> ids.contains(p.getId()))
                .map(p -> new EvidenceResponse(p.getId(), p.getFileName(), p.getContentType(), p.getFileSizeBytes(),
                        p.getEvidenceType(), p.getStatus(), p.getUploadedByName(), p.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public DownloadedEvidence download(TenantId tenantId, UUID evidenceId) {
        Evidence evidence = evidenceRepo.findByTenantAndId(tenantId.getValue(), evidenceId)
                .filter(e -> !EvidenceFacade.isRestricted(e.getSourceModule()))
                .orElseThrow(() -> new ResourceNotFoundException("Evidence", evidenceId.toString()));
        return read(tenantId, evidence);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponse> listRestricted(TenantId tenantId, String sourceModule, String relatedEntityType, UUID relatedEntityId) {
        requireRestricted(sourceModule);
        return evidenceRepo.findActiveForEntity(tenantId.getValue(), sourceModule, relatedEntityType, relatedEntityId)
                .stream()
                .map(p -> new EvidenceResponse(p.getId(), p.getFileName(), p.getContentType(), p.getFileSizeBytes(),
                        p.getEvidenceType(), p.getStatus(), p.getUploadedByName(), p.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public DownloadedEvidence downloadRestricted(TenantId tenantId, String sourceModule, String relatedEntityType, UUID relatedEntityId, UUID evidenceId) {
        return read(tenantId, restricted(tenantId, sourceModule, relatedEntityType, relatedEntityId, evidenceId));
    }

    @Override
    @Transactional
    public void detachRestricted(TenantId tenantId, String sourceModule, String relatedEntityType, UUID relatedEntityId, UUID evidenceId) {
        Evidence evidence = restricted(tenantId, sourceModule, relatedEntityType, relatedEntityId, evidenceId);
        evidence.detach();
        evidenceRepo.save(evidence);
        trail.record(tenantId, "EVIDENCE_DETACHED", "EVIDENCE", evidenceId, java.util.Map.of("status", "ACTIVE"), java.util.Map.of("status", "DETACHED"));
    }

    private static void requireRestricted(String sourceModule) {
        if (!EvidenceFacade.isRestricted(sourceModule))
            throw new IllegalArgumentException("A restricted source module must start with '" + EvidenceFacade.RESTRICTED_PREFIX + "'.");
    }

    /** The active file, only if it is attached to exactly this entity of this restricted module. */
    private Evidence restricted(TenantId tenantId, String sourceModule, String relatedEntityType, UUID relatedEntityId, UUID evidenceId) {
        requireRestricted(sourceModule);
        return evidenceRepo.findByTenantAndId(tenantId.getValue(), evidenceId)
                .filter(e -> "ACTIVE".equals(e.getStatus()) && sourceModule.equals(e.getSourceModule())
                        && relatedEntityType.equals(e.getRelatedEntityType()) && relatedEntityId.equals(e.getRelatedEntityId()))
                .orElseThrow(() -> new ResourceNotFoundException("Evidence", evidenceId.toString()));
    }

    private DownloadedEvidence read(TenantId tenantId, Evidence evidence) {
        UUID evidenceId = evidence.getId();
        try {
            byte[] content = fileStorageService.retrieve(evidence.getStorageKey());
            trail.record(tenantId, "EVIDENCE_DOWNLOADED", "EVIDENCE", evidenceId, null, EvidenceFacade.isRestricted(evidence.getSourceModule())
                    ? java.util.Map.of("sourceModule", evidence.getSourceModule())
                    : java.util.Map.of("fileName", evidence.getFileName()));
            return new DownloadedEvidence(content, evidence.getFileName(), evidence.getContentType());
        } catch (IOException e) {
            log.error("Failed to retrieve evidence={}: {}", evidenceId, e.getMessage(), e);
            throw new HandyFlowException("Failed to retrieve file", HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED");
        }
    }

    @Override
    @Transactional
    public void detach(TenantId tenantId, UUID evidenceId) {
        Evidence evidence = evidenceRepo.findByTenantAndId(tenantId.getValue(), evidenceId)
                .filter(e -> !EvidenceFacade.isRestricted(e.getSourceModule()))
                .orElseThrow(() -> new ResourceNotFoundException("Evidence", evidenceId.toString()));
        // Soft-detach only — never calls fileStorageService.delete() here.
        // Evidence that's been cited (an approved expense, a sent invoice)
        // should never have its underlying file silently vanish just
        // because someone detached it from one record; the row staying
        // present with status=DETACHED preserves that.
        evidence.detach();
        evidenceRepo.save(evidence);
        trail.record(tenantId, "EVIDENCE_DETACHED", "EVIDENCE", evidenceId, java.util.Map.of("status", "ACTIVE"), java.util.Map.of("status", "DETACHED"));
        log.info("Evidence detached={} tenant={}", evidenceId, tenantId.getValue());
    }

    private EvidenceResponse toResponse(Evidence e) {
        return new EvidenceResponse(e.getId(), e.getFileName(), e.getContentType(), e.getFileSizeBytes(),
                e.getEvidenceType(), e.getStatus(), e.getUploadedByName(), e.getCreatedAt());
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every standard JVM — this
            // branch is unreachable in practice, not a real failure mode.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}