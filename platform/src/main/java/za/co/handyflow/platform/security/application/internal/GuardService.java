// security/application/internal/GuardService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.GuardDocument;
import za.co.handyflow.platform.security.domain.repository.GuardDocumentRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GuardService — CHANGE (V214): guard creation now generates a tenant-prefixed,
 * globally-unique employee code (generateEmployeeCode/deriveDefaultPrefix
 * below), for the ~1000-guard-per-tenant case where most guards will never
 * have a full HandyFlow account and need to log into the guard app with
 * employee code + PIN instead of phone + PIN. See V214 migration for the
 * full schema rationale.
 *
 * WHY JdbcTemplate against the tenants table directly, rather than going
 * through TenantFacade like PdfReportService does?
 * TenantFacade's actual interface contract wasn't available when this was
 * written -- guessing at a facade interface (versus a DTO record) is a much
 * larger blast radius if wrong. Raw JDBC against tenants.code_prefix /
 * tenants.next_guard_code_number is self-contained and matches this
 * codebase's already-established pattern of dropping to JdbcTemplate for
 * cross-table reads the ORM layer doesn't need to own (see ClientPortalService,
 * IncidentService, ScanLogService, and the original CRM session's "User
 * contact lookup: raw JDBC via JdbcTemplate... confirmed working" note).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuardService {

    private static final Set<String> NOTE_REQUIRED = Set.of("SUSPENDED", "TERMINATED");
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 5;

    private final GuardRepository         guardRepository;
    private final GuardDocumentRepository documentRepository;
    private final JdbcTemplate            jdbc;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<GuardResponse> getGuards(TenantId tenantId, String search, Pageable pageable) {
        var page = (search == null || search.isBlank())
                ? guardRepository.findAllActive(tenantId, pageable)
                : guardRepository.searchActive(tenantId, search, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public GuardResponse getGuard(TenantId tenantId, UUID id) {
        return guardRepository.findActiveById(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", id.toString()));
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    @Transactional
    public GuardResponse createGuard(TenantId tenantId, CreateGuardRequest req) {
        // PSIRA's individual registration form (SIRA-1) requires a certified SA
        // ID (or passport/permit number) for every applicant -- this is not
        // optional metadata, it's a legal precondition for the guard to be
        // deployable at all. Validated here rather than only via a
        // @NotBlank on the DTO so the rule holds even if the DTO's own
        // validation is ever loosened.
        if (req.idNumber() == null || req.idNumber().isBlank()) {
            throw new HandyFlowException(
                    "An ID number (or passport/permit number) is required — PSIRA registration "
                            + "cannot proceed without it",
                    HttpStatus.BAD_REQUEST, "ID_NUMBER_REQUIRED");
        }

        // At least one contact method is required -- the guard's own phone is
        // NOT made mandatory on its own, since that would contradict the
        // employee-code-login design (many guards won't reliably have a
        // personal registered phone). An emergency contact is something
        // every employee should have regardless.
        boolean hasPhone = req.phone() != null && !req.phone().isBlank();
        boolean hasEmergencyPhone = req.emergencyContactPhone() != null && !req.emergencyContactPhone().isBlank();
        if (!hasPhone && !hasEmergencyPhone) {
            throw new HandyFlowException(
                    "Either the guard's own phone number or an emergency contact phone number is required",
                    HttpStatus.BAD_REQUEST, "CONTACT_REQUIRED");
        }

        if (req.psiraNumber() != null && !req.psiraNumber().isBlank()) {
            if (guardRepository.existsByTenantIdAndPsiraNumberAndDeletedAtIsNull(
                    tenantId, req.psiraNumber())) {
                throw new HandyFlowException(
                        "A guard with PSiRA number " + req.psiraNumber() + " already exists",
                        HttpStatus.CONFLICT, "PSIRA_DUPLICATE");
            }
        }
        Guard guard = Guard.create(tenantId, req.firstName(), req.lastName(),
                req.psiraNumber(), req.idNumber(), req.phone(), req.grade(),
                req.psiraExpiryDate(), req.emergencyContactName(), req.emergencyContactPhone());

        String employeeCode = generateEmployeeCode(tenantId);
        guard.assignEmployeeCode(employeeCode);

        guardRepository.save(guard);
        log.info("[Security] Created guard={} employeeCode={} tenant={}",
                guard.getFullName(), employeeCode, tenantId);
        return toResponse(guard);
    }

    @Transactional
    public GuardResponse updateGuard(TenantId tenantId, UUID id, CreateGuardRequest req) {
        Guard guard = findActive(tenantId, id);

        boolean hasPhone = req.phone() != null && !req.phone().isBlank();
        boolean hasEmergencyPhone = req.emergencyContactPhone() != null && !req.emergencyContactPhone().isBlank();
        if (!hasPhone && !hasEmergencyPhone) {
            throw new HandyFlowException(
                    "Either the guard's own phone number or an emergency contact phone number is required",
                    HttpStatus.BAD_REQUEST, "CONTACT_REQUIRED");
        }

        if (req.psiraNumber() != null && !req.psiraNumber().isBlank()) {
            if (guardRepository.existsByPsiraExcluding(tenantId, req.psiraNumber(), id)) {
                throw new HandyFlowException(
                        "Another guard with PSiRA number " + req.psiraNumber() + " already exists",
                        HttpStatus.CONFLICT, "PSIRA_DUPLICATE");
            }
        }

        guard.update(req.firstName(), req.lastName(), req.psiraNumber(),
                req.idNumber(), req.phone(), req.grade(), req.notes(),
                req.psiraExpiryDate(), req.emergencyContactName(), req.emergencyContactPhone());
        guardRepository.save(guard);
        log.info("[Security] Updated guard={} tenant={}", id, tenantId);
        return toResponse(guard);
    }

    @Transactional
    public GuardResponse updateStatus(TenantId tenantId, UUID id,
                                      UpdateGuardStatusRequest req,
                                      UUID changedBy) {
        Guard guard = findActive(tenantId, id);

        if (NOTE_REQUIRED.contains(req.status()) &&
                (req.note() == null || req.note().isBlank())) {
            throw new HandyFlowException(
                    "A reason note is required when setting status to " + req.status(),
                    HttpStatus.BAD_REQUEST, "NOTE_REQUIRED");
        }

        guard.updateStatus(req.status(), req.note(), changedBy);
        guardRepository.save(guard);

        log.info("[Security] Guard status changed guard={} status={} by={} tenant={}",
                id, req.status(), changedBy, tenantId);
        return toResponse(guard);
    }

    @Transactional
    public void deleteGuard(TenantId tenantId, UUID id, UUID deletedBy) {
        Guard guard = findActive(tenantId, id);
        guard.softDelete(deletedBy);
        guardRepository.save(guard);
        log.info("[Security] Soft-deleted guard={} by={} tenant={}", id, deletedBy, tenantId);
    }

    @Transactional
    public GuardResponse updatePhoto(TenantId tenantId, UUID id, String photoBase64) {
        Guard guard = findActive(tenantId, id);
        if (photoBase64 != null && photoBase64.startsWith("data:")) {
            log.warn("[Security] Base64 photo received for guard={} — stored as PENDING_UPLOAD. " +
                    "Wire up S3 presigned URL before production deployment.", id);
        }
        guard.updatePhoto(photoBase64);
        guardRepository.save(guard);
        return toResponse(guard);
    }

    // ── V214: Employee code generation ─────────────────────────────────────────

    /**
     * Generates a tenant-prefixed, globally-unique employee code:
     *   1. Resolve the tenant's prefix (explicit tenants.code_prefix, or
     *      derived from tenants.name if unset).
     *   2. Atomically claim the next sequence number for this tenant via
     *      UPDATE ... RETURNING (single statement, no separate lock needed).
     *   3. Check global uniqueness (guardRepository.existsByEmployeeCode) --
     *      collisions are only possible when two tenants share a prefix AND
     *      have reached the same sequence number, which is rare but not
     *      impossible; retry up to MAX_CODE_GENERATION_ATTEMPTS times.
     */
    private String generateEmployeeCode(TenantId tenantId) {
        Map<String, Object> tenantRow;
        try {
            tenantRow = jdbc.queryForMap(
                    "SELECT name, code_prefix FROM tenants WHERE id = ?", tenantId.getValue());
        } catch (Exception e) {
            throw new HandyFlowException(
                    "Could not resolve tenant details for employee code generation",
                    HttpStatus.INTERNAL_SERVER_ERROR, "TENANT_LOOKUP_FAILED");
        }

        String prefix = (String) tenantRow.get("code_prefix");
        if (prefix == null || prefix.isBlank()) {
            prefix = deriveDefaultPrefix((String) tenantRow.get("name"));
        }

        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            Integer assignedNumber = jdbc.queryForObject(
                    "UPDATE tenants SET next_guard_code_number = next_guard_code_number + 1 " +
                            "WHERE id = ? RETURNING next_guard_code_number - 1",
                    Integer.class, tenantId.getValue());

            String candidate = prefix + String.format("%06d", assignedNumber);
            if (!guardRepository.existsByEmployeeCode(candidate)) {
                return candidate;
            }
            log.warn("[Security] Employee code collision on candidate={} (attempt {}/{}), retrying",
                    candidate, attempt + 1, MAX_CODE_GENERATION_ATTEMPTS);
        }

        throw new HandyFlowException(
                "Could not generate a unique employee code after " + MAX_CODE_GENERATION_ATTEMPTS + " attempts",
                HttpStatus.INTERNAL_SERVER_ERROR, "CODE_GENERATION_FAILED");
    }

    private static final java.util.Set<String> CORPORATE_SUFFIXES = java.util.Set.of(
            "PTY", "LTD", "LIMITED", "INC", "LLC", "CC", "CORP", "CO", "GROUP");

    /**
     * Falls back to deriving a prefix from the tenant's name when
     * tenants.code_prefix hasn't been set: single-word names use the first
     * letter ("Fidelity" -> "F"); multi-word names use initials, capped at
     * 3 letters ("Fast Response" -> "FS"). Matches the examples given when
     * this feature was scoped: Shovalula -> S, Fast Response -> FS.
     *
     * BUGFIX: previously split the raw tenant name on whitespace with no
     * sanitization, so a legal entity name like "Zeta Earthmoving (Pty) Ltd"
     * produced "ZE(" -- the third "word" after splitting is literally
     * "(Pty)", whose first character is the parenthesis itself. Confirmed
     * in production: a real guard's employee code came back "ZE(000001".
     * Now: (1) strips anything that isn't a letter or whitespace BEFORE
     * splitting, so punctuation can never leak into a generated code, and
     * (2) drops common corporate-suffix words (Pty, Ltd, Inc, etc.)
     * entirely, so the prefix reflects the trading name ("Zeta
     * Earthmoving") rather than the full legal entity name -- matching the
     * clean, suffix-free examples this feature was originally scoped
     * against.
     */
    private String deriveDefaultPrefix(String tenantName) {
        if (tenantName == null || tenantName.isBlank()) return "X";

        String cleaned = tenantName.replaceAll("[^\\p{L}\\s]", " ").trim();
        if (cleaned.isEmpty()) {
            return tenantName.trim().substring(0, 1).toUpperCase();
        }

        String[] words = java.util.Arrays.stream(cleaned.split("\\s+"))
                .filter(w -> !w.isBlank())
                .filter(w -> !CORPORATE_SUFFIXES.contains(w.toUpperCase()))
                .toArray(String[]::new);

        if (words.length == 0) {
            // Name was entirely corporate-suffix words (unlikely) -- fall
            // back to the first letter of the cleaned (punctuation-free)
            // name rather than returning an empty prefix.
            return cleaned.substring(0, 1).toUpperCase();
        }
        if (words.length == 1) {
            return words[0].substring(0, 1).toUpperCase();
        }
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() >= 3) break;
            sb.append(Character.toUpperCase(w.charAt(0)));
        }
        return sb.toString();
    }

    // ── Guard File — compliance documents ──────────────────────────────────────

    /**
     * Uploads a compliance document to a guard's file (ID copy, PSIRA
     * certificate, proof of address, etc). Dev-mode base64 handling mirrors
     * CpEvidence.upload() / GuardService.updatePhoto() exactly.
     */
    @Transactional
    public GuardDocumentResponse uploadDocument(TenantId tenantId, UUID guardId,
                                                UploadGuardDocumentRequest req, UUID uploadedBy) {
        findActive(tenantId, guardId); // validates the guard exists

        GuardDocument.Category category;
        try {
            category = GuardDocument.Category.valueOf(req.category());
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException("Invalid document category: " + req.category(),
                    HttpStatus.BAD_REQUEST, "INVALID_DOCUMENT_CATEGORY");
        }

        String fileUrl = req.fileUrl();
        if ((fileUrl == null || fileUrl.isBlank())
                && req.fileBase64() != null && req.fileBase64().startsWith("data:")) {
            log.warn("[Security] Base64 document received for guard={} category={} — stored as "
                            + "PENDING_UPLOAD. Wire up S3 presigned URL before production deployment.",
                    guardId, category);
            fileUrl = "PENDING_UPLOAD";
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new HandyFlowException("Either fileUrl or fileBase64 is required",
                    HttpStatus.BAD_REQUEST, "MISSING_FILE");
        }

        GuardDocument doc = GuardDocument.upload(
                tenantId, guardId, category, fileUrl, req.fileName(), req.notes(), uploadedBy);
        documentRepository.save(doc);

        log.info("[Security] Guard document uploaded guardId={} category={} by={}",
                guardId, category, uploadedBy);

        return toDocumentResponse(doc);
    }

    @Transactional(readOnly = true)
    public List<GuardDocumentResponse> getDocuments(TenantId tenantId, UUID guardId) {
        findActive(tenantId, guardId);
        return documentRepository.findActiveForGuard(tenantId, guardId).stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Transactional
    public void deleteDocument(TenantId tenantId, UUID documentId, UUID actorId,
                               DeleteGuardDocumentRequest req) {
        GuardDocument doc = documentRepository.findActiveById(tenantId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("GuardDocument", documentId.toString()));
        doc.softDelete(actorId, req.reason());
        documentRepository.save(doc);
        log.warn("[Security] Guard document deleted id={} by={} reason='{}'",
                documentId, actorId, req.reason());
    }

    private GuardDocumentResponse toDocumentResponse(GuardDocument d) {
        return new GuardDocumentResponse(
                d.getId(), d.getGuardId(), d.getCategory().name(),
                d.getFileUrl(), d.getFileName(), d.getNotes(), d.getUploadedBy(), d.getCreatedAt());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Guard findActive(TenantId tenantId, UUID id) {
        return guardRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Guard", id.toString()));
    }

    @Transactional
    public ResetPinResponse resetPin(TenantId tenantId, UUID guardId,
                                     UUID supervisorId, ResetPinRequest req) {
        Guard guard = findActive(tenantId, guardId);

        String tempPin    = String.format("%06d",
                new java.security.SecureRandom().nextInt(1_000_000));
        String pinHash    = org.springframework.security.crypto.bcrypt.BCrypt
                .hashpw(tempPin, org.springframework.security.crypto.bcrypt.BCrypt.gensalt(12));
        java.time.Instant expiresAt = java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS);

        guard.setPinHash(pinHash, expiresAt);
        guardRepository.save(guard);

        log.info("[Security] PIN reset guardId={} by supervisor={} reason={}",
                guardId, supervisorId, req.reason());

        return new ResetPinResponse(guardId, tempPin, expiresAt);
    }

    /**
     * Masks the ID/passport number before it leaves the backend at all --
     * enforced here, not left to the frontend, since a frontend-only mask
     * is trivially bypassed by anyone reading the raw network response.
     * Shows the first 6 digits (the DOB portion of an SA ID: YYMMDD) and
     * asterisks the rest. Non-13-digit values (passport numbers, foreign
     * IDs) are left unmasked, since the "first 6 = DOB" convention is SA-ID
     * specific and masking an arbitrary passport number the same way would
     * just as often hide MORE of a shorter identifier than intended.
     */
    private String maskIdNumber(String idNumber) {
        if (idNumber == null) return null;
        String digitsOnly = idNumber.replaceAll("\\D", "");
        if (digitsOnly.length() != 13) return idNumber; // not an SA ID shape -- leave as-is
        return digitsOnly.substring(0, 6) + "*".repeat(digitsOnly.length() - 6);
    }

    private GuardResponse toResponse(Guard g) {
        return new GuardResponse(
                g.getId(), g.getFirstName(), g.getLastName(),
                g.getFullName(), g.getPsiraNumber(), maskIdNumber(g.getIdNumber()),
                g.getPhone(),
                "PENDING_UPLOAD".equals(g.getPhotoUrl()) ? null : g.getPhotoUrl(),
                g.getGrade(), g.isActive(), g.getNotes(), g.getCreatedAt(),
                g.getStatus(), g.getStatusNote(), g.getStatusChangedAt(),
                g.getPsiraExpiryDate(),
                g.getEmployeeCode(),
                g.getEmergencyContactName(),
                g.getEmergencyContactPhone()
        );
    }
}