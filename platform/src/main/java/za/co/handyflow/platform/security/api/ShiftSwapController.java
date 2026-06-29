// security/api/ShiftSwapController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.ShiftSwapService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * ShiftSwapController — REST surface for the two-stage shift swap workflow.
 *
 * Workflow summary:
 *   POST   /swaps                       → requesting guard creates a swap request
 *   POST   /swaps/{id}/accept           → proposed guard accepts their end
 *   POST   /swaps/{id}/approve          → supervisor approves and re-assigns the shift
 *   POST   /swaps/{id}/reject           → supervisor rejects
 *   DELETE /swaps/{id}                  → requesting guard cancels
 *
 * Guard identity for accept/cancel comes from TenantContext.getCurrentUserId()
 * so it cannot be spoofed from the request body (same fix as bug #13).
 */
@Tag(name = "Security - Shift Swaps")
@RestController
@RequestMapping("/api/v1/security/shifts/swaps")
@RequiredArgsConstructor
public class ShiftSwapController {

    private final ShiftSwapService swapService;

    @GetMapping
    @Operation(summary = "List open swap requests awaiting supervisor action")
    public ResponseEntity<ApiResponse<Page<ShiftSwapResponse>>> getPendingSwaps(Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                swapService.getPendingSwaps(tenantId, pageable)));
    }

    @GetMapping("/guard/{guardId}")
    @Operation(summary = "List all swap requests involving a specific guard")
    public ResponseEntity<ApiResponse<Page<ShiftSwapResponse>>> getSwapsByGuard(
            @PathVariable UUID guardId, Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                swapService.getSwapsByGuard(tenantId, guardId, pageable)));
    }

    @PostMapping
    @Operation(summary = "Request a shift swap",
            description = """
               The requesting guard initiates the swap.
               proposedGuardId is optional — omit for an open request (any available guard).
               A shift can only have one open swap request at a time.
               """)
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> createSwapRequest(
            @Valid @RequestBody CreateSwapRequest req) {
        TenantId tenantId        = TenantContext.getTenantIdAsObject();
        UUID     requestingGuard = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        swapService.createSwapRequest(tenantId, requestingGuard, req)));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Proposed guard accepts the swap request",
            description = """
               Moves status from PENDING to PROPOSED_ACCEPTED.
               Only the guard named as proposedGuardId can call this.
               """)
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> acceptSwap(@PathVariable UUID id) {
        TenantId tenantId       = TenantContext.getTenantIdAsObject();
        UUID     acceptingGuard = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                swapService.acceptSwap(tenantId, id, acceptingGuard)));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Supervisor approves the swap",
            description = """
               Runs full validation (guard status, PSiRA, overlap) then re-assigns
               the shift.  If validation fails, the swap is still approved and the
               supervisor is shown the validation notes — the supervisor makes the
               final call, the system doesn't auto-reject.
               """)
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> approveSwap(
            @PathVariable UUID id,
            @RequestBody(required = false) SwapActionRequest req) {
        TenantId tenantId    = TenantContext.getTenantIdAsObject();
        UUID     supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                swapService.approveSwap(tenantId, id, supervisorId,
                        req != null ? req : new SwapActionRequest(null))));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Supervisor rejects the swap request")
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> rejectSwap(
            @PathVariable UUID id,
            @Valid @RequestBody SwapActionRequest req) {
        TenantId tenantId    = TenantContext.getTenantIdAsObject();
        UUID     supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                swapService.rejectSwap(tenantId, id, supervisorId, req)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Requesting guard cancels their swap request",
            description = "Can only be done by the guard who created it, and only while PENDING or PROPOSED_ACCEPTED.")
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> cancelSwap(@PathVariable UUID id) {
        TenantId tenantId       = TenantContext.getTenantIdAsObject();
        UUID     requestingGuard = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                swapService.cancelSwap(tenantId, id, requestingGuard)));
    }
}
