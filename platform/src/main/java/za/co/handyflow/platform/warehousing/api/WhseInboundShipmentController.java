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
import za.co.handyflow.platform.warehousing.application.internal.WhseInboundShipmentService;
import za.co.handyflow.platform.warehousing.application.internal.WhseInboundShipmentService.InboundLine;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipment;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipmentLine;
import za.co.handyflow.platform.warehousing.dto.*;

import java.util.List;
import java.util.UUID;

/** The receiving workflow: ASN creation, per-line receipt against a put-away location, and its supporting evidence (delivery notes, ASN documents, ...). */
@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Inbound Shipments", description = "Advance shipping notices and receiving")
public class WhseInboundShipmentController {

    private static final String EVIDENCE_ENTITY_TYPE = "WhseInboundShipment";

    private final WhseInboundShipmentService shipmentService;
    private final EvidenceFacade evidenceFacade;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/inbound-shipments")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InboundShipmentResponse>>> list(@PathVariable UUID clientId,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                shipmentService.listForClient(TenantContext.getTenantIdAsObject(), clientId, pageable).map(this::toResponse)));
    }

    @GetMapping("/inbound-shipments/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<InboundShipmentResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(toResponse(shipmentService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @GetMapping("/inbound-shipments/{id}/lines")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<List<InboundShipmentLineResponse>>> listLines(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                shipmentService.listLines(TenantContext.getTenantIdAsObject(), id).stream().map(this::toLineResponse).toList()));
    }

    @PostMapping("/clients/{clientId}/inbound-shipments")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Create an inbound shipment (ASN) — the client telling the operator stock is coming")
    public ResponseEntity<ApiResponse<InboundShipmentResponse>> create(@PathVariable UUID clientId,
            @Valid @RequestBody CreateInboundShipmentRequest req) {
        featureGuard.requireModule("warehousing");
        List<InboundLine> lines = req.lines().stream()
                .map(l -> new InboundLine(l.itemId(), l.expectedQty(), l.notes())).toList();
        WhseInboundShipment shipment = shipmentService.createShipment(TenantContext.getTenantIdAsObject(), clientId,
                req.referenceNumber(), req.expectedDate(), lines, req.notes());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Shipment created", toResponse(shipment)));
    }

    @PostMapping("/inbound-shipments/{id}/lines/{lineId}/receive")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Record receipt of a line's stock at a put-away location — posts directly into inventory")
    public ResponseEntity<ApiResponse<InboundShipmentLineResponse>> receiveLine(@PathVariable UUID id,
            @PathVariable UUID lineId, @Valid @RequestBody ReceiveLineRequest req) {
        featureGuard.requireModule("warehousing");
        WhseInboundShipmentLine line = shipmentService.receiveLine(TenantContext.getTenantIdAsObject(), id, lineId,
                req.qty(), req.locationId(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Receipt recorded", toLineResponse(line)));
    }

    @PostMapping("/inbound-shipments/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<InboundShipmentResponse>> cancel(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Shipment cancelled",
                toResponse(shipmentService.cancel(TenantContext.getTenantIdAsObject(), id))));
    }

    // ── Evidence (ASN document, delivery note, ...) ─────────────────────────────

    @PostMapping(value = "/inbound-shipments/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Attach a supporting document (ASN, delivery note, ...) to this shipment")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attachEvidence(@PathVariable UUID id,
            @RequestParam("file") MultipartFile file, @RequestParam String evidenceType) {
        featureGuard.requireModule("warehousing");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        shipmentService.get(tenantId, id); // 404s if the shipment doesn't exist or isn't this tenant's
        EvidenceResponse evidence = evidenceFacade.attach(tenantId, file, evidenceType, "warehousing",
                EVIDENCE_ENTITY_TYPE, id, null, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached", evidence));
    }

    @GetMapping("/inbound-shipments/{id}/evidence")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        shipmentService.get(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(evidenceFacade.listFor(tenantId, "warehousing", EVIDENCE_ENTITY_TYPE, id)));
    }

    private InboundShipmentResponse toResponse(WhseInboundShipment s) {
        return new InboundShipmentResponse(s.getId(), s.getClientId(), s.getReferenceNumber(), s.getExpectedDate(),
                s.getReceivedDate(), s.getStatus(), s.getNotes());
    }

    private InboundShipmentLineResponse toLineResponse(WhseInboundShipmentLine l) {
        return new InboundShipmentLineResponse(l.getId(), l.getShipmentId(), l.getItemId(), l.getExpectedQty(),
                l.getReceivedQty(), l.getLocationId(), l.getNotes(), l.outstandingQty());
    }
}
