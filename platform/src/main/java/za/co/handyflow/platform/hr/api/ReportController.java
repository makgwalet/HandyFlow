package za.co.handyflow.platform.hr.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.hr.application.internal.HrAccess;
import za.co.handyflow.platform.hr.application.internal.ReportService;
import za.co.handyflow.platform.hr.dto.ReportDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

/** The HR reports. Opening them needs HR_REPORT_READ; what each shows is limited further by the rights the person holds. */
@RestController
@RequestMapping("/api/v1/hr/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('HR_REPORT_READ')")
@Tag(name = "HR reports", description = "Workforce, time and leave, payroll cost, employee relations and compliance")
public class ReportController {

    private final ReportService reports;

    @GetMapping("/workforce")
    @Operation(summary = "Headcount, turnover and who the workforce is")
    public ResponseEntity<ApiResponse<Workforce>> workforce(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(reports.workforce(TenantContext.getTenantIdAsObject(), from, to, HrAccess.held(auth))));
    }

    @GetMapping("/time")
    @Operation(summary = "Leave, attendance and overtime")
    public ResponseEntity<ApiResponse<TimeReport>> time(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(reports.time(TenantContext.getTenantIdAsObject(), from, to, HrAccess.held(auth), user(auth))));
    }

    @GetMapping("/payroll")
    @Operation(summary = "Payroll cost and statutory totals")
    public ResponseEntity<ApiResponse<PayrollReport>> payroll(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(reports.payroll(TenantContext.getTenantIdAsObject(), from, to, HrAccess.held(auth))));
    }

    @GetMapping("/compliance")
    @Operation(summary = "Employee relations and compliance")
    public ResponseEntity<ApiResponse<Compliance>> compliance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(reports.compliance(TenantContext.getTenantIdAsObject(), from, to, HrAccess.held(auth))));
    }

    private static UUID user(Authentication auth) {
        try { return UUID.fromString(auth.getName()); } catch (RuntimeException e) { return null; }
    }
}
