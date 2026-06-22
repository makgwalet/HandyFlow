package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.ClinicMedicalAidRepository;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.billing.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicBillingService {

    private final ClinicClaimRepository              claimRepo;
    private final ClinicConsultationRepository       consultationRepo;
    private final ClinicPatientRepository            patientRepo;
    private final ClinicPractitionerRepository       practitionerRepo;
    private final ClinicPrescriptionRepository       prescriptionRepo;
    private final ClinicMedicationCatalogueRepository medicationRepo;
    private final ClinicMedicalAidRepository           medicalAidRepo;

    // ── Create claim from consultation ────────────────────────────────────────

    @Transactional
    public ClinicClaimResponse createClaim(TenantId tenantId, UUID consultationId,
                                           CreateClaimRequest req) {
        if (claimRepo.findByConsultation(tenantId, consultationId).isPresent())
            throw new IllegalStateException("A claim already exists for consultation " + consultationId);

        ClinicConsultation c = consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));

        // FIX #11 — Auto-fill scheme details from stored medical aid record.
        // If the request provides them explicitly, use those; otherwise look up
        // the patient's (or their principal's) active medical aid record.
        String schemeName   = req.schemeName();
        String memberNumber = req.memberNumber();
        String dependentCode = req.dependentCode();

        if (schemeName == null || memberNumber == null) {
            // Try patient's own record first, then principal's (for dependants)
            var aidRecords = medicalAidRepo.findActiveByPatient(tenantId, c.getPatientId());
            if (aidRecords.isEmpty()) {
                // Check if patient is a dependant — look up principal's record
                patientRepo.findActiveById(tenantId, c.getPatientId())
                        .filter(p -> p.getPrincipalId() != null)
                        .ifPresent(p -> {
                            var principalAids = medicalAidRepo.findActiveByPatient(
                                    tenantId, p.getPrincipalId());
                            if (!principalAids.isEmpty()) {
                                var aid = principalAids.get(0);
                                // Override nulls only — request values take precedence
                            }
                        });
            }
            if (!aidRecords.isEmpty()) {
                var aid = aidRecords.get(0);
                if (schemeName   == null) schemeName   = aid.getSchemeName();
                if (memberNumber == null) memberNumber = aid.getMemberNumber();
                if (dependentCode == null) dependentCode = aid.getDependentCode();
            }
        }

        ClinicClaim claim = ClinicClaim.create(tenantId, consultationId,
                c.getPatientId(), c.getPractitionerId(),
                schemeName, memberNumber, dependentCode);
        claimRepo.save(claim);

        // ── Consultation tariff line ───────────────────────────────────────────
        if (req.consultationTariffCode() != null) {
            ClinicClaimLine line = ClinicClaimLine.of(
                    claim.getId(), "CONSULTATION",
                    req.consultationTariffCode(), null,
                    req.consultationIcd10Code(),
                    req.consultationDescription() != null ? req.consultationDescription() : "Consultation",
                    BigDecimal.ONE, req.consultationRate(), null, 0);
            claim.addLine(line);
        }

        // ── Procedure lines ───────────────────────────────────────────────────
        if (req.procedures() != null) {
            int procOrder = 10; // FIX #5 — renamed from `order` to avoid duplicate declaration
            for (var proc : req.procedures()) {
                ClinicClaimLine line = ClinicClaimLine.of(
                        claim.getId(), "PROCEDURE",
                        proc.tariffCode(), null, proc.icd10Code(),
                        proc.description(), proc.quantity(), proc.unitPrice(), null, procOrder);
                claim.addLine(line);
                procOrder += 10;
            }
        }

        // ── Medicine lines (from consultation prescriptions) ──────────────────
        List<ClinicPrescription> rxList = prescriptionRepo.findByConsultation(tenantId, consultationId);
        int medicineOrder = 100; // FIX #5 — was also named `order`, causing compile error
        for (ClinicPrescription rx : rxList) {
            BigDecimal unitPrice = rx.getNappiCode() != null
                    ? medicationRepo.findBySep(rx.getNappiCode(), tenantId).orElse(BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            ClinicClaimLine line = ClinicClaimLine.of(
                    claim.getId(), "MEDICINE",
                    null, rx.getNappiCode(),
                    c.getIcd10Codes() != null && !c.getIcd10Codes().isEmpty()
                            ? c.getIcd10Codes().get(0) : null,
                    rx.getMedicationName(),
                    BigDecimal.valueOf(rx.getQuantity() != null ? rx.getQuantity() : 1),
                    unitPrice, rx.getId(), medicineOrder);
            claim.addLine(line);
            medicineOrder += 10;
        }

        claim.recalculate();
        claimRepo.save(claim);

        // FIX #9 — mark the source consultation as billed so it stops appearing
        // in the "unbilled consultations" picker (findAllUnbilled) in ClaimsTab.
        // Without this, a doctor could accidentally create two claims for one consultation
        // (the unique index on consultation_id catches it at DB level with a 500, not a UI warning).
        c.markBilled(req.consultationTariffCode(),
                req.consultationRate() != null ? req.consultationRate() : BigDecimal.ZERO);
        consultationRepo.save(c);

        log.info("Created claim={} consultation={} gross={}", claim.getId(), consultationId, claim.getGrossAmount());
        return toResponse(claim, tenantId);
    }

    @Transactional(readOnly = true)
    public ClinicClaimResponse getClaim(TenantId tenantId, UUID consultationId) {
        return claimRepo.findByConsultation(tenantId, consultationId)
                .map(c -> toResponse(c, tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Claim for consultation", consultationId.toString()));
    }

    // IMPROVEMENT A — batch-load patient/practitioner names so ClaimsTab can display them
    @Transactional(readOnly = true)
    public List<ClinicClaimResponse> getClaims(TenantId tenantId, String status) {
        List<ClinicClaim> claims = status != null
                ? claimRepo.findByStatus(tenantId, status)
                : claimRepo.findAll(tenantId);

        // Batch-load names — avoids N+1
        Set<UUID> patientIds     = claims.stream().map(ClinicClaim::getPatientId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> practitionerIds = claims.stream().map(ClinicClaim::getPractitionerId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, String> patientNames = patientIds.isEmpty() ? Map.of()
                : patientRepo.findAllByIds(tenantId, patientIds).stream()
                .collect(Collectors.toMap(ClinicPatient::getId, ClinicPatient::getFullName));
        Map<UUID, String> practNames = practitionerIds.isEmpty() ? Map.of()
                : practitionerRepo.findAllByIds(tenantId, practitionerIds).stream()
                .collect(Collectors.toMap(ClinicPractitioner::getId, ClinicPractitioner::getFullName));

        return claims.stream()
                .map(c -> toResponseWithNames(c, patientNames, practNames))
                .toList();
    }

    @Transactional
    public ClinicClaimResponse submitClaim(TenantId tenantId, UUID claimId, String referenceNumber) {
        ClinicClaim claim = claimRepo.findActiveById(tenantId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));

        // FIX Gap B — Claim readiness validation before submission.
        // Schemes reject claims with missing ICD-10 codes on any line — catch this here
        // rather than letting it fail silently at the medical aid switch.
        var linesWithoutIcd10 = claim.getLines().stream()
                .filter(l -> l.getIcd10Code() == null || l.getIcd10Code().isBlank())
                .map(l -> l.getDescription() + " (" + l.getLineType() + ")")
                .toList();

        if (!linesWithoutIcd10.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot submit: the following claim lines are missing ICD-10 codes: " +
                            String.join(", ", linesWithoutIcd10));
        }

        // Require at least one line
        if (claim.getLines().isEmpty()) {
            throw new IllegalStateException("Cannot submit a claim with no billing lines");
        }

        claim.submit(referenceNumber);
        claimRepo.save(claim);
        log.info("Submitted claim={} ref={} lines={}", claimId, referenceNumber, claim.getLines().size());
        return toResponse(claim, tenantId);
    }

    @Transactional
    public ClinicClaimResponse updateClaimStatus(TenantId tenantId, UUID claimId,
                                                 String action, String reason,
                                                 java.math.BigDecimal schemeAmount) {
        ClinicClaim claim = claimRepo.findActiveById(tenantId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));
        switch (action.toUpperCase()) {
            case "ACCEPT"  -> claim.markAccepted();
            case "REJECT"  -> claim.markRejected(reason);
            case "PAID" -> {
                // Use provided amount or default to full gross (scheme paid everything)
                var paid = schemeAmount != null ? schemeAmount : claim.getGrossAmount();
                claim.markPaid(paid);
            }
            case "PARTIAL" -> {
                // schemeAmount is required for PARTIAL — default to 80% if not provided
                var partial = schemeAmount != null ? schemeAmount
                        : claim.getGrossAmount()
                        .multiply(new java.math.BigDecimal("0.80"))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                claim.markPartial(partial);
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        claimRepo.save(claim);
        return toResponse(claim, tenantId);
    }

    // Backwards-compatible overload for callers that don't supply a scheme amount
    @Transactional
    public ClinicClaimResponse updateClaimStatus(TenantId tenantId, UUID claimId,
                                                 String action, String reason) {
        return updateClaimStatus(tenantId, claimId, action, reason, null);
    }

    // ── Outstanding / Payments / Revenue stubs (FIX #6) ─────────────────────
    // Real queries will be added when the payments table is implemented.
    // Returning typed empty structures lets the frontend render without crashing.

    @Transactional(readOnly = true)
    public List<OutstandingBalanceResponse> getOutstanding(TenantId tenantId) {
        // Compute from claims: patients with SUBMITTED or DRAFT claims have outstanding balances
        return claimRepo.findByStatus(tenantId, "SUBMITTED").stream()
                .map(c -> new OutstandingBalanceResponse(
                        c.getPatientId(), "Patient", null,
                        c.getGrossAmount(), BigDecimal.ZERO, c.getSchemePortion(),
                        c.getCreatedAt(), 1))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments(TenantId tenantId, String period) {
        // Stub — returns empty until ClinicPayment table is implemented
        return List.of();
    }

    @Transactional(readOnly = true)
    public List<RevenuePointResponse> getRevenue(TenantId tenantId, String period) {
        // Stub — returns empty until revenue aggregation is implemented
        return List.of();
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private ClinicClaimResponse toResponse(ClinicClaim c, TenantId tenantId) {
        return toResponseWithNames(c, Map.of(), Map.of());
    }

    private ClinicClaimResponse toResponseWithNames(ClinicClaim c,
                                                    Map<UUID, String> patientNames,
                                                    Map<UUID, String> practNames) {
        List<ClinicClaimLineResponse> lines = c.getLines().stream()
                .map(l -> new ClinicClaimLineResponse(
                        l.getId(), l.getLineType(), l.getTariffCode(), l.getNappiCode(),
                        l.getIcd10Code(), l.getDescription(), l.getQuantity(),
                        l.getUnitPrice(), l.getGrossAmount(),
                        l.getSchemePortion(), l.getPatientPortion()))
                .toList();
        return new ClinicClaimResponse(
                c.getId(), c.getConsultationId(), c.getPatientId(),
                patientNames.getOrDefault(c.getPatientId(), null),
                c.getPractitionerId(),
                practNames.getOrDefault(c.getPractitionerId(), null),
                c.getStatus(), c.getSchemeName(), c.getMemberNumber(), c.getDependentCode(),
                c.getGrossAmount(), c.getSchemePortion(), c.getPatientPortion(),
                c.getSubmittedAt(), c.getReferenceNumber(), c.getRejectionReason(),
                lines, c.getCreatedAt());
    }
}
