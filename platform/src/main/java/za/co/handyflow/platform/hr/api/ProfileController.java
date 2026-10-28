package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.CareerService;
import za.co.handyflow.platform.hr.application.internal.EmployeeAuditService;
import za.co.handyflow.platform.hr.application.internal.HrAccess;
import za.co.handyflow.platform.hr.application.internal.RelativeService;
import za.co.handyflow.platform.hr.dto.ProfileDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The employee profile: next of kin and dependants, and the career timeline. HR needs access to personal data for the family and
 * HR read access for the career (pay lines also need pay access). An employee sees and keeps their own through the "mine" calls.
 */
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
@Tag(name = "Employee profile", description = "Next of kin, dependants and career history")
public class ProfileController {

    private static final String WRITE = "hasAnyAuthority('HR_MANAGE','USER_UPDATE')";

    private final RelativeService relatives;
    private final CareerService career;
    private final EmployeeAuditService audit;

    // ── Family, kept by HR ────────────────────────────────────────────────────────────────────────

    @GetMapping("/employees/{id}/relatives")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "An employee's next of kin and dependants")
    public ResponseEntity<ApiResponse<List<RelativeView>>> list(@PathVariable UUID id, Authentication authentication, Principal principal) {
        requireSensitive(authentication);
        audit.recordViewOnce(TenantContext.getTenantIdAsObject(), id, "VIEWED_FAMILY", user(principal));
        return ResponseEntity.ok(ApiResponse.success(relatives.forEmployee(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/employees/{id}/relatives")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<RelativeView>> add(@PathVariable UUID id, @RequestBody RelativeRequest req, Authentication authentication, Principal principal) {
        requireSensitive(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Saved",
                relatives.add(TenantContext.getTenantIdAsObject(), id, req, user(principal))));
    }

    @PutMapping("/employees/{id}/relatives/{relativeId}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<RelativeView>> update(@PathVariable UUID id, @PathVariable UUID relativeId, @RequestBody RelativeRequest req,
                                                            Authentication authentication, Principal principal) {
        requireSensitive(authentication);
        return ResponseEntity.ok(ApiResponse.success("Saved", relatives.update(TenantContext.getTenantIdAsObject(), id, relativeId, req, user(principal))));
    }

    @DeleteMapping("/employees/{id}/relatives/{relativeId}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id, @PathVariable UUID relativeId, Authentication authentication, Principal principal) {
        requireSensitive(authentication);
        relatives.remove(TenantContext.getTenantIdAsObject(), id, relativeId, user(principal));
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }

    // ── Family, kept by the employee ──────────────────────────────────────────────────────────────

    @GetMapping("/profile/relatives")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My own next of kin and dependants")
    public ResponseEntity<ApiResponse<List<RelativeView>>> mine(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(relatives.forEmployee(TenantContext.getTenantIdAsObject(), me(principal))));
    }

    @PostMapping("/profile/relatives")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RelativeView>> addMine(@RequestBody RelativeRequest req, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Saved",
                relatives.add(TenantContext.getTenantIdAsObject(), me(principal), req, user(principal))));
    }

    @PutMapping("/profile/relatives/{relativeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RelativeView>> updateMine(@PathVariable UUID relativeId, @RequestBody RelativeRequest req, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Saved", relatives.update(TenantContext.getTenantIdAsObject(), me(principal), relativeId, req, user(principal))));
    }

    @DeleteMapping("/profile/relatives/{relativeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeMine(@PathVariable UUID relativeId, Principal principal) {
        relatives.remove(TenantContext.getTenantIdAsObject(), me(principal), relativeId, user(principal));
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }

    // ── Career ────────────────────────────────────────────────────────────────────────────────────

    @GetMapping("/employees/{id}/career")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "An employee's career timeline. Salary lines are left out unless the caller may see pay")
    public ResponseEntity<ApiResponse<List<CareerView>>> careerOf(@PathVariable UUID id, Authentication authentication) {
        Set<String> held = HrAccess.held(authentication);
        if (!HrAccess.seesAll(held)) throw new AccessDeniedException("You may not see employee career records.");
        return ResponseEntity.ok(ApiResponse.success(career.forEmployee(TenantContext.getTenantIdAsObject(), id, HrAccess.compensation(held))));
    }

    @PostMapping("/employees/{id}/career")
    @PreAuthorize(WRITE)
    @Operation(summary = "Add an earlier employer, a promotion, a transfer or another note to the timeline")
    public ResponseEntity<ApiResponse<CareerView>> addCareer(@PathVariable UUID id, @RequestBody CareerRequest req, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Added", career.add(TenantContext.getTenantIdAsObject(), id, req, user(principal))));
    }

    @DeleteMapping("/career/{eventId}")
    @PreAuthorize(WRITE)
    @Operation(summary = "Remove a timeline line that was added by hand")
    public ResponseEntity<ApiResponse<Void>> removeCareer(@PathVariable UUID eventId, Principal principal) {
        career.remove(TenantContext.getTenantIdAsObject(), eventId, user(principal));
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }

    @GetMapping("/profile/career")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My own career timeline, without salary lines")
    public ResponseEntity<ApiResponse<List<CareerView>>> careerMine(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(career.forEmployee(TenantContext.getTenantIdAsObject(), me(principal), false)));
    }

    private UUID me(Principal principal) {
        return relatives.employeeOf(TenantContext.getTenantIdAsObject(), user(principal))
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "your login is not linked to an employee record"));
    }

    private static void requireSensitive(Authentication authentication) {
        if (!HrAccess.sensitive(HrAccess.held(authentication))) throw new AccessDeniedException("You may not see or change an employee's family details.");
    }

    private static UUID user(Principal principal) {
        if (principal == null) return null;
        try { return UUID.fromString(principal.getName()); } catch (Exception e) { return null; }
    }
}
