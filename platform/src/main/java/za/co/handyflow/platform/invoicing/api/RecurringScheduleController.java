package za.co.handyflow.platform.invoicing.api;

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
import za.co.handyflow.platform.invoicing.application.internal.RecurringScheduleService;
import za.co.handyflow.platform.invoicing.application.internal.InvoiceService;
import za.co.handyflow.platform.invoicing.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoicing")
@RequiredArgsConstructor
@Tag(name = "Invoicing - Recurring & Retainers", description = "Recurring schedules and retainer (upfront-hours) invoices")
public class RecurringScheduleController {

    private final RecurringScheduleService scheduleService;
    private final InvoiceService           invoiceService;
    private final FeatureGuard             featureGuard;

    // ── Recurring schedules ───────────────────────────────────────────────────

    @GetMapping("/recurring-schedules")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "List all recurring schedules")
    public ResponseEntity<ApiResponse<Page<RecurringScheduleResponse>>> getSchedules(
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("invoicing");
        return ResponseEntity.ok(ApiResponse.success(
                scheduleService.getSchedules(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/recurring-schedules/{id}")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Get a recurring schedule by ID")
    public ResponseEntity<ApiResponse<RecurringScheduleResponse>> getSchedule(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        return ResponseEntity.ok(ApiResponse.success(
                scheduleService.getSchedule(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/recurring-schedules")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Create a recurring billing schedule")
    public ResponseEntity<ApiResponse<RecurringScheduleResponse>> createSchedule(
            @Valid @RequestBody CreateRecurringScheduleRequest req) {
        featureGuard.requireModule("invoicing");
        var result = scheduleService.createSchedule(TenantContext.getTenantIdAsObject(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Recurring schedule created", result));
    }

    @PostMapping("/recurring-schedules/{id}/line-items")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Add a line item to a recurring schedule")
    public ResponseEntity<ApiResponse<RecurringScheduleResponse>> addLineItem(
            @PathVariable UUID id,
            @Valid @RequestBody AddLineItemRequest req) {
        featureGuard.requireModule("invoicing");
        var result = scheduleService.addLineItem(TenantContext.getTenantIdAsObject(), id, req);
        return ResponseEntity.ok(ApiResponse.success("Line item added", result));
    }

    @PostMapping("/recurring-schedules/{id}/pause")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Pause a recurring schedule")
    public ResponseEntity<ApiResponse<RecurringScheduleResponse>> pause(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        return ResponseEntity.ok(ApiResponse.success("Schedule paused",
                scheduleService.pauseSchedule(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/recurring-schedules/{id}/resume")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Resume a paused recurring schedule")
    public ResponseEntity<ApiResponse<RecurringScheduleResponse>> resume(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        return ResponseEntity.ok(ApiResponse.success("Schedule resumed",
                scheduleService.resumeSchedule(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/recurring-schedules/{id}")
    @PreAuthorize("hasAuthority('INVOICE_DELETE')")
    @Operation(summary = "Cancel a recurring schedule")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        featureGuard.requireModule("invoicing");
        scheduleService.cancelSchedule(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Schedule cancelled", null));
    }

    // ── Retainer / upfront-hours invoices ─────────────────────────────────────

    @PostMapping("/invoices/retainer")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Create an upfront / committed-hours retainer invoice — machine hire pre-payment")
    public ResponseEntity<ApiResponse<InvoiceResponse>> createRetainer(
            @Valid @RequestBody CreateRetainerInvoiceRequest req) {
        featureGuard.requireModule("invoicing");
        var result = invoiceService.createRetainer(TenantContext.getTenantIdAsObject(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Retainer invoice created", result));
    }

    @PostMapping("/invoices/{id}/hours")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Log hours consumed against a retainer invoice")
    public ResponseEntity<ApiResponse<InvoiceResponse>> logHours(
            @PathVariable UUID id,
            @Valid @RequestBody LogHoursRequest req) {
        featureGuard.requireModule("invoicing");
        var result = invoiceService.logHours(TenantContext.getTenantIdAsObject(), id, req);
        return ResponseEntity.ok(ApiResponse.success("Hours logged", result));
    }

    @PostMapping("/recurring-schedules/variable-hours-contract")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Create a variable-hours contract — mining/earthmoving machine hire with monthly hour logging")
    public ResponseEntity<ApiResponse<RecurringScheduleResponse>> createVariableHoursContract(
            @Valid @RequestBody CreateVariableHoursContractRequest req) {
        featureGuard.requireModule("invoicing");
        var result = scheduleService.createVariableHoursContract(
                TenantContext.getTenantIdAsObject(), req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Variable-hours contract created", result));
    }

    /**
     * Operator endpoint — called at end of each billing cycle to log actual
     * hours worked and trigger invoice generation.
     *
     * POST /recurring-schedules/{id}/log-cycle-hours
     * Body: { actualHours: 187.5, periodLabel: "June 2026", operatorNotes: "..." }
     *
     * Business rules enforced by service:
     * - If actualHours < minimumHoursPerCycle → bill the minimum (take-or-pay)
     * - If actualHours > minimum → bill exactly what was worked
     * - Invoice is auto-issued immediately
     */
    @PostMapping("/recurring-schedules/{id}/log-cycle-hours")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Log actual hours for this billing cycle and generate invoice — enforces minimum hours clause")
    public ResponseEntity<ApiResponse<InvoiceResponse>> logCycleHours(
            @PathVariable UUID id,
            @Valid @RequestBody LogCycleHoursRequest req) {
        featureGuard.requireModule("invoicing");
        var result = scheduleService.logCycleHours(
                TenantContext.getTenantIdAsObject(), id, req);
        return ResponseEntity.ok(ApiResponse.success("Cycle hours logged and invoice generated", result));
    }
}
