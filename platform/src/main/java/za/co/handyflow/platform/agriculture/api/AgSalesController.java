package za.co.handyflow.platform.agriculture.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.agriculture.application.internal.AgSalesAllocationService;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.AllocateSaleRequest;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SaleLineResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesAllocationResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesTotalsResponse;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Links invoice lines to production (ADR-001, W2). Revenue is the invoice's, not Agriculture's: nothing here creates or edits a sale.
 * <p>
 * Every endpoint needs AGRICULTURE_FINANCE. The ones that read or return invoice data (customer names, prices) ALSO need INVOICE_READ and an
 * Invoicing subscription, so Agriculture can never be used to read invoices a user could not open in Invoicing.
 */
@RestController
@RequestMapping("/api/v1/agriculture")
@RequiredArgsConstructor
@Tag(name = "Agriculture - Sales allocation", description = "Attribute invoice lines to crop cycles, groups, animals and enterprises")
public class AgSalesController {

    private final AgSalesAllocationService salesService;
    private final FeatureGuard featureGuard;

    @GetMapping("/farms/{farmId}/sales/lines")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('INVOICE_READ')")
    @Operation(summary = "Invoice lines that count as revenue (issued onwards), newest first, with what is already allocated",
            description = "from and to default to the last 90 days (at most 366 days). q matches invoice number, customer or description.")
    public ResponseEntity<ApiResponse<List<SaleLineResponse>>> searchLines(
            @PathVariable UUID farmId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q) {
        requireModules();
        return ResponseEntity.ok(ApiResponse.success(salesService.searchLines(TenantContext.getTenantIdAsObject(), farmId, from, to, q)));
    }

    @PostMapping("/farms/{farmId}/sales-allocations")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('INVOICE_READ')")
    @Operation(summary = "Allocate part or all of one invoice line to production targets on this farm",
            description = "Quantities are in the line's own unit and cannot exceed what is left on the line. Only issued invoices count. " +
                    "This records revenue attribution only: it does not mark animals sold or change a head count.")
    public ResponseEntity<ApiResponse<List<SalesAllocationResponse>>> allocate(@PathVariable UUID farmId, @Valid @RequestBody AllocateSaleRequest request) {
        requireModules();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                salesService.allocate(TenantContext.getTenantIdAsObject(), farmId, TenantContext.getCurrentUserId(), request)));
    }

    @GetMapping("/farms/{farmId}/sales-allocations")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('INVOICE_READ')")
    @Operation(summary = "A farm's active sales allocations with their live revenue, optionally for one target (targetType and targetId together)")
    public ResponseEntity<ApiResponse<Page<SalesAllocationResponse>>> list(@PathVariable UUID farmId,
                                                                           @RequestParam(required = false) String targetType,
                                                                           @RequestParam(required = false) UUID targetId,
                                                                           @PageableDefault(size = 50) Pageable pageable) {
        requireModules();
        return ResponseEntity.ok(ApiResponse.success(salesService.list(TenantContext.getTenantIdAsObject(), farmId, targetType, targetId, pageable)));
    }

    @GetMapping("/farms/{farmId}/sales-allocations/totals")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE') and hasAuthority('INVOICE_READ')")
    @Operation(summary = "Revenue of a farm's (or one target's) active allocations, live, ex-VAT and net of credit notes, by target")
    public ResponseEntity<ApiResponse<SalesTotalsResponse>> totals(@PathVariable UUID farmId,
                                                                  @RequestParam(required = false) String targetType,
                                                                  @RequestParam(required = false) UUID targetId) {
        requireModules();
        return ResponseEntity.ok(ApiResponse.success(salesService.totals(TenantContext.getTenantIdAsObject(), farmId, targetType, targetId)));
    }

    @DeleteMapping("/sales-allocations/{id}")
    @PreAuthorize("hasAuthority('AGRICULTURE_FINANCE')")
    @Operation(summary = "Remove an allocation from revenue (it stays on record as REMOVED); the invoice itself is untouched")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        featureGuard.requireModule("agriculture");
        salesService.remove(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    private void requireModules() {
        featureGuard.requireModule("agriculture");
        featureGuard.requireModule("invoicing");
    }
}
