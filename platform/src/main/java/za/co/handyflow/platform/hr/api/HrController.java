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

    // ── Employees ─────────────────────────────────────────────────────────────

    @GetMapping("/employees")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','PAYROLL_RUN','PAYROLL_PREPARE','PAYROLL_APPROVE','USER_READ')")
    @Operation(summary = "List employees with optional status and search filters")
    public ResponseEntity<ApiResponse<Page<EmployeeResponse>>> getEmployees(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                hrService.getEmployees(TenantContext.getTenantIdAsObject(), status, search, pageable)));
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','USER_READ')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getEmployee(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                hrService.getEmployee(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/employees")
    // FIX: was USER_READ — anyone could create employees
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Register a new employee — seeds BCEA leave balances automatically")
    public ResponseEntity<ApiResponse<EmployeeResponse>> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Employee registered",
                hrService.createEmployee(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Update employee details — salary, department, contact info")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody CreateEmployeeRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Employee updated",
                hrService.updateEmployee(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/employees/{id}/terminate")
    // FIX: was USER_READ — anyone could terminate employees
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Terminate an employee — sets end date and status to TERMINATED")
    public ResponseEntity<ApiResponse<EmployeeResponse>> terminateEmployee(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success("Employee terminated",
                hrService.terminateEmployee(TenantContext.getTenantIdAsObject(), id, endDate)));
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

    @GetMapping("/leave-requests")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','USER_READ')")
    @Operation(summary = "List all leave requests, optionally filter by status")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> getLeaveRequests(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 100) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                hrService.getLeaveRequests(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @PostMapping("/employees/{id}/leave-requests")
    // FIX: was USER_READ
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Submit a leave request for an employee")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> submitLeaveRequest(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitLeaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Leave request submitted",
                hrService.submitLeaveRequest(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/leave-requests/{id}/approve")
    // FIX: was USER_READ — anyone could approve leave
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Approve a leave request — deducts from employee's balance")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approveLeave(
            @PathVariable UUID id, Principal principal) {
        // FIX: was passing null as approverId — approval audit trail was broken
        UUID approverId = resolveUserId(principal);
        return ResponseEntity.ok(ApiResponse.success("Leave approved",
                hrService.approveLeaveRequest(TenantContext.getTenantIdAsObject(), id, approverId)));
    }

    @PostMapping("/leave-requests/{id}/reject")
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> rejectLeave(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            Principal principal) {
        UUID approverId = resolveUserId(principal);
        return ResponseEntity.ok(ApiResponse.success("Leave rejected",
                hrService.rejectLeaveRequest(TenantContext.getTenantIdAsObject(), id, approverId, reason)));
    }

    // ── Disciplinary ──────────────────────────────────────────────────────────

    @GetMapping("/employees/{id}/disciplinary")
    @PreAuthorize("hasAnyAuthority('HR_READ','HR_MANAGE','USER_READ')")
    public ResponseEntity<ApiResponse<List<DisciplinaryResponse>>> getDisciplinary(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                hrService.getDisciplinary(TenantContext.getTenantIdAsObject(), id)));
    }

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


    @PostMapping("/employees/{id}/disciplinary/{disciplinaryId}/outcome")
    @PreAuthorize("hasAnyAuthority('HR_MANAGE','USER_UPDATE')")
    @Operation(summary = "Record the outcome of a disciplinary hearing — verbal/written/final warning or dismissal")
    public ResponseEntity<ApiResponse<DisciplinaryResponse>> recordDisciplinaryOutcome(
            @PathVariable UUID id,
            @PathVariable UUID disciplinaryId,
            @Valid @RequestBody RecordDisciplinaryOutcomeRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Outcome recorded",
                hrService.recordDisciplinaryOutcome(TenantContext.getTenantIdAsObject(), id, disciplinaryId, req)));
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
