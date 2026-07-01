// security/api/VettingController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.VettingService;
import za.co.handyflow.platform.security.domain.model.DeclinedPrincipal;
import za.co.handyflow.platform.security.domain.model.PrincipalVetting;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * VettingController — Part 9.5 (officer CP vetting) and 9.6 (principal vetting).
 *
 * All endpoints require VIP_DETAIL_ACCESS — vetting records are the most
 * sensitive data in this module (sanctions hits, PEP status, intelligence
 * behind a declination decision), and should be restricted to the same
 * tier as principal medical notes and threat intel.
 *
 * Officer vetting (9.5) is done through the Armoury-style guard competency
 * pattern — supervisor asserts a CP clearance tier on a guard after the
 * underlying screening evidence (polygraph etc.) has been logged in Phase 2's
 * screening records. The DetailAssignment gate in CloseProtectionService
 * enforces this tier automatically on every assignment attempt.
 */
@Tag(name = "Security - CP Vetting")
@RestController
@RequestMapping("/api/v1/security/cp/vetting")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('VIP_DETAIL_ACCESS')")
public class VettingController {

    private final VettingService vettingService;

    // ── Part 9.5: Officer CP vetting tier ─────────────────────────────────────

    @PostMapping("/officers/{guardId}/tier")
    @Operation(
            summary = "Set a guard's CP clearance tier",
            description = """
            Asserts the guard's vetting clearance level for close protection
            assignments. tier: STANDARD | ENHANCED | HIGH | CRITICAL.
            Maps to principal threat_level: a CRITICAL principal requires
            a guard at CRITICAL tier; HIGH requires HIGH or above.
            The underlying evidence (polygraph results etc.) lives in the
            Phase 2 guard screening records — this tier is the administrative
            conclusion from that evidence.
            """)
    public ResponseEntity<ApiResponse<Void>> setGuardVettingTier(
            @PathVariable UUID guardId,
            @Valid @RequestBody SetCpVettingTierRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        vettingService.setGuardCpVettingTier(tenantId, guardId, req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Part 9.6: Principal vetting ───────────────────────────────────────────

    @GetMapping("/principals/{principalId}")
    @Operation(summary = "Get all vetting checks for a principal")
    public ResponseEntity<ApiResponse<List<PrincipalVetting>>> getVettingHistory(
            @PathVariable UUID principalId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                vettingService.getVettingHistory(tenantId, principalId)));
    }

    @PostMapping("/principals/{principalId}")
    @Operation(
            summary = "Initiate a vetting check on a principal",
            description = "vettingType: SANCTIONS_SCREENING | PEP_CHECK | ADVERSE_MEDIA | " +
                    "SOURCE_OF_FUNDS | CRIMINAL_ASSOCIATES | OTHER. " +
                    "Starts as PENDING — record result via POST /{checkId}/result.")
    public ResponseEntity<ApiResponse<PrincipalVetting>> createVettingCheck(
            @PathVariable UUID principalId,
            @Valid @RequestBody CreatePrincipalVettingRequest req) {
        TenantId tenantId   = TenantContext.getTenantIdAsObject();
        UUID     supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                vettingService.createVettingCheck(tenantId, principalId, req, supervisorId)));
    }

    @PostMapping("/checks/{checkId}/result")
    @Operation(
            summary = "Record the result of a vetting check",
            description = "result: CLEAR | HIT | INCONCLUSIVE. A HIT flags the principal " +
                    "for compliance review but does NOT auto-cancel active engagements — " +
                    "that decision is for leadership.")
    public ResponseEntity<ApiResponse<PrincipalVetting>> recordVettingResult(
            @PathVariable UUID checkId,
            @Valid @RequestBody RecordVettingResultRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                vettingService.recordVettingResult(tenantId, checkId, req)));
    }

    // ── Declined principals register ──────────────────────────────────────────

    @GetMapping("/declined")
    @Operation(summary = "The declined principals register — companies formally decided against")
    public ResponseEntity<ApiResponse<List<DeclinedPrincipal>>> getDeclinedRegister() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                vettingService.getDeclinedRegister(tenantId)));
    }

    @PostMapping("/principals/{principalId}/decline")
    @Operation(
            summary = "Formally decline to take an engagement for this principal",
            description = """
            Records a compliance decision not to work with this person.
            sensitiveDetail (the intelligence behind the decision) is
            encrypted before storage — it's treated as more restricted
            than even the principal record itself.
            Does not prevent re-accepting in future; this is a compliance
            record, not a system block.
            """)
    public ResponseEntity<ApiResponse<DeclinedPrincipal>> declinePrincipal(
            @PathVariable UUID principalId,
            @Valid @RequestBody DeclinePrincipalRequest req) {
        TenantId tenantId   = TenantContext.getTenantIdAsObject();
        UUID     declinedBy  = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                vettingService.declinePrincipal(tenantId, principalId, declinedBy, req)));
    }
}
