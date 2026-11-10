package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseOperationsService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/operations")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Operations", description = "Dashboard, stock across clients, movements and reservations")
public class WhseOperationsController {

    private final WhseOperationsService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Dashboard numbers: stock by status, work in progress, today's activity, top movers")
    public ResponseEntity<ApiResponse<WhseOperationsService.Summary>> summary() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.summary(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Units received, dispatched and adjusted per day (default 7 days, at most 90)")
    public ResponseEntity<ApiResponse<List<WhseOperationsService.TrendPoint>>> trend(@RequestParam(defaultValue = "7") int days) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.trend(TenantContext.getTenantIdAsObject(), days)));
    }

    @GetMapping("/inventory")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Stock across all clients, filterable by client, status and SKU/description")
    public ResponseEntity<ApiResponse<WhseOperationsService.Page<WhseOperationsService.StockRow>>> inventory(
            @RequestParam(required = false) UUID clientId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean includeEmpty,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.stock(TenantContext.getTenantIdAsObject(), clientId, status, q, includeEmpty, page, size)));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "The stock movement ledger, newest first")
    public ResponseEntity<ApiResponse<WhseOperationsService.Page<WhseOperationsService.MovementRow>>> movements(
            @RequestParam(required = false) UUID clientId, @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.movements(TenantContext.getTenantIdAsObject(), clientId, itemId, type, from, to, page, size)));
    }

    @GetMapping("/stock-alerts")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Items below their minimum or above their maximum level, with a suggested top-up quantity")
    public ResponseEntity<ApiResponse<List<WhseOperationsService.StockAlert>>> stockAlerts(@RequestParam(required = false) UUID clientId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.stockAlerts(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @GetMapping("/reservations")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "What is reserved right now, per order and position")
    public ResponseEntity<ApiResponse<List<WhseOperationsService.ReservationRow>>> reservations(@RequestParam(required = false) UUID clientId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.reservations(TenantContext.getTenantIdAsObject(), clientId)));
    }
}
