package za.co.handyflow.platform.hr.api;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.HrEmployeePortalDataService;
import za.co.handyflow.platform.hr.application.internal.HrEmployeePortalAuthService;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * FIX: backlog 3.4 — "employee self-service reality unconfirmed." Closes
 * the confirmed gap: employees previously had zero way to log in and see
 * their own payslip or leave balance.
 * <p>
 * /auth/register and /auth/login mirror
 * accountant.AccountantPortalAuthController's confirmed-real shape
 * exactly — same sub-path convention (only /auth/** is public, see
 * SecurityConfig's permitAll() list patch), same PortalJwtFilter-based
 * protection for everything else (that filter matches on any path
 * containing "/portal/", confirmed generic — no filter changes needed
 * for a new module to get portal auth for free).
 * <p>
 * Every data endpoint below takes {employeeId} as a path variable rather
 * than inferring "the employee this portal user is" implicitly — this
 * mirrors payrollbureau's own portal shape (client ID is always
 * explicit, e.g. getMyFeeNotes(portalUserId, clientId, ...)), and matters
 * more here than there for one reason worth stating: a single PortalUser
 * could in principle hold grants for more than one employee record over
 * a working life (e.g. rehired after a break, or an edge case this
 * schema doesn't prevent), so "my one implicit employee" isn't a safe
 * assumption to bake into the URL shape. Every access is still fully
 * checked against the grant regardless of what's in the URL —
 * requireAccess() in the data service is what actually enforces this,
 * not the path shape.
 */
@RestController
@RequestMapping("/api/v1/hr/portal")
@RequiredArgsConstructor
@Tag(name = "HR Employee Self-Service Portal", description = "Employee-facing payslip/leave-balance/leave-request access")
public class HrEmployeePortalController {

    private final HrEmployeePortalAuthService portalAuthService;
    private final HrEmployeePortalDataService portalDataService;

    // ── Auth ──────────────────────────────────────────────────────────────────

    @PostMapping("/auth/register")
    @Operation(summary = "Register a portal account via an invite token")
    public ResponseEntity<ApiResponse<HrPortalAuthResponse>> register(@Valid @RequestBody HrPortalRegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                portalAuthService.registerViaInvite(req.inviteToken(), req.password(), req.fullName())));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in to the employee self-service portal")
    public ResponseEntity<ApiResponse<HrPortalAuthResponse>> login(@Valid @RequestBody HrPortalLoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Logged in",
                portalAuthService.login(req.email(), req.password())));
    }

    // ── Self-service data ─────────────────────────────────────────────────────

    @GetMapping("/employees/{employeeId}/profile")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "View my own employee profile")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getMyProfile(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyProfile(getPortalUserId(), employeeId)));
    }

    @GetMapping("/employees/{employeeId}/payslips")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "View my own payslips")
    public ResponseEntity<ApiResponse<List<PayslipResponse>>> getMyPayslips(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyPayslips(getPortalUserId(), employeeId)));
    }

    @GetMapping("/employees/{employeeId}/leave-balances")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "View my own leave balances")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getMyLeaveBalances(
            @PathVariable UUID employeeId,
            @RequestParam(defaultValue = "0") int year) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyLeaveBalances(getPortalUserId(), employeeId, year)));
    }

    @GetMapping("/employees/{employeeId}/leave-requests")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "View my own leave request history")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> getMyLeaveRequests(
            @PathVariable UUID employeeId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyLeaveRequests(getPortalUserId(), employeeId, pageable)));
    }

    @PostMapping("/employees/{employeeId}/leave-requests")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Submit a leave request for myself — routes to my manager for approval, same as HR-submitted requests")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> submitMyLeaveRequest(
            @PathVariable UUID employeeId,
            @Valid @RequestBody SubmitLeaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Leave request submitted",
                portalDataService.submitMyLeaveRequest(getPortalUserId(), employeeId, req)));
    }

    /**
     * PortalJwtFilter stores the portal user's ID (UUID string) as the
     * Authentication principal — confirmed identical pattern to
     * AccountantPortalAuthController's own getPortalUserId() helper.
     */
    private UUID getPortalUserId() {
        return UUID.fromString((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }
}