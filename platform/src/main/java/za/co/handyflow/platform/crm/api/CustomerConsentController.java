package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.CustomerConsentService;
import za.co.handyflow.platform.crm.domain.model.CustomerConsent;
import za.co.handyflow.platform.crm.domain.model.CustomerConsent.ConsentSource;
import za.co.handyflow.platform.crm.domain.model.CustomerConsent.LawfulBasis;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/customers")
@RequiredArgsConstructor
@Tag(name = "CRM - Consent", description = "POPIA consent management")
public class CustomerConsentController {

    private final CustomerConsentService consentService;

    /** GET /{id}/consent — get active consent record */
    @GetMapping("/{id}/consent")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Get active POPIA consent record for a customer")
    public ResponseEntity<ApiResponse<Object>> getConsent(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var consent  = consentService.getActive(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(consent));
    }

    /** GET /{id}/consent/history — full consent history */
    @GetMapping("/{id}/consent/history")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Get full POPIA consent history for a customer")
    public ResponseEntity<ApiResponse<List<CustomerConsent>>> getConsentHistory(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var history  = consentService.getHistory(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    /**
     * POST /{id}/consent — record initial consent.
     *
     * Request body (JSON):
     * {
     *   "lawfulBasis": "CONSENT",
     *   "purposes": ["SERVICE_DELIVERY", "MARKETING"],
     *   "source": "WEB_FORM",
     *   "evidence": "Signed contact form on website 2025-06-25",
     *   "retentionYears": 7
     * }
     */
    /**
     * FIX: confirmed via real testing — POST returned 403 Forbidden.
     * CUSTOMER_WRITE doesn't exist as a granted authority anywhere in this
     * system; CustomerController.java's entire convention is CREATE/READ/
     * UPDATE/DELETE (confirmed: zero other CRM endpoints ever grant or
     * check "WRITE"). This controller was the only place using it, across
     * all three of its non-GET endpoints — same fix applied below on
     * withdrawConsent and recordReview.
     */
    @PostMapping("/{id}/consent")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Record POPIA consent for a customer")
    public ResponseEntity<ApiResponse<Object>> recordConsent(
            @PathVariable UUID id,
            @RequestBody ConsentRequest request
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var consent  = consentService.recordConsent(
                tenantId, id,
                request.lawfulBasis(),
                request.purposes(),
                request.source(),
                request.evidence(),
                request.retentionYears()
        );
        return ResponseEntity.ok(ApiResponse.success("Consent recorded", consent));
    }

    /**
     * DELETE /{id}/consent — withdraw consent.
     *
     * Request body: { "reason": "Customer requested opt-out via email 2025-06-25" }
     */
    @DeleteMapping("/{id}/consent")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Withdraw POPIA consent for a customer")
    public ResponseEntity<ApiResponse<Object>> withdrawConsent(
            @PathVariable UUID id,
            @RequestBody WithdrawalRequest request
    ) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var consent  = consentService.withdrawConsent(tenantId, id, request.reason());
        return ResponseEntity.ok(ApiResponse.success("Consent withdrawn", consent));
    }

    /**
     * POST /consent/{consentId}/review — mark a retention review as done.
     *
     * FIX: confirmed via real testing (on the sibling follow-ups endpoint
     * that copied this exact pattern) — @RequestAttribute("userId") throws
     * ServletRequestBindingException, since nothing in this runtime's
     * filter chain actually populates that request attribute. Same
     * SecurityContextHolder resolution PopiaExportController already uses
     * successfully in this same class's sibling controller.
     */
    @PostMapping("/consent/{consentId}/review")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Record that a retention review was performed")
    public ResponseEntity<ApiResponse<Void>> recordReview(@PathVariable UUID consentId) {
        var tenantId = TenantContext.getTenantIdAsObject();
        consentService.recordReview(tenantId, consentId, currentUserId());
        return ResponseEntity.ok(ApiResponse.success("Review recorded", null));
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        try { return UUID.fromString(auth.getName()); }
        catch (Exception e) { return null; }
    }

    // ── Request records ───────────────────────────────────────────────────────

    record ConsentRequest(
            LawfulBasis   lawfulBasis,
            String[]      purposes,
            ConsentSource source,
            String        evidence,
            Integer       retentionYears
    ) {}

    record WithdrawalRequest(String reason) {}
}