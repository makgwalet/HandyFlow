package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.Emp201PdfGenerator;
import za.co.handyflow.platform.hr.application.internal.HrService;
import za.co.handyflow.platform.hr.application.internal.PayrollService;
import za.co.handyflow.platform.hr.application.internal.PayslipPdfGenerator;
import za.co.handyflow.platform.hr.domain.model.HrEmp201;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.hr.domain.model.HrPayslip;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Slf4j
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
@Tag(name = "HR & Payroll", description = "Employee management, leave, payroll and SARS compliance")
public class HrController {

    private final HrService hrService;
    private final PayrollService payrollService;
    private final PayslipPdfGenerator payslipPdfGenerator;
    private final za.co.handyflow.platform.hr.domain.repository.HrPayslipRepository payslipRepo;
    private final za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository employeeRepo;
    private final za.co.handyflow.platform.hr.domain.repository.HrPayRunRepository payRunRepo;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final Emp201PdfGenerator emp201PdfGenerator;
    private final za.co.handyflow.platform.hr.domain.repository.HrEmp201Repository emp201Repo;


    // ── Employees ─────────────────────────────────────────────────────────────

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List employees with optional status and search filters")
    public ResponseEntity<ApiResponse<Page<EmployeeResponse>>> getEmployees(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                hrService.getEmployees(TenantContext.getTenantIdAsObject(),
                        status, search, pageable)));
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get employee detail")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getEmployee(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                hrService.getEmployee(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Register a new employee (seeds BCEA leave balances automatically)")
    public ResponseEntity<ApiResponse<EmployeeResponse>> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Employee registered",
                hrService.createEmployee(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/employees/{id}/terminate")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Terminate an employee")
    public ResponseEntity<ApiResponse<EmployeeResponse>> terminateEmployee(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("Employee terminated",
                hrService.terminateEmployee(TenantContext.getTenantIdAsObject(), id, endDate)));
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    @GetMapping("/employees/{id}/leave-balances")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get leave balances for an employee")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getLeaveBalances(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int year) {
        int leaveYear = year == 0 ? LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.success("Success",
                hrService.getLeaveBalances(TenantContext.getTenantIdAsObject(), id, leaveYear)));
    }

    @GetMapping("/leave-requests")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all leave requests, optionally filter by status")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> getLeaveRequests(
            @RequestParam(required = false) String status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                hrService.getLeaveRequests(TenantContext.getTenantIdAsObject(),
                        status, pageable)));
    }

    @PostMapping("/employees/{id}/leave-requests")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Submit a leave request for an employee")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> submitLeaveRequest(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitLeaveRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Leave request submitted",
                hrService.submitLeaveRequest(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/leave-requests/{id}/approve")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Approve a leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approveLeave(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Leave approved",
                hrService.approveLeaveRequest(TenantContext.getTenantIdAsObject(),
                        id, null)));
    }

    @PostMapping("/leave-requests/{id}/reject")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Reject a leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> rejectLeave(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success("Leave rejected",
                hrService.rejectLeaveRequest(TenantContext.getTenantIdAsObject(),
                        id, null, reason)));
    }

    // ── Disciplinary ──────────────────────────────────────────────────────────

    @GetMapping("/employees/{id}/disciplinary")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get disciplinary records for an employee")
    public ResponseEntity<ApiResponse<List<DisciplinaryResponse>>> getDisciplinary(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                hrService.getDisciplinary(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/employees/{id}/disciplinary")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Add a disciplinary record — verbal warning, written warning, etc.")
    public ResponseEntity<ApiResponse<DisciplinaryResponse>> addDisciplinary(
            @PathVariable UUID id,
            @Valid @RequestBody AddDisciplinaryRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Disciplinary record added",
                hrService.addDisciplinary(TenantContext.getTenantIdAsObject(),
                        id, req, null)));
    }

    // ── Pay runs ──────────────────────────────────────────────────────────────

    @GetMapping("/pay-runs")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List pay runs")
    public ResponseEntity<ApiResponse<Page<PayRunResponse>>> getPayRuns(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                payrollService.getPayRuns(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @PostMapping("/pay-runs")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Create a new pay run for a period")
    public ResponseEntity<ApiResponse<PayRunResponse>> createPayRun(
            @Valid @RequestBody CreatePayRunRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Pay run created",
                payrollService.createPayRun(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/pay-runs/{id}/process")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Process pay run — calculates PAYE, UIF, SDL for all active employees")
    public ResponseEntity<ApiResponse<PayRunResponse>> processPayRun(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Pay run processed",
                payrollService.processPayRun(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/pay-runs/{id}/payslips")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all payslips in a pay run")
    public ResponseEntity<ApiResponse<List<PayslipResponse>>> getPayRunPayslips(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                payrollService.getPayRunPayslips(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/employees/{id}/payslips")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get payslip history for an employee")
    public ResponseEntity<ApiResponse<List<PayslipResponse>>> getEmployeePayslips(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                payrollService.getEmployeePayslips(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── SARS compliance ───────────────────────────────────────────────────────

    @GetMapping("/emp201")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List EMP201 monthly employer declarations")
    public ResponseEntity<ApiResponse<List<Emp201Response>>> getEmp201s() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                payrollService.getEmp201s(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/payslips/{id}/pdf")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Download payslip as PDF")
    public ResponseEntity<byte[]> downloadPayslip(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();

        HrPayslip slip = payslipRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip", id.toString()));
        HrEmployee emp = employeeRepo.findActiveById(tenantId, slip.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee",
                        slip.getEmployeeId().toString()));
        HrPayRun run = payRunRepo.findByTenantAndId(tenantId, slip.getPayRunId())
                .orElseThrow(() -> new ResourceNotFoundException("PayRun",
                        slip.getPayRunId().toString()));

        // Fetch tenant name and VAT directly — avoids cross-module Facade dependency
        String tenantName = "HandyFlow Tenant";
        String tenantVat  = null;
        try {
            var row = jdbc.queryForMap(
                    "SELECT name, vat_number FROM tenants WHERE id = ?",
                    tenantId.getValue());
            if (row.get("name") != null)
                tenantName = row.get("name").toString();
            if (row.get("vat_number") != null)
                tenantVat = row.get("vat_number").toString();
        } catch (Exception e) {
            log.warn("Could not fetch tenant details: {}", e.getMessage());
        }

        byte[] pdf = payslipPdfGenerator.generate(slip, emp, run, tenantName, tenantVat);

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition",
                        "attachment; filename=\"payslip-" + emp.getEmployeeNumber() +
                                "-" + run.getPayRunNumber() + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/emp201/{id}/pdf")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Download EMP201 monthly declaration as PDF")
    public ResponseEntity<byte[]> downloadEmp201(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();

        HrEmp201 emp201 = emp201Repo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("EMP201", id.toString()));

        HrPayRun payRun = null;
        if (emp201.getPayRunId() != null) {
            payRun = payRunRepo.findByTenantAndId(tenantId, emp201.getPayRunId())
                    .orElse(null);
        }

        // Count employees in the pay run for the PDF header
        int employeeCount = emp201.getPayRunId() != null
                ? emp201Repo.countByPayRunId(emp201.getPayRunId())
                : 0;

        // Fetch tenant name and VAT — same pattern as payslip PDF
        String tenantName = "HandyFlow Tenant";
        String tenantVat  = null;
        try {
            var row = jdbc.queryForMap(
                    "SELECT name, vat_number FROM tenants WHERE id = ?",
                    tenantId.getValue());
            if (row.get("name") != null)       tenantName = row.get("name").toString();
            if (row.get("vat_number") != null)  tenantVat  = row.get("vat_number").toString();
        } catch (Exception e) {
            log.warn("Could not fetch tenant details for EMP201 PDF: {}", e.getMessage());
        }

        byte[] pdf = emp201PdfGenerator.generate(
                emp201, payRun, tenantName, tenantVat, employeeCount);

        String period = emp201.getPeriodEnd()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));

        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition",
                        "attachment; filename=\"EMP201-" + period + ".pdf\"")
                .body(pdf);
    }
}