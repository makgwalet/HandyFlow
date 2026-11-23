// security/api/ShiftController.java

package za.co.handyflow.platform.security.api;

import za.co.handyflow.platform.security.application.internal.SiteScopeService;
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

/**
 * FIX: backlog 1.7 — the third Security sibling flagged during
 * GuardController's own fix, now closed out. Same tiers as
 * GuardController/SiteController: SECURITY_READ, SECURITY_MANAGE for
 * every write here (no delete endpoint in this controller, so no
 * SECURITY_ADMIN tier applies). No new permission migration needed.
 */
@RestController
@RequestMapping("/api/v1/security/shifts")
@RequiredArgsConstructor
@Tag(name = "Security - Shifts", description = "Shift scheduling and lifecycle management")
public class ShiftController {
    /** Optional so a hand-built controller still works; in the application it is always there. Limits a user to the sites in their scope. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.security.application.internal.SiteScopeService scope;

    /** A site outside the user's scope answers exactly like a site that does not exist. */
    private void site(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.check(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** For a list whose site is optional: a user limited to some sites must name one of them, the whole company may leave it out. */
    private void choice(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkChoice(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    /** For a view that spans every site and cannot be narrowed yet: only a user with the whole company may use it. */
    private void wholeCompany() {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkUnrestricted(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    /** The sites the user may see, or null for the whole company. */
    private java.util.Set<UUID> allowed() {
        return SiteScopeService.allowedSites(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
    }

    /** A record that belongs to a site: one at a site outside the user's scope answers "not found". */
    private void child(za.co.handyflow.platform.security.application.internal.SiteScopeService.Kind kind, UUID id) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkChild(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), kind, id);
    }

    /** For a site that is optional in the request: leaving it out means the whole company. */
    private void optionalSite(UUID siteId) {
        za.co.handyflow.platform.security.application.internal.SiteScopeService.checkOptionalSite(scope, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), siteId);
    }

    private final ShiftService shiftService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List shifts — paginated, sorted by startAt DESC")
    public ResponseEntity<ApiResponse<Page<ShiftResponse>>> getShifts(
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("security");
        java.util.Set<UUID> allowed = allowed();
        return ResponseEntity.ok(ApiResponse.success(
                shiftService.getShifts(TenantContext.getTenantIdAsObject(), pageable, allowed)));
    }

    @GetMapping("/range")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Shifts starting in [from, to), at most 35 days; used by the scheduler grid")
    public ResponseEntity<ApiResponse<java.util.List<ShiftResponse>>> getRange(
            @RequestParam java.time.Instant from, @RequestParam java.time.Instant to) {
        featureGuard.requireModule("security");
        java.util.Set<UUID> allowed = allowed();
        return ResponseEntity.ok(ApiResponse.success(
                shiftService.getRange(TenantContext.getTenantIdAsObject(), from, to, allowed)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Schedule a shift",
            description = "Validates: guard is ACTIVE and schedulable, site belongs to this tenant, " +
                    "no overlapping shifts for this guard."
    )
    public ResponseEntity<ApiResponse<ShiftResponse>> createShift(
            @Valid @RequestBody CreateShiftRequest request) {
        featureGuard.requireModule("security");
        site(request.siteId());
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
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Update a shift (notes and/or extend end time)",
            description = "Only notes and endAt can be updated. " +
                    "Extending endAt is checked against other shifts for this guard."
    )
    public ResponseEntity<ApiResponse<ShiftResponse>> updateShift(
            @PathVariable UUID id,
            @RequestBody UpdateShiftRequest request) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.SHIFT, id);
        return ResponseEntity.ok(ApiResponse.success("Shift updated",
                shiftService.updateShift(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Guard starts shift — SCHEDULED → ACTIVE")
    public ResponseEntity<ApiResponse<ShiftResponse>> startShift(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.SHIFT, id);
        return ResponseEntity.ok(ApiResponse.success("Shift started",
                shiftService.startShift(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Guard completes shift — ACTIVE → COMPLETED",
            description = "Enforces minimum checkpoint scan count if configured on this shift " +
                    "(minScanCount > 0). Fails with 409 if required scans haven't been recorded."
    )
    public ResponseEntity<ApiResponse<ShiftResponse>> completeShift(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.SHIFT, id);
        return ResponseEntity.ok(ApiResponse.success("Shift completed",
                shiftService.completeShift(TenantContext.getTenantIdAsObject(), id)));
    }

    // (Same SECURITY_MANAGE gate as updateStatus()/deleteGuard() elsewhere -- these
    // are supervisor actions, not guard-facing.)

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Cancel a shift that has not started",
            description = "SCHEDULED shifts only (409 otherwise). A written reason is required and audited; " +
                    "the guard is notified.")
    public ResponseEntity<ApiResponse<ShiftResponse>> cancelShift(
            @PathVariable UUID id,
            @Valid @RequestBody ShiftSupervisorActionRequest request) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.SHIFT, id);
        UUID supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Shift cancelled",
                shiftService.cancelShift(TenantContext.getTenantIdAsObject(), id, supervisorId, request)));
    }

    @PostMapping("/{id}/dismiss-no-show")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Dismiss a no-show/late alert for a shift",
            description = "Record-keeping only -- does not change the shift's status. " +
                    "Use when the absence is already being handled outside the system " +
                    "(guard called in sick, replacement arranged manually).")
    public ResponseEntity<ApiResponse<ShiftResponse>> dismissNoShow(
            @PathVariable UUID id,
            @Valid @RequestBody ShiftSupervisorActionRequest request) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.SHIFT, id);
        UUID supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("No-show dismissed",
                shiftService.dismissNoShow(TenantContext.getTenantIdAsObject(), id, supervisorId, request)));
    }

    @PostMapping("/{id}/close-overtime")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Force-close a shift running in unconfirmed overtime",
            description = "Completes an ACTIVE shift without the guard clocking out. " +
                    "Bypasses minimum checkpoint scan enforcement. Also force-closes " +
                    "any open device session tied to this shift.")
    public ResponseEntity<ApiResponse<ShiftResponse>> closeOvertime(
            @PathVariable UUID id,
            @Valid @RequestBody ShiftSupervisorActionRequest request) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.SHIFT, id);
        UUID supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Overtime shift closed",
                shiftService.forceCloseOvertime(TenantContext.getTenantIdAsObject(), id, supervisorId, request)));
    }

    @PostMapping("/{id}/pull")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Pull a guard off site mid-shift",
            description = "Supervisor-initiated interrupt of an ACTIVE shift -- client complaint, " +
                    "guard unwell, redeployment, etc. Distinct from a normal end-of-shift " +
                    "completion; a written reason is required and the action is fully audited.")
    public ResponseEntity<ApiResponse<ShiftResponse>> pullFromSite(
            @PathVariable UUID id,
            @Valid @RequestBody ShiftSupervisorActionRequest request) {
        featureGuard.requireModule("security");
        child(SiteScopeService.Kind.SHIFT, id);
        UUID supervisorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Guard pulled from site",
                shiftService.pullFromSite(TenantContext.getTenantIdAsObject(), id, supervisorId, request)));
    }
}