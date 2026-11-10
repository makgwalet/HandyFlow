package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseSerialService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Serial numbers", description = "Serial register: switch an item on, pick serials to order lines, trace a serial")
public class WhseSerialController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhseSerialService service;
    private final FeatureGuard featureGuard;

    public record TrackingRequest(boolean serialTracked) {}
    public record PickRequest(@NotNull UUID orderId, @NotNull UUID lineId, @NotEmpty @Size(max = 1000) List<String> serials) {}

    @PutMapping("/items/{id}/serial-tracking")
    @PreAuthorize(WRITE)
    @Operation(summary = "Switch serial tracking on or off for an item. Refused while it has stock, and once serials exist for switching off.")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> track(@PathVariable UUID id, @Valid @RequestBody TrackingRequest req) {
        featureGuard.requireModule("warehousing");
        boolean on = service.setSerialTracked(TenantContext.getTenantIdAsObject(), id, req.serialTracked(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Saved", Map.of("serialTracked", on)));
    }

    @GetMapping("/serials/orders/{orderId}")
    @PreAuthorize(READ)
    @Operation(summary = "The serial-tracked lines of an order with the serials recorded so far")
    public ResponseEntity<ApiResponse<List<WhseSerialService.OrderLine>>> orderLines(@PathVariable UUID orderId) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.orderLines(TenantContext.getTenantIdAsObject(), orderId)));
    }

    @PostMapping("/serials/pick")
    @PreAuthorize(WRITE)
    @Operation(summary = "Assign serials to an order line while it is being picked")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> pick(@Valid @RequestBody PickRequest req) {
        featureGuard.requireModule("warehousing");
        int n = service.pick(TenantContext.getTenantIdAsObject(), req.orderId(), req.lineId(), req.serials(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Serials recorded", Map.of("recorded", n)));
    }

    @PostMapping("/serials/unpick")
    @PreAuthorize(WRITE)
    @Operation(summary = "Take serials back off an order line while it is being picked")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> unpick(@Valid @RequestBody PickRequest req) {
        featureGuard.requireModule("warehousing");
        int n = service.unpick(TenantContext.getTenantIdAsObject(), req.orderId(), req.lineId(), req.serials(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Serials removed", Map.of("removed", n)));
    }

    @GetMapping("/serials/{id}")
    @PreAuthorize(READ)
    @Operation(summary = "One serial with its status and whole history")
    public ResponseEntity<ApiResponse<WhseSerialService.Trace>> trace(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.trace(TenantContext.getTenantIdAsObject(), id)));
    }
}
