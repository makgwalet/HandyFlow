// security/api/GuardShiftSwapController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.ShiftSwapService;
import za.co.handyflow.platform.security.dto.CreateSwapRequest;
import za.co.handyflow.platform.security.dto.ShiftSwapResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * GAP-08 fix. {@code ShiftSwapController}'s own class doc comment already
 * flagged this explicitly as an open, unresolved question — "createSwapRequest/
 * acceptSwap/cancelSwap may not actually be callable from a guard's own
 * Shield app today... not solved here either" — rather than an unnoticed
 * gap, matching the same wrong-JWT-surface shape as every other
 * controller migrated so far in this pass.
 * <p>
 * createSwapRequest/acceptSwap/cancelSwap already had the correct
 * authority ({@code hasAnyAuthority('SECURITY_GUARD','SECURITY_MANAGE')})
 * and already resolve identity via {@code TenantContext.getCurrentUserId()}
 * — same confirmed-working mechanism under GuardJwtFilter relied on
 * throughout this pass (checkpoint scanning, incident reporting). Purely
 * a routing fix for those three, copied unchanged.
 * <p>
 * {@code getSwapsByGuard(guardId)} — "the obvious way for a guard to see
 * their own swap history" per the gap report — required SECURITY_READ,
 * which guard tokens don't carry, so a guard couldn't read their own
 * swap list even setting the JWT-surface question aside. Fixed here as
 * {@code GET /my-swaps} with NO guardId parameter at all — resolved from
 * the caller's own identity, same "no free identity/site parameter"
 * posture already established for GAP-07's gate-access reads, so a guard
 * can only ever see their own swap history, not query for anyone else's
 * by changing a path variable.
 * <p>
 * approve/reject and the tenant-wide pending-swaps list remain
 * supervisor-only on the original, untouched {@link ShiftSwapController}.
 */
@Tag(name = "Guard - Shift Swaps", description = "Guard-facing shift swap request/accept/cancel (mobile)")
@RestController
@RequestMapping("/api/v1/guard/shifts/swaps")
@RequiredArgsConstructor
public class GuardShiftSwapController {

    private final ShiftSwapService swapService;

    @GetMapping("/my-swaps")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "This guard's own swap request history — guard-facing",
            description = "No guardId parameter — always the calling guard's own history, " +
                    "resolved from the authenticated session.")
    public ResponseEntity<ApiResponse<Page<ShiftSwapResponse>>> getMySwaps(Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID guardId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                swapService.getSwapsByGuard(tenantId, guardId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Request a shift swap — guard-facing",
            description = "proposedGuardId is optional — omit for an open request (any " +
                    "available guard). A shift can only have one open swap request at a time.")
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> createSwapRequest(
            @Valid @RequestBody CreateSwapRequest req) {
        TenantId tenantId        = TenantContext.getTenantIdAsObject();
        UUID     requestingGuard = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        swapService.createSwapRequest(tenantId, requestingGuard, req)));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Proposed guard accepts the swap request — guard-facing",
            description = "Moves status from PENDING to PROPOSED_ACCEPTED. Only the guard " +
                    "named as proposedGuardId can call this.")
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> acceptSwap(@PathVariable UUID id) {
        TenantId tenantId       = TenantContext.getTenantIdAsObject();
        UUID     acceptingGuard = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                swapService.acceptSwap(tenantId, id, acceptingGuard)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Requesting guard cancels their swap request — guard-facing",
            description = "Can only be done by the guard who created it, and only while " +
                    "PENDING or PROPOSED_ACCEPTED.")
    public ResponseEntity<ApiResponse<ShiftSwapResponse>> cancelSwap(@PathVariable UUID id) {
        TenantId tenantId        = TenantContext.getTenantIdAsObject();
        UUID     requestingGuard = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                swapService.cancelSwap(tenantId, id, requestingGuard)));
    }
}
