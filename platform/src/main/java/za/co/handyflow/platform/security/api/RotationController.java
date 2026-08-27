// security/api/RotationController.java

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
import za.co.handyflow.platform.security.application.internal.RotationService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Security - Rotation Schedules")
@RestController
@RequestMapping("/api/v1/security/rotations")
@RequiredArgsConstructor
public class RotationController {

    private final RotationService rotationService;

    // ── Patterns ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all active rotation patterns for this tenant")
    public ResponseEntity<ApiResponse<Page<RotationPatternResponse>>> getPatterns(Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                rotationService.getPatterns(tenantId, pageable)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Create a new rotation pattern for a site")
    public ResponseEntity<ApiResponse<RotationPatternResponse>> createPattern(
            @Valid @RequestBody CreateRotationPatternRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(rotationService.createPattern(tenantId, req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Update rotation pattern name, cycle definition, or shift length")
    public ResponseEntity<ApiResponse<RotationPatternResponse>> updatePattern(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRotationPatternRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                rotationService.updatePattern(tenantId, id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Deactivate a rotation pattern (does not delete generated shifts)")
    public ResponseEntity<ApiResponse<Void>> deactivatePattern(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        rotationService.deactivatePattern(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Guard Assignments ──────────────────────────────────────────────────────

    @PostMapping("/assignments")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Assign a guard to a rotation pattern",
            description = "Automatically ends the guard's previous open-ended assignment.")
    public ResponseEntity<ApiResponse<RotationAssignmentResponse>> assignGuard(
            @Valid @RequestBody CreateRotationAssignmentRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(rotationService.assignGuard(tenantId, req)));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "End a guard's rotation assignment on the specified date")
    public ResponseEntity<ApiResponse<Void>> endAssignment(
            @PathVariable UUID assignmentId,
            @RequestParam LocalDate endsAt) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        rotationService.endAssignment(tenantId, assignmentId, endsAt);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Schedule Generation ────────────────────────────────────────────────────

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Generate shifts from a rotation pattern over a date range",
            description = """
               Materialises Shift rows for all guards assigned to the pattern.
               Idempotent — existing shifts are skipped.
               Maximum window: 90 days.
               Returns a summary of shifts created and any guards skipped
               (expired PSiRA, non-ACTIVE status, overlap).
               """)
    public ResponseEntity<ApiResponse<GenerateScheduleResponse>> generateSchedule(
            @Valid @RequestBody GenerateScheduleRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                rotationService.generateSchedule(tenantId, req)));
    }
}