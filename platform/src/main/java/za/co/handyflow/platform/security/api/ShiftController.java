// security/api/ShiftController.java

package za.co.handyflow.platform.security.api;

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
import za.co.handyflow.platform.security.application.internal.ShiftService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/shifts")
@RequiredArgsConstructor
@Tag(name = "Security - Shifts", description = "Shift scheduling and management")
public class ShiftController {

    private final ShiftService shiftService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<ShiftResponse>>> getShifts(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                shiftService.getShifts(tenantId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Schedule a new shift — validates no overlapping shifts for guard")
    public ResponseEntity<ApiResponse<ShiftResponse>> createShift(
            @Valid @RequestBody CreateShiftRequest request
    ) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        var shift = shiftService.createShift(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Shift scheduled", shift));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Guard starts shift — status: SCHEDULED → ACTIVE")
    public ResponseEntity<ApiResponse<ShiftResponse>> startShift(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success("Shift started",
                shiftService.startShift(tenantId, id)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Guard completes shift — status: ACTIVE → COMPLETED")
    public ResponseEntity<ApiResponse<ShiftResponse>> completeShift(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success("Shift completed",
                shiftService.completeShift(tenantId, id)));
    }
}
