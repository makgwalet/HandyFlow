package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.*;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
@Tag(name = "HR & Payroll", description = "Employee management, leave, payroll and SARS compliance")
public class HrController {

    private final HrService              hrService;
    private final PayrollService         payrollService;
    private final PayslipPdfGenerator    payslipPdfGenerator;
    private final HrPayslipRepository    payslipRepo;
    private final HrEmployeeRepository   employeeRepo;
    private final HrPayRunRepository     payRunRepo;
    private final HrEmp201Repository     emp201Repo;
    private final Emp201PdfGenerator     emp201PdfGenerator;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final HrEmployeePortalAuthService hrEmployeePortalAuthService;
    private final StatutoryRulesService  statutoryRulesService;
    private final MedicalSchemeService   medicalSchemeService;
    private final PayrollImpactService   payrollImpactService;
    private final PayrollControlService  payrollControlService;
    private final EmployeeViewService   employeeViewService;
    private final EmployeeChangeService employeeChangeService;
    private final EmployeeAuditService  employeeAuditService;
    private final LeaveService          leaveService;

    // ── Employees ─────────────────────────────────────────────────────────────

    @GetMapping("/employees")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','HR_TEAM_READ','PAYROLL_RUN','PAYROLL_PREPARE','PAYROLL_APPROVE','USER_READ')")
    @Operation(summary = "List employees. Personal data and pay are left out unless the caller holds the permission")
    public ResponseEntity<ApiResponse<Page<EmployeeViewResponse>>> getEmployees(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(employeeViewService.list(
                TenantContext.getTenantIdAsObject(), status, search, pageable, HrAccess.held(authentication), resolveUserId(principal))));
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','HR_TEAM_READ','USER_READ')")
    public ResponseEntity<ApiResponse<EmployeeViewResponse>> getEmployee(
            @PathVariable UUID id, Authentication authentication, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(employeeViewService.get(
                TenantContext.getTenantIdAsObject(), id, HrAccess.held(authentication), resolveUserId(principal))));
    }

    @PostMapping("/employees")
    // FIX: was USER_READ — anyone could create employees
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Register a new employee — seeds BCEA leave balances automatically")
    public ResponseEntity<ApiResponse<EmployeeViewResponse>> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest req, Authentication authentication, Principal principal) {
        EmployeeResponse created = hrService.createEmployee(TenantContext.getTenantIdAsObject(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Employee registered",
                employeeViewService.get(TenantContext.getTenantIdAsObject(), created.id(), HrAccess.held(authentication), resolveUserId(principal))));
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Update contact and job details. Pay and bank details change through a request that someone else approves")
    public ResponseEntity<ApiResponse<EmployeeViewResponse>> updateEmployee(
            @PathVariable UUID id, @RequestBody UpdateEmployeeRequest req, Authentication authentication, Principal principal) {
        var held = HrAccess.held(authentication);
        UUID actor = resolveUserId(principal);
        hrService.updateDetails(TenantContext.getTenantIdAsObject(), id, req, HrAccess.sensitive(held), actor);
        return ResponseEntity.ok(ApiResponse.success("Employee updated",
                employeeViewService.get(TenantContext.getTenantIdAsObject(), id, held, actor)));
    }

    @PostMapping("/employees/{id}/terminate")
    // FIX: was USER_READ — anyone could terminate employees
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Terminate an employee — last day worked and a reason are required")
    public ResponseEntity<ApiResponse<EmployeeViewResponse>> terminateEmployee(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String reason, Authentication authentication, Principal principal) {
        UUID actor = resolveUserId(principal);
        hrService.terminateEmployee(TenantContext.getTenantIdAsObject(), id, endDate, reason, actor);
        return ResponseEntity.ok(ApiResponse.success("Employee terminated",
                employeeViewService.get(TenantContext.getTenantIdAsObject(), id, HrAccess.held(authentication), actor)));
    }

    @PutMapping("/employees/{id}/manager")
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Set or remove the manager of an employee. A person cannot report to themselves or to their own team")
    public ResponseEntity<ApiResponse<EmployeeViewResponse>> setManager(
            @PathVariable UUID id, @RequestBody SetManagerRequest req, Authentication authentication, Principal principal) {
        UUID actor = resolveUserId(principal);
        hrService.setManager(TenantContext.getTenantIdAsObject(), id, req.managerId(), actor);
        return ResponseEntity.ok(ApiResponse.success("Manager updated",
                employeeViewService.get(TenantContext.getTenantIdAsObject(), id, HrAccess.held(authentication), actor)));
    }

    @PutMapping("/employees/{id}/user-link")
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Link an employee to a staff login, so a manager sees their own team")
    public ResponseEntity<ApiResponse<EmployeeViewResponse>> linkUser(
            @PathVariable UUID id, @RequestBody LinkUserRequest req, Authentication authentication, Principal principal) {
        UUID actor = resolveUserId(principal);
        hrService.linkUser(TenantContext.getTenantIdAsObject(), id, req.userId(), actor);
        return ResponseEntity.ok(ApiResponse.success("Login link updated",
                employeeViewService.get(TenantContext.getTenantIdAsObject(), id, HrAccess.held(authentication), actor)));
    }

    @GetMapping("/employees/{id}/audit")
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','HR_SENSITIVE_READ','HR_COMPENSATION_APPROVE')")
    @Operation(summary = "What was done to an employee record, and who looked at personal data or pay")
    public ResponseEntity<ApiResponse<List<EmployeeAuditResponse>>> employeeAudit(@PathVariable UUID id) {
        hrService.getEmployee(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(employeeAuditService.history(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Pay and bank changes ──────────────────────────────────────────────────

    @PostMapping("/employees/{id}/changes/compensation")
    @PreAuthorize("hasAuthority('HR_COMPENSATION_MANAGE')")
    @Operation(summary = "Ask for a change to salary, allowances or pay frequency. It takes effect once someone else approves it")
    public ResponseEntity<ApiResponse<EmployeeChangeResponse>> requestCompensation(
            @PathVariable UUID id, @RequestBody CompensationChangeRequest req, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Change requested",
                employeeChangeService.requestCompensation(TenantContext.getTenantIdAsObject(), id, req, resolveUserId(principal))));
    }

    @PostMapping("/employees/{id}/changes/bank")
    @PreAuthorize("hasAuthority('HR_COMPENSATION_MANAGE')")
    @Operation(summary = "Ask for a change to bank details. It takes effect once someone else approves it")
    public ResponseEntity<ApiResponse<EmployeeChangeResponse>> requestBank(
            @PathVariable UUID id, @RequestBody BankChangeRequest req, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Change requested",
                employeeChangeService.requestBank(TenantContext.getTenantIdAsObject(), id, req, resolveUserId(principal))));
    }

    @GetMapping("/employees/{id}/changes")
    @PreAuthorize("hasAnyAuthority('HR_COMPENSATION_READ','HR_COMPENSATION_MANAGE','HR_COMPENSATION_APPROVE')")
    public ResponseEntity<ApiResponse<List<EmployeeChangeResponse>>> employeeChanges(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(employeeChangeService.forEmployee(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/employee-changes/pending")
    @PreAuthorize("hasAnyAuthority('HR_COMPENSATION_READ','HR_COMPENSATION_MANAGE','HR_COMPENSATION_APPROVE')")
    @Operation(summary = "Pay and bank changes waiting for a decision")
    public ResponseEntity<ApiResponse<List<EmployeeChangeResponse>>> pendingChanges() {
        return ResponseEntity.ok(ApiResponse.success(employeeChangeService.pending(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/employee-changes/{changeId}/approve")
    @PreAuthorize("hasAuthority('HR_COMPENSATION_APPROVE')")
    public ResponseEntity<ApiResponse<EmployeeChangeResponse>> approveChange(
            @PathVariable UUID changeId, @RequestBody(required = false) ChangeDecisionRequest req, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Change approved", employeeChangeService.approve(
                TenantContext.getTenantIdAsObject(), changeId, req == null ? null : req.note(), resolveUserId(principal))));
    }

    @PostMapping("/employee-changes/{changeId}/reject")
    @PreAuthorize("hasAuthority('HR_COMPENSATION_APPROVE')")
    public ResponseEntity<ApiResponse<EmployeeChangeResponse>> rejectChange(
            @PathVariable UUID changeId, @RequestBody(required = false) ChangeDecisionRequest req, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Change rejected", employeeChangeService.reject(
                TenantContext.getTenantIdAsObject(), changeId, req == null ? null : req.note(), resolveUserId(principal))));
    }

    @PostMapping("/employee-changes/{changeId}/cancel")
    @PreAuthorize("hasAuthority('HR_COMPENSATION_MANAGE')")
    public ResponseEntity<ApiResponse<EmployeeChangeResponse>> cancelChange(@PathVariable UUID changeId, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Change cancelled", employeeChangeService.cancel(
                TenantContext.getTenantIdAsObject(), changeId, resolveUserId(principal))));
    }

    @GetMapping("/employees/access-review")
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "What the employee-security migration gave each role, and which roles lost sight of personal data or pay")
    public ResponseEntity<ApiResponse<EmployeeAccessReviewResponse>> employeeAccessReview() {
        List<EmployeeAccessReviewResponse.Role> roles = jdbc.query("""
                SELECT role_id, role_name, users_in_role, legacy_held, granted, needs_review, note
                FROM hr_employee_access_review WHERE tenant_id = ? ORDER BY needs_review DESC, role_name""",
                (rs, i) -> new EmployeeAccessReviewResponse.Role((UUID) rs.getObject("role_id"), rs.getString("role_name"),
                        rs.getInt("users_in_role"), rs.getString("legacy_held"), rs.getString("granted"),
                        rs.getBoolean("needs_review"), rs.getString("note")),
                TenantContext.getTenantIdAsObject().getValue());
        return ResponseEntity.ok(ApiResponse.success(new EmployeeAccessReviewResponse(
                (int) roles.stream().filter(EmployeeAccessReviewResponse.Role::needsReview).count(), roles)));
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    @GetMapping("/employees/{id}/leave-balances")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','USER_READ')")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getLeaveBalances(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int year) {
        int leaveYear = year == 0 ? LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.success(
                hrService.getLeaveBalances(TenantContext.getTenantIdAsObject(), id, leaveYear)));
    }

    @GetMapping("/employees/{id}/leave-statement")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','LEAVE_APPROVE','LEAVE_ADJUST')")
    @Operation(summary = "Leave balances for a year and every movement behind them")
    public ResponseEntity<ApiResponse<LeaveStatementResponse>> leaveStatement(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int year) {
        int leaveYear = year == 0 ? LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.success(leaveService.statement(TenantContext.getTenantIdAsObject(), id, leaveYear)));
    }

    @GetMapping("/leave-requests")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','USER_READ','LEAVE_APPROVE')")
    @Operation(summary = "List all leave requests, optionally filter by status")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> getLeaveRequests(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 100) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                hrService.getLeaveRequests(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @PostMapping("/employees/{id}/leave-requests")
    // FIX: was USER_READ
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE','LEAVE_APPROVE')")
    @Operation(summary = "Submit a leave request for an employee")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> submitLeaveRequest(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitLeaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Leave request submitted",
                hrService.submitLeaveRequest(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/leave-requests/{id}/approve")
    // FIX: was USER_READ — anyone could approve leave
    @PreAuthorize("hasAnyAuthority('LEAVE_APPROVE','HR_MANAGE')")
    @Operation(summary = "Approve a leave request. The days held for it become days taken")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approveLeave(
            @PathVariable UUID id, Principal principal, Authentication authentication) {
        // FIX: was passing null as approverId — approval audit trail was broken
        UUID approverId = resolveUserId(principal);
        return ResponseEntity.ok(ApiResponse.success("Leave approved",
                leaveService.approve(TenantContext.getTenantIdAsObject(), id, approverId, HrAccess.held(authentication))));
    }

    @PostMapping("/leave-requests/{id}/reject")
    @PreAuthorize("hasAnyAuthority('LEAVE_APPROVE','HR_MANAGE')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> rejectLeave(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            Principal principal, Authentication authentication) {
        UUID approverId = resolveUserId(principal);
        return ResponseEntity.ok(ApiResponse.success("Leave rejected",
                leaveService.reject(TenantContext.getTenantIdAsObject(), id, approverId, reason, HrAccess.held(authentication))));
    }

    @PostMapping("/leave-requests/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('LEAVE_APPROVE','HR_MANAGE','LEAVE_ADJUST')")
    @Operation(summary = "Cancel a waiting request, or approved leave (leave that has started needs LEAVE_ADJUST)")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> cancelLeave(
            @PathVariable UUID id, @RequestBody(required = false) LeaveCancelRequest req,
            Principal principal, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Leave cancelled", leaveService.cancel(
                TenantContext.getTenantIdAsObject(), id, resolveUserId(principal), req == null ? null : req.reason(), HrAccess.held(authentication))));
    }

    @PostMapping("/employees/{id}/leave-adjustments")
    @PreAuthorize("hasAuthority('LEAVE_ADJUST')")
    @Operation(summary = "Add days to or take days off a leave balance, with a reason")
    public ResponseEntity<ApiResponse<LeaveStatementResponse.Entry>> adjustLeave(
            @PathVariable UUID id, @RequestBody LeaveAdjustmentRequest req, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Adjustment recorded",
                leaveService.adjust(TenantContext.getTenantIdAsObject(), id, req, resolveUserId(principal))));
    }

    // ── Disciplinary ──────────────────────────────────────────────────────────

    @GetMapping("/employees/{id}/disciplinary")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','USER_READ')")
    public ResponseEntity<ApiResponse<List<DisciplinaryResponse>>> getDisciplinary(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                hrService.getDisciplinary(TenantContext.getTenantIdAsObject(), id)));
    }

    // Older entry point. Employee relations (ErController) is where cases are run; this opens a case that has been reported, nothing more.
    @PostMapping("/employees/{id}/disciplinary")
    // FIX: was USER_READ — any read user could issue disciplinary records
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Add a disciplinary record — verbal warning, written warning, NTA, dismissal")
    public ResponseEntity<ApiResponse<DisciplinaryResponse>> addDisciplinary(
            @PathVariable UUID id,
            @Valid @RequestBody AddDisciplinaryRequest req,
            Principal principal) {
        // FIX: was passing null — issuedBy was never recorded
        UUID issuedBy = resolveUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Disciplinary record added",
                hrService.addDisciplinary(TenantContext.getTenantIdAsObject(), id, req, issuedBy)));
    }

    // ── Pay runs ──────────────────────────────────────────────────────────────

    @GetMapping("/pay-runs")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canRead(authentication)")
    public ResponseEntity<ApiResponse<Page<PayRunResponse>>> getPayRuns(
            @PageableDefault(size = 24) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                payrollService.getPayRuns(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/pay-runs")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canPrepare(authentication)")
    @Operation(summary = "Create a new pay run draft for a pay period")
    public ResponseEntity<ApiResponse<PayRunResponse>> createPayRun(
            @Valid @RequestBody CreatePayRunRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Pay run created",
                payrollService.createPayRun(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/pay-runs/{id}/process")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canPrepare(authentication)")
    @Operation(summary = "Calculate a pay run. It then waits for a second person to approve it.")
    public ResponseEntity<ApiResponse<PayRunResponse>> processPayRun(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Pay run calculated",
                payrollService.processPayRun(TenantContext.getTenantIdAsObject(), id, resolveUserId(principal))));
    }

    @GetMapping("/pay-runs/{id}/review")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canReview(authentication)")
    @Operation(summary = "What to check before approving a pay run: errors that block approval and warnings")
    public ResponseEntity<ApiResponse<PayRunReviewResponse>> reviewPayRun(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(payrollControlService.review(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/pay-runs/{id}/approve")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canApprove(authentication)")
    @Operation(summary = "Approve a calculated pay run. The person who calculated it cannot, unless the business allows it.")
    public ResponseEntity<ApiResponse<PayRunResponse>> approvePayRun(
            @PathVariable UUID id, @Valid @RequestBody(required = false) PayRunActionRequest req, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Pay run approved",
                payrollControlService.approve(TenantContext.getTenantIdAsObject(), id, resolveUserId(principal), req == null ? null : req.note())));
    }

    @PostMapping("/pay-runs/{id}/send-back")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canApprove(authentication)")
    @Operation(summary = "Send a calculated or approved pay run back to draft, with a reason")
    public ResponseEntity<ApiResponse<PayRunResponse>> sendBackPayRun(
            @PathVariable UUID id, @Valid @RequestBody(required = false) PayRunActionRequest req, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Pay run sent back",
                payrollControlService.sendBack(TenantContext.getTenantIdAsObject(), id, resolveUserId(principal), req == null ? null : req.note())));
    }

    @PostMapping("/pay-runs/{id}/finalise")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canFinalise(authentication)")
    @Operation(summary = "Finalise and lock an approved pay run. Payslips are then shown to employees.")
    public ResponseEntity<ApiResponse<PayRunResponse>> finalisePayRun(
            @PathVariable UUID id, @Valid @RequestBody(required = false) PayRunActionRequest req, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Pay run finalised",
                payrollControlService.finalise(TenantContext.getTenantIdAsObject(), id, resolveUserId(principal), req == null ? null : req.note())));
    }

    @GetMapping("/pay-runs/{id}/history")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canReview(authentication)")
    @Operation(summary = "Who calculated, approved, sent back and finalised a pay run, and when")
    public ResponseEntity<ApiResponse<List<PayRunEventResponse>>> payRunHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(payrollControlService.events(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/payroll/settings")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canReview(authentication)")
    public ResponseEntity<ApiResponse<PayrollSettingsResponse>> payrollSettings() {
        return ResponseEntity.ok(ApiResponse.success(payrollControlService.settings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/payroll/settings")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canConfigure(authentication)")
    @Operation(summary = "Choose whether one person may calculate and approve the same pay run")
    public ResponseEntity<ApiResponse<PayrollSettingsResponse>> updatePayrollSettings(
            @Valid @RequestBody PayrollSettingsRequest req, Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Payroll settings saved",
                payrollControlService.updateSettings(TenantContext.getTenantIdAsObject(), req.allowSelfApproval(), resolveUserId(principal))));
    }

    @GetMapping("/payroll/access-review")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canConfigure(authentication)")
    @Operation(summary = "What the payroll permissions migration gave each role, and which roles need a decision")
    public ResponseEntity<ApiResponse<PayrollAccessReviewResponse>> payrollAccessReview() {
        return ResponseEntity.ok(ApiResponse.success(payrollControlService.accessReview(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/pay-runs/{id}/payslips")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canRead(authentication)")
    public ResponseEntity<ApiResponse<List<PayslipResponse>>> getPayRunPayslips(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                payrollService.getPayRunPayslips(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/employees/{id}/payslips")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canRead(authentication) or hasAuthority('HR_READ')")
    public ResponseEntity<ApiResponse<List<PayslipResponse>>> getEmployeePayslips(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                payrollService.getEmployeePayslips(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── SARS compliance ───────────────────────────────────────────────────────

    @GetMapping("/emp201")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canRead(authentication)")
    @Operation(summary = "List EMP201 monthly employer declarations")
    public ResponseEntity<ApiResponse<List<Emp201Response>>> getEmp201s() {
        return ResponseEntity.ok(ApiResponse.success(
                payrollService.getEmp201s(TenantContext.getTenantIdAsObject())));
    }

    // ── PDF downloads ─────────────────────────────────────────────────────────

    @GetMapping("/payslips/{id}/pdf")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canRead(authentication) or hasAuthority('HR_READ')")
    @Operation(summary = "Download payslip as PDF")
    public ResponseEntity<byte[]> downloadPayslip(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        HrPayslip  slip = payslipRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip", id.toString()));
        HrEmployee emp  = employeeRepo.findActiveById(tenantId, slip.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", slip.getEmployeeId().toString()));
        HrPayRun   run  = payRunRepo.findByTenantAndId(tenantId, slip.getPayRunId())
                .orElseThrow(() -> new ResourceNotFoundException("PayRun", slip.getPayRunId().toString()));

        String[] tenantDetails = fetchTenantDetails(tenantId.getValue());
        byte[] pdf = payslipPdfGenerator.generate(slip, emp, run, tenantDetails[0], tenantDetails[1]);

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"payslip-"
                        + emp.getEmployeeNumber() + "-" + run.getPayRunNumber() + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/employees/{id}/portal-invite")
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Invite an employee to the self-service portal — emails them a registration link")
    public ResponseEntity<ApiResponse<Void>> invitePortalAccess(
            @PathVariable UUID id,
            @RequestBody(required = false) HrPortalInviteRequest req,
            Principal principal) {
        String inviteEmail = req != null ? req.inviteEmail() : null;
        UUID invitedBy = resolveUserId(principal);
        hrEmployeePortalAuthService.createInvite(
                TenantContext.getTenantIdAsObject(), id, inviteEmail, invitedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Portal invite sent", null));
    }

    @GetMapping("/emp201/{id}/pdf")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canExport(authentication)")
    @Operation(summary = "Download EMP201 monthly declaration as PDF")
    public ResponseEntity<byte[]> downloadEmp201(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        HrEmp201 emp201 = emp201Repo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("EMP201", id.toString()));
        HrPayRun payRun = emp201.getPayRunId() != null
                ? payRunRepo.findByTenantAndId(tenantId, emp201.getPayRunId()).orElse(null)
                : null;
        if (payRun != null && !PayRunWorkflowRules.isIssued(payRun.getStatus()))
            throw new IllegalStateException("The EMP201 can be exported once its pay run has been finalised.");
        int employeeCount = emp201.getPayRunId() != null
                ? emp201Repo.countByPayRunId(emp201.getPayRunId()) : 0;

        String[] tenantDetails = fetchTenantDetails(tenantId.getValue());
        byte[] pdf = emp201PdfGenerator.generate(emp201, payRun, tenantDetails[0], tenantDetails[1], employeeCount);

        String period = emp201.getPeriodEnd()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"EMP201-" + period + ".pdf\"")
                .body(pdf);
    }


    @GetMapping("/org-chart")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','USER_READ')")
    @Operation(summary = "Flat list of active employees with manager links, for rendering an org chart client-side")
    public ResponseEntity<ApiResponse<List<OrgChartNodeResponse>>> getOrgChart() {
        return ResponseEntity.ok(ApiResponse.success(
                hrService.getOrgChart(TenantContext.getTenantIdAsObject())));
    }

    // ── Statutory rules, medical scheme, impact report ────────────────────────
    // These use PAYROLL_* / HR_MANAGE only. The general USER_READ / USER_UPDATE authorities are deliberately not accepted.

    @GetMapping("/statutory/tax-years")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canRead(authentication)")
    @Operation(summary = "Statutory tax years with their rules, source and approval state")
    public ResponseEntity<ApiResponse<List<TaxYearResponse>>> getTaxYears() {
        return ResponseEntity.ok(ApiResponse.success(statutoryRulesService.list()));
    }

    @GetMapping("/payroll/impact-report")
    @PreAuthorize("T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canRead(authentication)")
    @Operation(summary = "Read-only comparison of recorded payslips with a recalculation under the approved rules")
    public ResponseEntity<ApiResponse<PayrollImpactResponse>> getImpactReport(
            @RequestParam(required = false) UUID runId) {
        return ResponseEntity.ok(ApiResponse.success(
                payrollImpactService.report(TenantContext.getTenantIdAsObject(), runId)));
    }

    @GetMapping("/employees/{id}/medical-scheme")
    @PreAuthorize("hasAuthority('HR_MANAGE') or T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canRead(authentication)")
    @Operation(summary = "An employee's medical scheme membership history")
    public ResponseEntity<ApiResponse<List<MedicalSchemeResponse>>> getMedicalScheme(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                medicalSchemeService.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/employees/{id}/medical-scheme")
    @PreAuthorize("hasAuthority('HR_MANAGE') or T(za.co.handyflow.platform.hr.application.internal.PayrollAccess).canPrepare(authentication)")
    @Operation(summary = "Record a new stretch of medical scheme membership; the open one is closed the day before")
    public ResponseEntity<ApiResponse<MedicalSchemeResponse>> addMedicalScheme(
            @PathVariable UUID id, @Valid @RequestBody MedicalSchemeRequest req, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Membership recorded",
                medicalSchemeService.add(TenantContext.getTenantIdAsObject(), id, req, resolveUserId(principal))));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID resolveUserId(Principal principal) {
        if (principal == null) return null;
        try { return UUID.fromString(principal.getName()); }
        catch (Exception e) { return null; }
    }

    private String[] fetchTenantDetails(UUID tenantId) {
        String name = "HandyFlow Tenant", vat = null;
        try {
            var row = jdbc.queryForMap("SELECT name, vat_number FROM tenants WHERE id = ?", tenantId);
            if (row.get("name") != null)       name = row.get("name").toString();
            if (row.get("vat_number") != null) vat  = row.get("vat_number").toString();
        } catch (Exception e) {
            log.warn("Could not fetch tenant details: {}", e.getMessage());
        }
        return new String[]{ name, vat };
    }
}
