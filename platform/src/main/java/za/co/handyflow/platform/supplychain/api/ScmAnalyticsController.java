package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.ScmAnalyticsService;
import za.co.handyflow.platform.supplychain.application.internal.ScmAnalyticsService.*;

import java.time.LocalDate;
import java.util.List;

/** Spend, savings and supplier performance. Read-only. */
@RestController
@RequestMapping("/api/v1/supply-chain/analytics")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Analytics")
public class ScmAnalyticsController {

    private static final String CAN_READ = "hasAnyAuthority('SCM_READ','SCM_ADMIN')";

    private final ScmAnalyticsService service;

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) { return ResponseEntity.ok(ApiResponse.success("Success", body)); }

    @GetMapping("/spend")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Spend (approved or paid invoices, ex VAT) by month, supplier and budget scope; share with no PO; supplier concentration. Default: last 12 months")
    public ResponseEntity<ApiResponse<Spend>> spend(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(service.spend(tenant(), from, to));
    }

    @GetMapping("/savings")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Savings from awarded RFQs (against the buyer's estimate and the highest bid), what was paid above contract prices, and credits recovered")
    public ResponseEntity<ApiResponse<Savings>> savings(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(service.savings(tenant(), from, to));
    }

    @GetMapping("/suppliers")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Suppliers by spend with their scorecard; flags low scores, and those that are also a large share of spend")
    public ResponseEntity<ApiResponse<List<SupplierView>>> suppliers(@RequestParam(required = false) Integer months) {
        return ok(service.suppliers(tenant(), months));
    }
}
