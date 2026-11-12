// security/api/GuardMyWorkController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.GuardMyWorkService;
import za.co.handyflow.platform.security.dto.GuardMyWorkDtos.RoundView;
import za.co.handyflow.platform.security.dto.GuardMyWorkDtos.ShiftView;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;

/** The guard's own shifts and patrol rounds (mobile). Guard token only; the guard is never taken from the request. */
@RestController
@RequestMapping("/api/v1/guard/me")
@RequiredArgsConstructor
@Tag(name = "Guard - My work", description = "Guard-facing roster and patrol rounds (mobile)")
public class GuardMyWorkController {

    private final GuardMyWorkService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/shifts")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "My shifts: from 12 hours ago to 7 days ahead, soonest first")
    public ResponseEntity<ApiResponse<List<ShiftView>>> shifts() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                service.myShifts(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/rounds")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(summary = "Patrol rounds of my current shift, each with its checkpoints in order and which are scanned",
            description = "409 NO_OPEN_SESSION until the guard has clocked in.")
    public ResponseEntity<ApiResponse<List<RoundView>>> rounds() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                service.myRounds(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }
}
