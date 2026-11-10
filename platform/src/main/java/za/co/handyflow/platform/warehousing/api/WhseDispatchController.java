package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhseDispatchService;
import za.co.handyflow.platform.warehousing.application.internal.WhsePdfService;
import za.co.handyflow.platform.warehousing.application.internal.WhseProfileService;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.dto.CancelManifestRequest;
import za.co.handyflow.platform.warehousing.dto.CreateManifestRequest;
import za.co.handyflow.platform.warehousing.dto.ManifestOrderRequest;
import za.co.handyflow.platform.warehousing.dto.RecordDeliveryRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Dispatch", description = "Loading, manifest and proof of delivery")
public class WhseDispatchController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhseDispatchService service;
    private final WhsePdfService pdfService;
    private final WhseProfileService profileService;
    private final FeatureGuard featureGuard;

    private TenantId tenant() { return TenantContext.getTenantIdAsObject(); }

    @GetMapping("/dispatch/ready-orders")
    @PreAuthorize(READ)
    @Operation(summary = "Packed orders that are not on a manifest yet")
    public ResponseEntity<ApiResponse<List<WhseDispatchService.ReadyOrder>>> ready() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.readyOrders(tenant())));
    }

    @GetMapping("/dispatch/manifests")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhseDispatchService.ManifestRow>>> list(@RequestParam(required = false) String status) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.list(tenant(), status)));
    }

    @PostMapping("/dispatch/manifests")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Map<String, UUID>>> create(@Valid @RequestBody CreateManifestRequest req) {
        featureGuard.requireModule("warehousing");
        UUID id = service.create(tenant(), req.carrier(), req.vehicleRegistration(), req.driverName(), req.notes(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Manifest created", Map.of("id", id)));
    }

    @GetMapping("/dispatch/manifests/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<WhseDispatchService.ManifestDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.get(tenant(), id)));
    }

    @PostMapping("/dispatch/manifests/{id}/orders")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> addOrder(@PathVariable UUID id, @Valid @RequestBody ManifestOrderRequest req) {
        featureGuard.requireModule("warehousing");
        service.addOrder(tenant(), id, req.orderId());
        return ResponseEntity.ok(ApiResponse.success("Order added", null));
    }

    @DeleteMapping("/dispatch/manifests/{id}/orders/{orderId}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> removeOrder(@PathVariable UUID id, @PathVariable UUID orderId) {
        featureGuard.requireModule("warehousing");
        service.removeOrder(tenant(), id, orderId);
        return ResponseEntity.ok(ApiResponse.success("Order taken off", null));
    }

    @PostMapping("/dispatch/manifests/{id}/start-loading")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> startLoading(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.startLoading(tenant(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Loading started", null));
    }

    @PostMapping("/dispatch/manifests/{id}/dispatch")
    @PreAuthorize(WRITE)
    @Operation(summary = "Ship every order on the manifest, all or none. Stock leaves exactly as when an order is shipped on its own.")
    public ResponseEntity<ApiResponse<Void>> dispatch(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.dispatch(tenant(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Manifest dispatched", null));
    }

    @PostMapping("/dispatch/manifests/{id}/cancel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id, @Valid @RequestBody CancelManifestRequest req) {
        featureGuard.requireModule("warehousing");
        service.cancel(tenant(), id, req.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Manifest cancelled", null));
    }

    @GetMapping(value = "/dispatch/manifests/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(READ)
    @Operation(summary = "The manifest as a PDF, with sign-off lines for the dispatcher and the driver")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        WhseDispatchService.ManifestDetail detail = service.get(tenant(), id);
        WhseProfile profile = profileService.get(tenant());
        byte[] bytes = pdfService.generateManifest(profile != null ? profile.getWarehouseName() : null, detail);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"manifest-" + detail.manifest().manifestNumber() + ".pdf\"")
                .body(bytes);
    }

    @PostMapping("/outbound-orders/{id}/delivery")
    @PreAuthorize(WRITE)
    @Operation(summary = "Record proof of delivery for a shipped order: who received it and when")
    public ResponseEntity<ApiResponse<Void>> recordDelivery(@PathVariable UUID id, @Valid @RequestBody RecordDeliveryRequest req) {
        featureGuard.requireModule("warehousing");
        service.recordDelivery(tenant(), id, req.receivedBy(), req.deliveredAt(), req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Delivery recorded", null));
    }

    @GetMapping("/outbound-orders/{id}/delivery")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<WhseDispatchService.Delivery>> delivery(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.deliveryOf(tenant(), id)));
    }
}
