package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.warehousing.application.internal.WhsePortalDataService;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipment;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrder;
import za.co.handyflow.platform.warehousing.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.UUID;

/** Client-portal-facing reads — a client checking their own stock, shipments, orders, and billing history. Direct mirror of CollAgencyPortalDataController. */
@RestController
@RequestMapping("/api/v1/warehousing/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Warehousing Client Portal", description = "Client-facing data access")
public class WhsePortalDataController {

    private final WhsePortalDataService portalDataService;

    @GetMapping("/clients")
    @Operation(summary = "List every client this portal user has access to")
    public ResponseEntity<ApiResponse<List<PortalClientSummaryResponse>>> getMyClients() {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyClients(getPortalUserId())));
    }

    @GetMapping("/clients/{clientId}/inventory")
    @Operation(summary = "Current stock position for a client this portal user has access to")
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getMyInventory(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyInventory(getPortalUserId(), clientId).stream().map(this::toInventoryResponse).toList()));
    }

    @GetMapping("/clients/{clientId}/inbound-shipments")
    @Operation(summary = "Inbound shipment status for a client this portal user has access to")
    public ResponseEntity<ApiResponse<List<InboundShipmentResponse>>> getMyInboundShipments(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyInboundShipments(getPortalUserId(), clientId).stream().map(this::toShipmentResponse).toList()));
    }

    @GetMapping("/clients/{clientId}/outbound-orders")
    @Operation(summary = "Outbound order status for a client this portal user has access to")
    public ResponseEntity<ApiResponse<List<OutboundOrderResponse>>> getMyOutboundOrders(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyOutboundOrders(getPortalUserId(), clientId).stream().map(this::toOrderResponse).toList()));
    }

    @GetMapping("/clients/{clientId}/billing-invoices")
    @Operation(summary = "Billing invoice history for a client this portal user has access to")
    public ResponseEntity<ApiResponse<List<BillingInvoiceResponse>>> getMyBillingInvoices(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyBillingInvoices(getPortalUserId(), clientId).stream().map(this::toInvoiceResponse).toList()));
    }

    private InventoryResponse toInventoryResponse(WhseInventory i) {
        return new InventoryResponse(i.getId(), i.getClientId(), i.getItemId(), i.getLocationId(), i.getQtyOnHand(),
                i.getQtyAllocated(), i.available());
    }

    private InboundShipmentResponse toShipmentResponse(WhseInboundShipment s) {
        return new InboundShipmentResponse(s.getId(), s.getClientId(), s.getReferenceNumber(), s.getExpectedDate(),
                s.getReceivedDate(), s.getStatus(), s.getNotes());
    }

    private OutboundOrderResponse toOrderResponse(WhseOutboundOrder o) {
        return new OutboundOrderResponse(o.getId(), o.getClientId(), o.getOrderReference(), o.getShipToName(),
                o.getShipToAddress(), o.getRequestedShipDate(), o.getShippedDate(), o.getStatus(), o.getCarrier(),
                o.getTrackingNumber(), o.getNotes());
    }

    private BillingInvoiceResponse toInvoiceResponse(WhseBillingInvoice i) {
        return new BillingInvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getPeriodStart(),
                i.getPeriodEnd(), i.getInvoiceDate(), i.getDueDate(), i.getStorageFee(), i.getHandlingFee(),
                i.getVatAmount(), i.getSubtotal(), i.getTotal(), i.getAmountPaid(), i.balance(), i.getStatus(),
                i.getSentAt(), i.getPaidAt());
    }

    private UUID getPortalUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}
