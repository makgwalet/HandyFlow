package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseReturnsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing/returns")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Returns", description = "Customer returns (RMA): expect, receive into restock/held/damaged, close")
public class WhseReturnsController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";

    private final WhseReturnsService service;
    private final FeatureGuard featureGuard;

    public record LineRequest(@NotNull UUID itemId, @NotNull @DecimalMin("0.001") BigDecimal expectedQty) {}
    public record CreateRequest(@NotNull UUID clientId, UUID outboundOrderId, @NotNull @Size(min = 3, max = 300) String reason,
                                @Size(max = 500) String notes, @NotEmpty @Valid List<LineRequest> lines) {}
    public record ReceiveRequest(@DecimalMin("0") BigDecimal qtyRestock, @DecimalMin("0") BigDecimal qtyHeld, @DecimalMin("0") BigDecimal qtyDamaged,
                                 @NotNull UUID locationId, @Size(max = 300) String reason, @Size(max = 100) String idempotencyKey,
                                 @Size(max = 60) String lotNumber) {}
    public record CloseRequest(@Size(max = 500) String note) {}

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhseReturnsService.ReturnRow>>> list(@RequestParam(required = false) UUID clientId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), clientId, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<WhseReturnsService.Detail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.detail(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(WRITE)
    @Operation(summary = "Expect a return. When linked to a shipped order, quantities cannot exceed what was shipped less earlier returns.")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> create(@Valid @RequestBody CreateRequest req) {
        featureGuard.requireModule("warehousing");
        UUID id = service.create(TenantContext.getTenantIdAsObject(), req.clientId(), req.outboundOrderId(), req.reason(), req.notes(),
                req.lines().stream().map(l -> new WhseReturnsService.NewLine(l.itemId(), l.expectedQty())).toList(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Return created", Map.of("id", id)));
    }

    @PostMapping("/{id}/lines/{lineId}/receive")
    @PreAuthorize(WRITE)
    @Operation(summary = "Receive returned goods as restocked (available), held for inspection (quarantine) or damaged")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> receive(@PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody ReceiveRequest req) {
        featureGuard.requireModule("warehousing");
        UUID receiptId = service.receive(TenantContext.getTenantIdAsObject(), id, lineId, req.qtyRestock(), req.qtyHeld(), req.qtyDamaged(),
                req.locationId(), req.reason(), req.lotNumber(), req.idempotencyKey(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Return received", Map.of("id", receiptId)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> close(@PathVariable UUID id, @Valid @RequestBody(required = false) CloseRequest req) {
        featureGuard.requireModule("warehousing");
        service.close(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Return closed", null));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        service.cancel(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Return cancelled", null));
    }
}
