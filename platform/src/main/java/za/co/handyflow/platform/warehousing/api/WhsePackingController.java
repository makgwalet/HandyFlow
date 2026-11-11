package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackingService;
import za.co.handyflow.platform.warehousing.application.internal.WhsePdfService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Packing", description = "Packages, contents, weights and labels for outbound orders")
public class WhsePackingController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhsePackingService service;
    private final WhsePdfService pdfService;
    private final FeatureGuard featureGuard;

    public record NewPackage(String type, Integer lengthMm, Integer widthMm, Integer heightMm, @PositiveOrZero BigDecimal tareKg) {}
    public record ItemRequest(@NotNull UUID lineId, @NotNull @PositiveOrZero BigDecimal qty) {}
    public record CloseRequest(@NotNull BigDecimal grossKg) {}
    public record PackAll(String type) {}

    @GetMapping("/packing/orders/{orderId}")
    @PreAuthorize(READ)
    @Operation(summary = "Packages of an order, what is packed per line, and what still blocks marking it packed")
    public ResponseEntity<ApiResponse<WhsePackingService.Summary>> summary(@PathVariable UUID orderId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("OK", service.summary(TenantContext.getTenantIdAsObject(), orderId)));
    }

    @PostMapping("/packing/orders/{orderId}/packages")
    @PreAuthorize(WRITE)
    @Operation(summary = "Start a new package")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> create(@PathVariable UUID orderId, @Valid @RequestBody NewPackage req) {
        featureGuard.requireModule("warehousing");
        UUID id = service.createPackage(TenantContext.getTenantIdAsObject(), orderId, req.type(), req.lengthMm(), req.widthMm(), req.heightMm(), req.tareKg(),
                TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Package created", Map.of("id", id)));
    }

    @PostMapping("/packing/orders/{orderId}/pack-remaining")
    @PreAuthorize(WRITE)
    @Operation(summary = "Put everything not yet packed into one new package")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> packRemaining(@PathVariable UUID orderId, @RequestBody(required = false) PackAll req) {
        featureGuard.requireModule("warehousing");
        UUID id = service.packRemaining(TenantContext.getTenantIdAsObject(), orderId, req == null ? null : req.type(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Package created", Map.of("id", id)));
    }

    @PutMapping("/packing/packages/{packageId}/items")
    @PreAuthorize(WRITE)
    @Operation(summary = "Set how much of an order line is in this package (0 removes it)")
    public ResponseEntity<ApiResponse<Void>> setItem(@PathVariable UUID packageId, @Valid @RequestBody ItemRequest req) {
        featureGuard.requireModule("warehousing");
        service.setItem(TenantContext.getTenantIdAsObject(), packageId, req.lineId(), req.qty(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Saved", null));
    }

    @PostMapping("/packing/packages/{packageId}/close")
    @PreAuthorize(WRITE)
    @Operation(summary = "Close a package with its weighed gross weight")
    public ResponseEntity<ApiResponse<Void>> close(@PathVariable UUID packageId, @Valid @RequestBody CloseRequest req) {
        featureGuard.requireModule("warehousing");
        service.closePackage(TenantContext.getTenantIdAsObject(), packageId, req.grossKg(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Package closed", null));
    }

    @PostMapping("/packing/packages/{packageId}/reopen")
    @PreAuthorize(WRITE)
    @Operation(summary = "Reopen a closed package (only while the order is still being picked)")
    public ResponseEntity<ApiResponse<Void>> reopen(@PathVariable UUID packageId) {
        featureGuard.requireModule("warehousing");
        service.reopenPackage(TenantContext.getTenantIdAsObject(), packageId, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Package reopened", null));
    }

    @DeleteMapping("/packing/packages/{packageId}")
    @PreAuthorize(WRITE)
    @Operation(summary = "Delete an open package")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID packageId) {
        featureGuard.requireModule("warehousing");
        service.deletePackage(TenantContext.getTenantIdAsObject(), packageId, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Package deleted", null));
    }

    @GetMapping(value = "/packing/packages/{packageId}/label/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(READ)
    @Operation(summary = "Shipping label for a closed package")
    public ResponseEntity<byte[]> label(@PathVariable UUID packageId) {
        featureGuard.requireModule("warehousing");
        WhsePackingService.LabelData d = service.label(TenantContext.getTenantIdAsObject(), packageId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"label-" + (d.orderReference() == null ? "order" : d.orderReference().replaceAll("[^A-Za-z0-9._-]", "_")) + "-P" + d.packageNo() + ".pdf\"")
                .body(pdfService.generatePackageLabel(d));
    }
}
