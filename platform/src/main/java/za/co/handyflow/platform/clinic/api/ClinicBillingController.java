package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicBillingService;
import za.co.handyflow.platform.clinic.application.internal.ClinicService;
import za.co.handyflow.platform.clinic.dto.billing.*;
import za.co.handyflow.platform.clinic.dto.ConsultationResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/billing")
@RequiredArgsConstructor
@Tag(name = "Clinic Billing", description = "Medical aid claims, payments, outstanding balances")
public class ClinicBillingController {

    private final ClinicBillingService billingService;
    private final ClinicService        clinicService;  // FIX #8 — for unbilled consultations

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

    // ── FIX #6: Outstanding / Payments / Revenue ──────────────────────────────
    // These power BillingTab's three views. Outstanding derives from claims;
    // Payments and Revenue return empty until the payments table is built.

    @GetMapping("/outstanding")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Outstanding balances per patient (derived from unpaid claims)")
    public ResponseEntity<ApiResponse<List<OutstandingBalanceResponse>>> getOutstanding() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                billingService.getOutstanding(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Payment history — stub until payments table is built")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPayments(
            @RequestParam(required = false, defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                billingService.getPayments(TenantContext.getTenantIdAsObject(), period)));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('CLINIC_BILLING_READ')")
    @Operation(summary = "Revenue breakdown by period — stub until aggregation is built")
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
