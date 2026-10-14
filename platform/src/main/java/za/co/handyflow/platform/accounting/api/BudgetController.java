package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.BudgetService;
import za.co.handyflow.platform.accounting.application.internal.CashForecastRules.Assumptions;
import za.co.handyflow.platform.accounting.application.internal.CashForecastService;
import za.co.handyflow.platform.accounting.dto.BudgetDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Budgets, budget against actual, and the 13-week cash forecast (finance F21). */
@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
@Tag(name = "Budgets and cash forecast")
public class BudgetController {

    private final BudgetService budgets;
    private final CashForecastService forecast;

    @GetMapping("/budgets")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<List<BudgetSummary>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Success", budgets.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/budgets")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<BudgetSummary>> create(@RequestBody CreateBudget req) {
        return ResponseEntity.ok(ApiResponse.success("Budget created", budgets.create(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/budgets/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<BudgetDetail>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", budgets.detail(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/budgets/{id}/lines")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Replace every line of a draft budget")
    public ResponseEntity<ApiResponse<BudgetDetail>> setLines(@PathVariable UUID id, @RequestBody SetLines req) {
        return ResponseEntity.ok(ApiResponse.success("Saved", budgets.setLines(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/budgets/{id}/copy-actuals")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Fill a draft budget from the 12 months before it starts, plus an uplift percentage")
    public ResponseEntity<ApiResponse<BudgetDetail>> copyActuals(@PathVariable UUID id, @RequestBody(required = false) CopyActuals req) {
        return ResponseEntity.ok(ApiResponse.success("Filled from last year", budgets.copyFromActuals(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/budgets/{id}/activate")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<BudgetSummary>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Budget active", budgets.activate(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/budgets/{id}/close")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<BudgetSummary>> close(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Budget closed", budgets.close(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/budgets/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        budgets.deleteDraft(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @GetMapping("/budgets/{id}/vs-actual")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<VarianceReport>> vsActual(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.success("Success", budgets.vsActual(TenantContext.getTenantIdAsObject(), id, asOf)));
    }

    @GetMapping("/cash-forecast")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "13 weeks of expected cash in and out, from open invoices, approved bills and items added by hand")
    public ResponseEntity<ApiResponse<ForecastResponse>> cashForecast(
            @RequestParam(defaultValue = "0") int customerDelayDays,
            @RequestParam(defaultValue = "14") int overdueReceiptInDays,
            @RequestParam(defaultValue = "7") int overduePayInDays,
            @RequestParam(defaultValue = "100") int collectPercent,
            @RequestParam(required = false) BigDecimal lowBalance) {
        return ResponseEntity.ok(ApiResponse.success("Success", forecast.forecast(TenantContext.getTenantIdAsObject(),
                new Assumptions(customerDelayDays, overdueReceiptInDays, overduePayInDays, collectPercent), lowBalance)));
    }

    @GetMapping("/cash-forecast/items")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> items() {
        return ResponseEntity.ok(ApiResponse.success("Success", forecast.manualItems(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/cash-forecast/items")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<ItemResponse>> addItem(@RequestBody ItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Added", forecast.addItem(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/cash-forecast/items/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> removeItem(@PathVariable UUID id) {
        forecast.removeItem(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }
}
