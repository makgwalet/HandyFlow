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
@Tag(name = "Security - Shifts", description = "Shift scheduling and lifecycle management")
public class ShiftController {

    private final ShiftService shiftService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List shifts — paginated, sorted by startAt DESC")
    public ResponseEntity<ApiResponse<Page<ShiftResponse>>> getShifts(
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                shiftService.getShifts(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(
            summary = "Schedule a shift",
            description = "Validates: guard is ACTIVE and schedulable, site belongs to this tenant, " +
                    "no overlapping shifts for this guard."
    )
    public ResponseEntity<ApiResponse<ShiftResponse>> createShift(
            @Valid @RequestBody CreateShiftRequest request) {
        featureGuard.requireModule("security");
        var shift = shiftService.createShift(TenantContext.getTenantIdAsObject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Shift scheduled", shift));
    }

    /**
     * Fixes bug #4: PUT /shifts/{id} was missing.
     * ShiftsTab.tsx called this endpoint on every "Edit Shift" save — it silently
     * 404'd in production, meaning the edit modal appeared to work but made no change.
     *
     * Only mutable fields are accepted: notes and endAt (to extend overtime).
     * Guard, site, and startAt cannot be changed — cancel and recreate instead.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Update a shift (notes and/or extend end time)",
            description = "Only notes and endAt can be updated. " +
                    "Extending endAt is checked against other shifts for this guard."
    )
    public ResponseEntity<ApiResponse<ShiftResponse>> updateShift(
            @PathVariable UUID id,
            @RequestBody UpdateShiftRequest request) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Shift updated",
                shiftService.updateShift(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Guard starts shift — SCHEDULED → ACTIVE")
    public ResponseEntity<ApiResponse<ShiftResponse>> startShift(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Shift started",
                shiftService.startShift(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Guard completes shift — ACTIVE → COMPLETED",
            description = "Enforces minimum checkpoint scan count if configured on this shift " +
                    "(minScanCount > 0). Fails with 409 if required scans haven't been recorded."
    )
    public ResponseEntity<ApiResponse<ShiftResponse>> completeShift(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Shift completed",
                shiftService.completeShift(TenantContext.getTenantIdAsObject(), id)));
    }


    // (Same USER_UPDATE gate as updateStatus()/deleteGuard() elsewhere -- these
// are supervisor actions, not guard-facing.)

    @PostMapping("/{id}/dismiss-no-show")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Dismiss a no-show/late alert for a shift",
            description = "Record-keeping only -- does not change the shift's status. " +
                    "Use when the absence is already being handled outside the system " +
                    "(guard called in sick, replacement arranged manually).")
    public ResponseEntity<ApiResponse<ShiftResponse>> dismissNoShow(
            @PathVariable UUID id,
            @Valid @RequestBody ShiftSupervisorActionRequest request) {
        featureGuard.requireModule("security");
        UUID supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("No-show dismissed",
                shiftService.dismissNoShow(TenantContext.getTenantIdAsObject(), id, supervisorId, request)));
    }

    @PostMapping("/{id}/close-overtime")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Force-close a shift running in unconfirmed overtime",
            description = "Completes an ACTIVE shift without the guard clocking out. " +
                    "Bypasses minimum checkpoint scan enforcement. Also force-closes " +
                    "any open device session tied to this shift.")
    public ResponseEntity<ApiResponse<ShiftResponse>> closeOvertime(
            @PathVariable UUID id,
            @Valid @RequestBody ShiftSupervisorActionRequest request) {
        featureGuard.requireModule("security");
        UUID supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Overtime shift closed",
                shiftService.forceCloseOvertime(TenantContext.getTenantIdAsObject(), id, supervisorId, request)));
    }

    @PostMapping("/{id}/pull")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Pull a guard off site mid-shift",
            description = "Supervisor-initiated interrupt of an ACTIVE shift -- client complaint, " +
                    "guard unwell, redeployment, etc. Distinct from a normal end-of-shift " +
                    "completion; a written reason is required and the action is fully audited.")
    public ResponseEntity<ApiResponse<ShiftResponse>> pullFromSite(
            @PathVariable UUID id,
            @Valid @RequestBody ShiftSupervisorActionRequest request) {
        featureGuard.requireModule("security");
        UUID supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Guard pulled from site",
                shiftService.pullFromSite(TenantContext.getTenantIdAsObject(), id, supervisorId, request)));
    }
}
