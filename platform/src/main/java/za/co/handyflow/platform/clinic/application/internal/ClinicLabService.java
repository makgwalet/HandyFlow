package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicLabResult;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.repository.ClinicLabResultRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.dto.lab.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

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

    @Transactional
    public LabResultResponse uploadResult(TenantId tenantId, UploadLabResultRequest req) {
        ClinicLabResult result = ClinicLabResult.create(
                tenantId, req.source(),
                req.pdfUrl(), req.pdfFilename(),
                req.patientNameRaw(), req.labReference()
        );
        // FIX #3 — wire collectedAt from the upload request
        if (req.collectedAt() != null) {
            result.setCollectedAt(req.collectedAt());
        }

        // Auto-match patient by name if possible
        if (req.patientNameRaw() != null) {
            tryMatchPatient(tenantId, result, req.patientNameRaw());
        }

        labRepo.save(result);
        log.info("Uploaded lab result={} source={} patient={}",
                result.getId(), result.getSource(), result.getPatientId());
        return toResponse(result);
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

    @Transactional
    public LabResultResponse markReviewed(TenantId tenantId, UUID resultId, UUID reviewedBy) {
        ClinicLabResult result = labRepo.findByIdAndTenant(tenantId, resultId)
                .orElseThrow(() -> new ResourceNotFoundException("LabResult", resultId.toString()));
        result.markReviewed(reviewedBy);
        labRepo.save(result);
        return toResponse(result);
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
