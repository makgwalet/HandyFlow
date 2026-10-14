package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.RecurringJournalService;
import za.co.handyflow.platform.accounting.dto.RecurringJournalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Recurring journals, accruals and prepayments (finance F17). */
@RestController
@RequestMapping("/api/v1/accounting/recurring-journals")
@RequiredArgsConstructor
@Tag(name = "Recurring journals")
public class RecurringJournalController {

    private final RecurringJournalService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Schedules with their next dates and recent runs")
    public ResponseEntity<ApiResponse<List<Response>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Set up a recurring journal, accrual or prepayment")
    public ResponseEntity<ApiResponse<Response>> create(@RequestBody CreateRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Schedule created",
                service.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<Response>> pause(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Paused", service.setStatus(TenantContext.getTenantIdAsObject(), id, "PAUSED")));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<Response>> resume(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Resumed", service.setStatus(TenantContext.getTenantIdAsObject(), id, "ACTIVE")));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<Response>> end(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Ended", service.setStatus(TenantContext.getTenantIdAsObject(), id, "ENDED")));
    }

    @PostMapping("/{id}/run-now")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Make the draft journals due today, without waiting for the daily job")
    public ResponseEntity<ApiResponse<RunResult>> runNow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Done", service.runDue(TenantContext.getTenantIdAsObject(), id, LocalDate.now())));
    }
}
