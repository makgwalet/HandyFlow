package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.warehousing.application.internal.WhseReceivingService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Receiving", description = "Receive with inspection, hold stock, report and sign off discrepancies")
public class WhseReceivingController {

    private static final String READ = "hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')";
    private static final String QC = "hasAuthority('WAREHOUSING_QC_RELEASE')";

    private final WhseReceivingService service;
    private final za.co.handyflow.platform.warehousing.application.internal.WhseSerialFlows flows;
    private final FeatureGuard featureGuard;

    public record ReceiptRequest(@DecimalMin("0") BigDecimal qtyGood, @DecimalMin("0") BigDecimal qtyDamaged, @DecimalMin("0") BigDecimal qtyHeld,
                                 @NotNull UUID locationId, @Size(max = 300) String reason, @Size(max = 100) String idempotencyKey,
                                 @Size(max = 60) String lotNumber, java.time.LocalDate expiryDate, java.time.LocalDate manufacturedDate,
                                 @Size(max = 1000) java.util.List<String> serials) {}
    public record UnexpectedLineRequest(@NotNull UUID itemId, @Size(max = 255) String note) {}
    public record InspectRequest(@NotNull @Pattern(regexp = "PASS|FAIL") String outcome, @NotNull @DecimalMin("0.001") BigDecimal qty,
                                 @NotNull @Size(min = 5, max = 300) String reason) {}
    public record CloseRequest(@Size(max = 500) String note) {}

    @PostMapping("/inbound-shipments/{id}/lines/{lineId}/receipts")
    @PreAuthorize(WRITE)
    @Operation(summary = "Receive a line, splitting what arrived into good, damaged and held-for-inspection stock")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> receive(@PathVariable UUID id, @PathVariable UUID lineId, @Valid @RequestBody ReceiptRequest req) {
        featureGuard.requireModule("warehousing");
        UUID receiptId = flows.receive(TenantContext.getTenantIdAsObject(), id, lineId, req.qtyGood(), req.qtyDamaged(), req.qtyHeld(),
                req.locationId(), req.reason(), req.lotNumber(), req.expiryDate(), req.manufacturedDate(), req.idempotencyKey(), req.serials(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Receipt recorded", Map.of("id", receiptId)));
    }

    @PostMapping("/inbound-shipments/{id}/unexpected-lines")
    @PreAuthorize(WRITE)
    @Operation(summary = "An item arrived that was not on the advice")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> unexpected(@PathVariable UUID id, @Valid @RequestBody UnexpectedLineRequest req) {
        featureGuard.requireModule("warehousing");
        UUID lineId = service.addUnexpectedLine(TenantContext.getTenantIdAsObject(), id, req.itemId(), req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Unexpected item added", Map.of("id", lineId)));
    }

    @GetMapping("/inbound-shipments/{id}/receipts")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WhseReceivingService.ReceiptRow>>> receipts(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.receipts(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/inbound-shipments/{id}/discrepancies")
    @PreAuthorize(READ)
    @Operation(summary = "Short, over, damaged, unexpected and held lines for a shipment")
    public ResponseEntity<ApiResponse<WhseReceivingService.DiscrepancyReport>> discrepancies(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.discrepancies(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/inbound-shipments/{id}/close")
    @PreAuthorize(WRITE)
    @Operation(summary = "Sign off a delivery. A note is required when there are discrepancies.")
    public ResponseEntity<ApiResponse<Void>> close(@PathVariable UUID id, @Valid @RequestBody(required = false) CloseRequest req) {
        featureGuard.requireModule("warehousing");
        service.close(TenantContext.getTenantIdAsObject(), id, req == null ? null : req.note(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Shipment closed", null));
    }

    @GetMapping("/receiving/inspection-queue")
    @PreAuthorize(READ)
    @Operation(summary = "Held stock waiting to be passed or failed, oldest first")
    public ResponseEntity<ApiResponse<List<WhseReceivingService.QueueRow>>> queue() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.inspectionQueue(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/receiving/receipts/{receiptId}/inspect")
    @PreAuthorize(QC)
    @Operation(summary = "Pass (release to available) or fail (move to damaged) held stock. Must be someone other than the receiver.")
    public ResponseEntity<ApiResponse<Void>> inspect(@PathVariable UUID receiptId, @Valid @RequestBody InspectRequest req) {
        featureGuard.requireModule("warehousing");
        service.inspect(TenantContext.getTenantIdAsObject(), receiptId, req.outcome(), req.qty(), req.reason(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Inspection recorded", null));
    }
}
