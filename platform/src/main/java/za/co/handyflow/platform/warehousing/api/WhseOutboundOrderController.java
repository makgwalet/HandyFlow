package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhseClientService;
import za.co.handyflow.platform.warehousing.application.internal.WhseItemService;
import za.co.handyflow.platform.warehousing.application.internal.WhseOutboundOrderService;
import za.co.handyflow.platform.warehousing.application.internal.WhseOutboundOrderService.OutboundLine;
import za.co.handyflow.platform.warehousing.application.internal.WhsePdfService;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrder;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrderLine;
import za.co.handyflow.platform.warehousing.dto.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The fulfilment workflow: order creation, picking/packing/shipping, cancellation, packing-slip PDF, and its supporting evidence (proof of delivery, ...). */
@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Outbound Orders", description = "Pick-pack-ship fulfilment orders")
public class WhseOutboundOrderController {

    private static final String EVIDENCE_ENTITY_TYPE = "WhseOutboundOrder";

    private final WhseOutboundOrderService orderService;
    private final WhseClientService clientService;
    private final WhseItemService itemService;
    private final WhsePdfService pdfService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/outbound-orders")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<OutboundOrderResponse>>> list(@PathVariable UUID clientId,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                orderService.listForClient(TenantContext.getTenantIdAsObject(), clientId, pageable).map(this::toResponse)));
    }

    @GetMapping("/outbound-orders/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<OutboundOrderResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(toResponse(orderService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @GetMapping("/outbound-orders/{id}/lines")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<List<OutboundOrderLineResponse>>> listLines(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                orderService.listLines(TenantContext.getTenantIdAsObject(), id).stream().map(this::toLineResponse).toList()));
    }

    @PostMapping("/clients/{clientId}/outbound-orders")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Create an outbound fulfilment order")
    public ResponseEntity<ApiResponse<OutboundOrderResponse>> create(@PathVariable UUID clientId,
            @Valid @RequestBody CreateOutboundOrderRequest req) {
        featureGuard.requireModule("warehousing");
        List<OutboundLine> lines = req.lines().stream()
                .map(l -> new OutboundLine(l.itemId(), l.qtyOrdered(), l.notes())).toList();
        WhseOutboundOrder order = orderService.createOrder(TenantContext.getTenantIdAsObject(), clientId,
                req.orderReference(), req.shipToName(), req.shipToAddress(), req.requestedShipDate(), lines, req.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Order created", toResponse(order)));
    }

    @PostMapping("/outbound-orders/{id}/start-picking")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Move to PICKING and allocate stock for every line")
    public ResponseEntity<ApiResponse<OutboundOrderResponse>> startPicking(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Picking started",
                toResponse(orderService.startPicking(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/outbound-orders/{id}/lines/{lineId}/pick")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Record picking progress on one line — informational only, see markShipped()'s Javadoc")
    public ResponseEntity<ApiResponse<OutboundOrderLineResponse>> markLinePicked(@PathVariable UUID id,
            @PathVariable UUID lineId, @Valid @RequestBody MarkLinePickedRequest req) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Line picked",
                toLineResponse(orderService.markLinePicked(TenantContext.getTenantIdAsObject(), id, lineId, req.qty()))));
    }

    @PostMapping("/outbound-orders/{id}/pack")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<OutboundOrderResponse>> markPacked(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Order packed",
                toResponse(orderService.markPacked(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/outbound-orders/{id}/ship")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Ship the order — fulfils the stock allocation and posts PICK movements")
    public ResponseEntity<ApiResponse<OutboundOrderResponse>> markShipped(@PathVariable UUID id,
            @RequestBody(required = false) MarkShippedRequest req) {
        featureGuard.requireModule("warehousing");
        String carrier = req != null ? req.carrier() : null;
        String trackingNumber = req != null ? req.trackingNumber() : null;
        return ResponseEntity.ok(ApiResponse.success("Order shipped", toResponse(
                orderService.markShipped(TenantContext.getTenantIdAsObject(), id, carrier, trackingNumber,
                        TenantContext.getCurrentUserId()))));
    }

    @PostMapping("/outbound-orders/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<OutboundOrderResponse>> cancel(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Order cancelled",
                toResponse(orderService.cancel(TenantContext.getTenantIdAsObject(), id))));
    }

    // ── PDF documents ────────────────────────────────────────────────────────

    @GetMapping(value = "/outbound-orders/{id}/packing-slip/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Export a packing slip / delivery note for this order")
    public ResponseEntity<byte[]> exportPackingSlip(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        WhseOutboundOrder order = orderService.get(tenantId, id);
        WhseClient client = clientService.get(tenantId, order.getClientId());
        List<WhseOutboundOrderLine> lines = orderService.listLines(tenantId, id);
        Map<UUID, WhseItem> itemsById = itemService.listAllActiveForClient(tenantId, order.getClientId()).stream()
                .collect(Collectors.toMap(WhseItem::getId, i -> i));
        byte[] pdf = pdfService.generatePackingSlip(null, client, order, lines, itemsById);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"packing-slip-" + nullSafe(order.getOrderReference()) + ".pdf\"")
                .body(pdf);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "order";
    }

    // ── Evidence (proof of delivery, signed POD, ...) ───────────────────────────

    @PostMapping(value = "/outbound-orders/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Attach a supporting document (proof of delivery, signed POD, ...) to this order")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(@PathVariable UUID id,
            @RequestParam("file") MultipartFile file, @RequestParam String evidenceType) {
        featureGuard.requireModule("warehousing");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        orderService.get(tenantId, id); // 404s if the order doesn't exist or isn't this tenant's
        EvidenceResponse evidence = evidenceFacade.attach(tenantId, file, evidenceType, "warehousing",
                EVIDENCE_ENTITY_TYPE, id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached", evidence));
    }

    @GetMapping("/outbound-orders/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        orderService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(evidenceFacade.listFor(tenantId, "warehousing", EVIDENCE_ENTITY_TYPE, id)));
    }

    private OutboundOrderResponse toResponse(WhseOutboundOrder o) {
        return new OutboundOrderResponse(o.getId(), o.getClientId(), o.getOrderReference(), o.getShipToName(),
                o.getShipToAddress(), o.getRequestedShipDate(), o.getShippedDate(), o.getStatus(), o.getCarrier(),
                o.getTrackingNumber(), o.getNotes());
    }

    private OutboundOrderLineResponse toLineResponse(WhseOutboundOrderLine l) {
        return new OutboundOrderLineResponse(l.getId(), l.getOrderId(), l.getItemId(), l.getLocationId(),
                l.getQtyOrdered(), l.getQtyPicked(), l.getNotes(), l.isFullyPicked());
    }
}
