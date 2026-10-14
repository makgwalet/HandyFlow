package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.MonthEndRules;
import za.co.handyflow.platform.accounting.application.internal.MonthEndService;
import za.co.handyflow.platform.accounting.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;

/** Finance patch F13: month-end close checklist and the cash flow statement. */
@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
@Tag(name = "Month-end close")
public class MonthEndController {

    private final MonthEndService service;

    @GetMapping("/month-end")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Close checklist for a month end (defaults to last month)")
    public ResponseEntity<ApiResponse<MonthEndChecklistResponse>> checklist(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        LocalDate end = periodEnd != null ? periodEnd : MonthEndRules.defaultPeriodEnd(LocalDate.now());
        return ResponseEntity.ok(ApiResponse.success("Success", service.checklist(TenantContext.getTenantIdAsObject(), end)));
    }

    @PostMapping("/month-end/confirmations")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Confirm a step only a person can vouch for")
    public ResponseEntity<ApiResponse<MonthEndChecklistResponse>> confirm(@RequestBody ConfirmCloseStepRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Step confirmed",
                service.confirm(TenantContext.getTenantIdAsObject(), req.periodEnd(), req.stepKey(), req.note(),
                        TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/month-end/confirmations")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Take back a confirmation")
    public ResponseEntity<ApiResponse<MonthEndChecklistResponse>> unconfirm(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd, @RequestParam String stepKey) {
        return ResponseEntity.ok(ApiResponse.success("Confirmation removed",
                service.unconfirm(TenantContext.getTenantIdAsObject(), periodEnd, stepKey)));
    }

    @PostMapping("/month-end/close")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    @Operation(summary = "Close the month (sets the lock date) once the checklist is clear")
    public ResponseEntity<ApiResponse<PeriodLockResponse>> close(@RequestBody CloseMonthRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Month closed",
                service.close(TenantContext.getTenantIdAsObject(), req.periodEnd(), req.note(),
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/reports/cash-flow")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Cash flow statement (indirect method) for a date range")
    public ResponseEntity<ApiResponse<CashFlowResponse>> cashFlow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.cashFlow(TenantContext.getTenantIdAsObject(), from, to)));
    }
}
