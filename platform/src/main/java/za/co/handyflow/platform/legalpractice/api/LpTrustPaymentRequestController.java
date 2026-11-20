package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustPaymentRequestService;
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustSettingsService;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Money out of trust: requested by one person, approved by another. See {@link LpTrustPaymentRequestService}. */
@RestController
@RequestMapping("/api/v1/legal-practice")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Trust approvals", description = "Maker-checker for payments out of trust")
public class LpTrustPaymentRequestController {

    private final LpTrustPaymentRequestService service;
    private final LpTrustSettingsService settingsService;
    private final FeatureGuard featureGuard;

    @PostMapping("/clients/{clientId}/trust-payment-requests")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_TRUST_PAYMENT_REQUEST','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Ask for a payment out of this client's trust account (nothing moves until approved)")
    public ResponseEntity<ApiResponse<LpTrustPaymentRequestResponse>> create(
            @PathVariable UUID clientId, @Valid @RequestBody CreateTrustPaymentRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment requested",
                service.create(TenantContext.getTenantIdAsObject(), clientId, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/trust-payment-requests")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_TRUST_PAYMENT_REQUEST','LEGALPRACTICE_TRUST_PAYMENT_APPROVE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LpTrustPaymentRequestResponse>>> list(
            @RequestParam(required = false) String status, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @PostMapping("/trust-payment-requests/{id}/approve")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_TRUST_PAYMENT_APPROVE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Approve a request and post the payment. Not the requester, unless the firm allows it and a reason is given")
    public ResponseEntity<ApiResponse<LpTrustPaymentRequestResponse>> approve(
            @PathVariable UUID id, @RequestBody(required = false) ApproveTrustPaymentRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Payment approved and posted",
                service.approve(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName(), req == null ? null : req.selfApprovalReason())));
    }

    @PostMapping("/trust-payment-requests/{id}/reject")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_TRUST_PAYMENT_APPROVE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpTrustPaymentRequestResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody DecideTrustPaymentRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Request rejected",
                service.reject(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName(), req.reason())));
    }

    @PostMapping("/trust-payment-requests/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_TRUST_PAYMENT_REQUEST','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpTrustPaymentRequestResponse>> cancel(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Request withdrawn",
                service.cancel(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName())));
    }

    @GetMapping("/trust-settings")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_TRUST_PAYMENT_REQUEST','LEGALPRACTICE_TRUST_PAYMENT_APPROVE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<LpTrustSettingsResponse>> settings() {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(settingsService.get(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/trust-settings")
    @PreAuthorize("hasAuthority('LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Allow or forbid one person approving their own trust payment (sole-practitioner exception) — ADMIN only, reason recorded")
    public ResponseEntity<ApiResponse<LpTrustSettingsResponse>> updateSettings(@Valid @RequestBody UpdateTrustSettingsRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Setting changed",
                settingsService.update(TenantContext.getTenantIdAsObject(), req.selfApprovalAllowed(), req.reason(),
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}
