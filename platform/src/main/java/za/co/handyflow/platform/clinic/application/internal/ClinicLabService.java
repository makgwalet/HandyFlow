package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.clinic.domain.model.ClinicLabResult;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.repository.ClinicLabResultRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.dto.lab.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lab results inbox service.
 *
 * Workflow:
 * 1. Lab result arrives (MANUAL upload or future email ingest)
 * 2. System fuzzy-matches patient by name from PDF
 * 3. Doctor reviews, Claude interprets markers
 * 4. Doctor files result against a consultation
 *
 * Future: inbound email webhook (SendGrid Inbound Parse) triggers uploadFromEmail()
 * which extracts PDF attachment, stores to S3, creates ClinicLabResult with
 * source = AMPATH/LANCET/PATHCARE based on sender domain.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicLabService {

    private final ClinicLabResultRepository labRepo;
    private final ClinicPatientRepository   patientRepo;
    private final FileStorageService        fileStorageService;
    private final ClinicAiInterpretationService aiInterpretationService;
    private final ClinicLabSummaryPdfService labSummaryPdfService;
    private final EmailService              emailService;

    /**
     * FIX: "lab result upload doesn't actually upload anything" gap — this
     * used to accept a pdfUrl string as if it already pointed at stored
     * content, when nothing in the flow ever put a file there. Now takes
     * the actual multipart file, stores its bytes via FileStorageService
     * (same shared port built for Tasks attachments — no second storage
     * mechanism invented here), and persists the returned storage key.
     * <p>
     * pdfUrl now holds an opaque storage key, not a fetchable URL — the
     * frontend must download via GET /results/{id}/pdf (see
     * ClinicLabController.downloadResultPdf), not by treating pdfUrl as a
     * direct link.
     */
    @Transactional
    public LabResultResponse uploadResult(TenantId tenantId, MultipartFile file, String source,
                                          String labReference, String patientNameRaw,
                                          Instant collectedAt) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A PDF file is required");
        }

        String storageKey;
        try {
            storageKey = fileStorageService.store(
                    "clinic-lab-results/" + tenantId.getValue(),
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            log.error("Failed to store lab result PDF for tenant={}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Failed to store lab result PDF", e);
        }

        ClinicLabResult result = ClinicLabResult.create(
                tenantId, source,
                storageKey, file.getOriginalFilename(),
                patientNameRaw, labReference
        );
        if (collectedAt != null) {
            result.setCollectedAt(collectedAt);
        }

        // Auto-match patient by name if possible
        if (patientNameRaw != null) {
            tryMatchPatient(tenantId, result, patientNameRaw);
        }

        labRepo.save(result);
        log.info("Uploaded lab result={} source={} patient={} sizeBytes={}",
                result.getId(), result.getSource(), result.getPatientId(), file.getSize());
        return toResponse(result);
    }

    /** Retrieves the stored PDF bytes for download — backs GET /results/{id}/pdf. */
    @Transactional(readOnly = true)
    public DownloadedFile downloadResultPdf(TenantId tenantId, UUID resultId) {
        ClinicLabResult result = labRepo.findByIdAndTenant(tenantId, resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", resultId.toString()));
        if (result.getPdfUrl() == null || result.getPdfUrl().isBlank()) {
            throw new ResourceNotFoundException("LabResult PDF", resultId.toString());
        }
        try {
            byte[] content = fileStorageService.retrieve(result.getPdfUrl());
            String filename = result.getPdfFilename() != null ? result.getPdfFilename() : "lab-result.pdf";
            return new DownloadedFile(content, filename);
        } catch (IOException e) {
            log.error("Failed to retrieve lab result PDF result={}: {}", resultId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve lab result PDF", e);
        }
    }

    /** Carries downloaded bytes back to the controller alongside the filename for the response header. */
    public record DownloadedFile(byte[] content, String fileName) {
    }

    @Transactional(readOnly = true)
    public List<LabResultResponse> getResults(TenantId tenantId, String status) {
        List<ClinicLabResult> results = status != null
                ? labRepo.findByStatus(tenantId, status)
                : labRepo.findAll(tenantId);
        return results.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LabResultResponse> getPatientResults(TenantId tenantId, UUID patientId) {
        return labRepo.findByPatient(tenantId, patientId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public LabResultResponse matchPatient(TenantId tenantId, UUID resultId, UUID patientId) {
        ClinicLabResult result = labRepo.findByIdAndTenant(tenantId, resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", resultId.toString()));
        result.matchPatient(patientId);
        labRepo.save(result);
        return toResponse(result);
    }

    @Transactional
    public LabResultResponse setInterpretation(TenantId tenantId, UUID resultId,
                                               String interpretation) {
        ClinicLabResult result = labRepo.findByIdAndTenant(tenantId, resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", resultId.toString()));
        result.setInterpretation(interpretation);
        labRepo.save(result);
        return toResponse(result);
    }

    /**
     * FIX: completes the "AI-assisted interpretation" loop the audit flagged
     * as scaffolded-but-never-finished — setInterpretation() existed to
     * store the result, but nothing server-side ever called Claude to
     * produce one. This is that missing piece: build the same prompt the
     * frontend used to build client-side, call Claude server-side (key
     * never leaves the backend), and persist the result via the same
     * setInterpretation() path a manual entry would use.
     * <p>
     * Silently no-ops (returns the unchanged result) if the API key isn't
     * configured or the call fails — an AI interpretation is a convenience
     * on top of the lab result, not something that should be able to break
     * viewing/filing/reviewing it.
     */
    @Transactional
    public LabResultResponse interpretWithAi(TenantId tenantId, UUID resultId, String patientFullName) {
        ClinicLabResult result = labRepo.findByIdAndTenant(tenantId, resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", resultId.toString()));

        String resultsText = result.getParsedMarkersJson() != null && !result.getParsedMarkersJson().isBlank()
                ? result.getParsedMarkersJson()
                : "Lab result file: " + (result.getPdfFilename() != null ? result.getPdfFilename() : "uploaded PDF")
                + ", Source: " + result.getSource();

        String prompt = """
                You are a clinical assistant helping a South African doctor understand lab results.
                Write a clear, plain-language interpretation of these lab results for the doctor's review.
                Highlight any abnormal values and their clinical significance. Be concise (3-5 sentences max).
                Do NOT give treatment recommendations — just interpret the findings.

                Patient: %s
                Lab source: %s
                %s

                Results:
                %s
                """.formatted(
                patientFullName != null ? patientFullName : "Unknown",
                result.getSource(),
                result.getCollectedAt() != null ? "Collected: " + result.getCollectedAt() : "",
                resultsText
        );

        String interpretation = aiInterpretationService.interpret(prompt);
        if (interpretation == null || interpretation.isBlank()) {
            // FIX: this used to fall through and return the unchanged
            // (still-null) result with a 200 and a hardcoded "Interpretation
            // generated" message — reporting success when nothing happened.
            // Throw so the controller returns a real error the frontend can
            // show, instead of silently doing nothing.
            throw new IllegalStateException(
                    "AI interpretation is unavailable right now (Anthropic API key not configured, " +
                            "or the request failed) — check server logs for details");
        }
        result.setInterpretation(interpretation);
        labRepo.save(result);
        return toResponse(result);
    }

    @Transactional
    public LabResultResponse markReviewed(TenantId tenantId, UUID resultId, UUID reviewedBy) {
        ClinicLabResult result = labRepo.findByIdAndTenant(tenantId, resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", resultId.toString()));
        result.markReviewed(reviewedBy);
        labRepo.save(result);

        // FIX: "no lab result email" gap — notified was a real column,
        // never set. Deliberately fires here (review sign-off), not on
        // upload — sending an unreviewed result straight to a patient
        // with no clinical context attached is a safety concern.
        sendLabResultNotification(tenantId, result);

        return toResponse(result);
    }

    private void sendLabResultNotification(TenantId tenantId, ClinicLabResult result) {
        try {
            if (result.getPatientId() == null) {
                log.info("Lab result={} has no matched patient — cannot notify", result.getId());
                return;
            }
            ClinicPatient patient = patientRepo.findActiveById(tenantId, result.getPatientId()).orElse(null);
            if (patient == null || patient.getEmail() == null || patient.getEmail().isBlank()) {
                log.info("No email on file for patient={} — lab result={} not emailed",
                        result.getPatientId(), result.getId());
                return;
            }

            byte[] pdfBytes = labSummaryPdfService.generate(tenantId, result.getId());
            String greetingName = patient.getFirstName() != null ? patient.getFirstName() : "there";
            String html = "<p>Dear " + greetingName + ",</p>"
                    + "<p>Your lab results are ready and have been reviewed by your practitioner. "
                    + "A summary is attached for your records.</p>"
                    + "<p>Please discuss these results with your practitioner if you have any questions.</p>";

            emailService.sendWithAttachment(patient.getEmail(), "Your lab results are ready", html,
                    "lab-summary-" + result.getId() + ".pdf", pdfBytes);

            result.markNotified();
            labRepo.save(result);
            log.info("Sent lab result notification patient={} result={}", patient.getId(), result.getId());
        } catch (Exception e) {
            // Deliberately not rethrown — a failed notification email
            // should never undo the review sign-off that already
            // succeeded above.
            log.warn("Lab result notification not sent for result={}: {}", result.getId(), e.getMessage());
        }
    }

    @Transactional
    public LabResultResponse fileResult(TenantId tenantId, UUID resultId, UUID consultationId) {
        ClinicLabResult result = labRepo.findByIdAndTenant(tenantId, resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", resultId.toString()));
        result.file(consultationId);
        labRepo.save(result);
        return toResponse(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void tryMatchPatient(TenantId tenantId, ClinicLabResult result, String rawName) {
        // Simple first-pass: split raw name and try to find a patient by last name
        // In production this would be a proper fuzzy-match / Levenshtein distance
        String[] parts = rawName.trim().split("[,\\s]+");
        if (parts.length == 0) return;
        String lastName = parts[0]; // most labs put surname first

        // Use searchActive with the last name — first match wins
        var matches = patientRepo.searchActive(tenantId, lastName,
                org.springframework.data.domain.PageRequest.of(0, 1));
        if (matches.hasContent()) {
            result.matchPatient(matches.getContent().get(0).getId());
            log.info("Auto-matched lab result={} to patient={}", result.getId(),
                    result.getPatientId());
        }
    }

    private LabResultResponse toResponse(ClinicLabResult r) {
        return new LabResultResponse(
                r.getId(), r.getPatientId(), r.getConsultationId(),
                r.getSource(), r.getLabReference(), r.getCollectedAt(), r.getReceivedAt(),
                r.getPdfUrl(), r.getPdfFilename(), r.getStatus(),
                r.getPatientNameRaw(), r.getParsedMarkersJson(), r.getInterpretation(),
                r.isNotified(), r.getCreatedAt());
    }
}