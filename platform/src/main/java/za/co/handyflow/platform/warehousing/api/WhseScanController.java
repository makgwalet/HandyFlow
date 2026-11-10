package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseScanService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/scan")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Scan", description = "Resolve a scanned code, look up stock, set location barcodes")
public class WhseScanController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhseScanService service;
    private final FeatureGuard featureGuard;

    public record BarcodeRequest(@Size(max = 100) String barcode) {}

    @GetMapping("/resolve")
    @PreAuthorize(READ)
    @Operation(summary = "What a scanned or typed code names: location, item (GTIN or SKU), lot, ASN, order or manifest. May return several.")
    public ResponseEntity<ApiResponse<List<WhseScanService.Match>>> resolve(@RequestParam String code) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.resolve(TenantContext.getTenantIdAsObject(), code)));
    }

    @GetMapping("/locations/{id}/stock")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhseScanService.StockLine>>> locationStock(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.stockAtLocation(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/items/{id}/stock")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhseScanService.StockLine>>> itemStock(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.stockOfItem(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/lots/{id}/stock")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhseScanService.StockLine>>> lotStock(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.stockOfLot(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/locations/{id}/barcode")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Sets or clears a location's own barcode. Its code scans either way.")
    public ResponseEntity<ApiResponse<Map<String, String>>> setBarcode(@PathVariable UUID id, @Valid @RequestBody BarcodeRequest req) {
        featureGuard.requireModule("warehousing");
        var l = service.setLocationBarcode(TenantContext.getTenantIdAsObject(), id, req.barcode());
        return ResponseEntity.ok(ApiResponse.success("Saved", Map.of("code", l.getCode(), "barcode", l.getBarcode() == null ? "" : l.getBarcode())));
    }
}
