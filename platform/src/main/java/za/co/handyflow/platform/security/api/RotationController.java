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


    /** Optional so a controller built by hand still works; in the application it is always there. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** Only a user with access to the whole company may use this: the records are not tied to one site. */
    private void wholeCompany() {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    private void child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind kind, UUID id) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkChild(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), kind, id);
    }

    private java.util.Set<UUID> allowed() {
        return za.co.handyflow.platform.security.application.internal.SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    // ── Patterns ───────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all active rotation patterns for this tenant")
    public ResponseEntity<ApiResponse<Page<RotationPatternResponse>>> getPatterns(Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                rotationService.getPatterns(tenantId, pageable, allowed())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Create a new rotation pattern for a site")
    public ResponseEntity<ApiResponse<RotationPatternResponse>> createPattern(
            @Valid @RequestBody CreateRotationPatternRequest req) {
        site(req.siteId());
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
        child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind.ROTATION_PATTERN, id);
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                rotationService.updatePattern(tenantId, id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Deactivate a rotation pattern (does not delete generated shifts)")
    public ResponseEntity<ApiResponse<Void>> deactivatePattern(@PathVariable UUID id) {
        child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind.ROTATION_PATTERN, id);
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
        child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind.ROTATION_PATTERN, req.patternId());
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(rotationService.assignGuard(tenantId, req)));
    }

    // FIX (P1 backlog): getAssignmentsForPattern's own service-method
    // comment has the fuller context — this endpoint didn't exist at
    // all before, meaning an assignment could be created but never
    // discovered again in order to end it.
    @GetMapping("/{patternId}/assignments")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Active guard assignments for a rotation pattern")
    public ResponseEntity<ApiResponse<java.util.List<RotationAssignmentResponse>>> getAssignmentsForPattern(
            @PathVariable UUID patternId) {
        child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind.ROTATION_PATTERN, patternId);
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                rotationService.getAssignmentsForPattern(tenantId, patternId)));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "End a guard's rotation assignment on the specified date")
    public ResponseEntity<ApiResponse<Void>> endAssignment(
            @PathVariable UUID assignmentId,
            @RequestParam LocalDate endsAt) {
        child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind.ROTATION_ASSIGNMENT, assignmentId);
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
        child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind.ROTATION_PATTERN, req.patternId());
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                rotationService.generateSchedule(tenantId, req)));
    }
}