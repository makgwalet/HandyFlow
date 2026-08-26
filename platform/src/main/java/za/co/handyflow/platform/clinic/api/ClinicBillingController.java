package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicBillingService;
import za.co.handyflow.platform.clinic.application.internal.ClinicClaimSubmissionPdfService;
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientInvoicePdfService;
import za.co.handyflow.platform.clinic.application.internal.ClinicService;
import za.co.handyflow.platform.clinic.application.internal.ClinicStatementOfAccountPdfService;
import za.co.handyflow.platform.clinic.dto.billing.*;
import za.co.handyflow.platform.clinic.dto.ConsultationResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/billing")
@RequiredArgsConstructor
@Tag(name = "Clinic Billing", description = "Medical aid claims, payments, outstanding balances")
public class ClinicBillingController {

    private final ClinicBillingService billingService;
    private final ClinicService        clinicService;  // FIX #8 — for unbilled consultations
    private final ClinicPatientInvoicePdfService patientInvoicePdfService;
    private final ClinicClaimSubmissionPdfService claimSubmissionPdfService;
    private final ClinicStatementOfAccountPdfService statementOfAccountPdfService;

    // ── Claims ────────────────────────────────────────────────────────────────

    @GetMapping("/claims")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "List claims, optionally filter by status")
    public ResponseEntity<ApiResponse<List<ClinicClaimResponse>>> getClaims(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                billingService.getClaims(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/consultations/{consultationId}/claim")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Get claim for a consultation")
    public ResponseEntity<ApiResponse<ClinicClaimResponse>> getClaim(
            @PathVariable UUID consultationId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                billingService.getClaim(TenantContext.getTenantIdAsObject(), consultationId)));
    }

    @PostMapping("/consultations/{consultationId}/claim")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_WRITE')")
    @Operation(summary = "Create a medical aid claim from a consultation")
    public ResponseEntity<ApiResponse<ClinicClaimResponse>> createClaim(
            @PathVariable UUID consultationId,
            @Valid @RequestBody CreateClaimRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Claim created",
                billingService.createClaim(TenantContext.getTenantIdAsObject(), consultationId, req)));
    }

    @PostMapping("/claims/{id}/submit")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_WRITE')")
    @Operation(summary = "Submit claim to medical aid switch")
    public ResponseEntity<ApiResponse<ClinicClaimResponse>> submitClaim(
            @PathVariable UUID id,
            @RequestParam(required = false) String referenceNumber) {
        return ResponseEntity.ok(ApiResponse.success("Claim submitted",
                billingService.submitClaim(TenantContext.getTenantIdAsObject(), id, referenceNumber)));
    }

    /**
     * FIX: "no batch claim submission" gap — practices with volume typically
     * want to submit several claims at once rather than one at a time.
     */
    @PostMapping("/claims/batch-submit")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_WRITE')")
    @Operation(summary = "Submit multiple claims to the medical aid switch in one call")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.clinic.dto.billing.BatchSubmitClaimsResponse>> batchSubmitClaims(
            @jakarta.validation.Valid @RequestBody za.co.handyflow.platform.clinic.dto.billing.BatchSubmitClaimsRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Batch submit complete",
                billingService.batchSubmitClaims(TenantContext.getTenantIdAsObject(), req.claimIds())));
    }

    @PostMapping("/claims/{id}/{action}")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_WRITE')")
    @Operation(summary = "Update claim status: accept | reject | paid | partial")
    public ResponseEntity<ApiResponse<ClinicClaimResponse>> updateClaimStatus(
            @PathVariable UUID id,
            @PathVariable String action,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) java.math.BigDecimal schemeAmount) {
        // schemeAmount: actual amount paid by scheme (required for paid/partial actions)
        return ResponseEntity.ok(ApiResponse.success("Claim updated",
                billingService.updateClaimStatus(
                        TenantContext.getTenantIdAsObject(), id, action, reason, schemeAmount)));
    }

    /**
     * FIX: "no patient invoice/receipt PDF" gap — self-pay/co-pay amount
     * after the medical aid scheme's contribution had no printable document,
     * despite ClinicClaim already tracking patientPortion separately.
     */
    @GetMapping("/claims/{id}/patient-invoice-pdf")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Download patient-portion invoice/receipt PDF for a claim")
    public ResponseEntity<byte[]> downloadPatientInvoicePdf(@PathVariable UUID id) {
        byte[] pdf = patientInvoicePdfService.generate(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"patient-invoice-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    /**
     * FIX: "no claim submission form/EDI record" gap — a printable record
     * of what was actually submitted to the scheme, for dispute resolution.
     */
    @GetMapping("/claims/{id}/submission-pdf")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Download the claim submission record PDF")
    public ResponseEntity<byte[]> downloadClaimSubmissionPdf(@PathVariable UUID id) {
        byte[] pdf = claimSubmissionPdfService.generate(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"claim-submission-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    /**
     * FIX: "no patient statement of account" gap — nothing aggregated a
     * patient's full billing history across claims/visits into one document.
     */
    @GetMapping("/patients/{patientId}/statement-pdf")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Download a statement of account PDF for a patient — omit from/to for all-time")
    public ResponseEntity<byte[]> downloadPatientStatementPdf(
            @PathVariable UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = statementOfAccountPdfService.generate(TenantContext.getTenantIdAsObject(), patientId, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"statement-" + patientId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }

    /**
     * FIX: "no PDF is ever emailed" gap — manual send rather than
     * automatic, since a statement isn't tied to any single triggering
     * event (see ClinicStatementOfAccountPdfService.emailStatement for
     * the full reasoning).
     */
    @PostMapping("/patients/{patientId}/statement/email")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_WRITE')")
    @Operation(summary = "Email the statement of account to the patient — omit from/to for all-time")
    public ResponseEntity<ApiResponse<Void>> emailPatientStatement(
            @PathVariable UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        statementOfAccountPdfService.emailStatement(TenantContext.getTenantIdAsObject(), patientId, from, to);
        return ResponseEntity.ok(ApiResponse.success("Statement emailed", null));
    }

    // ── Outstanding / Payments / Revenue ──────────────────────────────────────
    // These power BillingTab's three views. All three now backed by real
    // data via ClinicPayment (clinic_payments — an existing table this
    // module previously had no entity for).

    @GetMapping("/outstanding")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Outstanding balances per patient (derived from unpaid claims, net of recorded payments)")
    public ResponseEntity<ApiResponse<List<OutstandingBalanceResponse>>> getOutstanding() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                billingService.getOutstanding(TenantContext.getTenantIdAsObject())));
    }

    /**
     * FIX: "broken payment endpoint" — BillingTab's "Record payment" modal
     * has posted here all along; this endpoint just never existed.
     */
    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_WRITE')")
    @Operation(summary = "Record a payment against a patient")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordPayment(
            @jakarta.validation.Valid @RequestBody RecordPaymentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Payment recorded",
                billingService.recordPayment(TenantContext.getTenantIdAsObject(), req,
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Payment history for a period")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPayments(
            @RequestParam(required = false, defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                billingService.getPayments(TenantContext.getTenantIdAsObject(), period)));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Revenue breakdown by period, bucketed for charting")
    public ResponseEntity<ApiResponse<List<RevenuePointResponse>>> getRevenue(
            @RequestParam(required = false, defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                billingService.getRevenue(TenantContext.getTenantIdAsObject(), period)));
    }

    // ── FIX #8: Unbilled consultations for claim creation ────────────────────
    // ClaimsTab calls GET /billing/consultations?unbilled=true to populate
    // the "Select consultation" dropdown in the New Claim modal.

    @GetMapping("/consultations")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "List consultations — optionally filter to unbilled only (for claim creation)")
    public ResponseEntity<ApiResponse<List<ConsultationResponse>>> getConsultations(
            @RequestParam(required = false, defaultValue = "false") boolean unbilled) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var page = clinicService.getConsultations(tenantId, unbilled, PageRequest.of(0, 200));
        return ResponseEntity.ok(ApiResponse.success("Success", page.getContent()));
    }
}