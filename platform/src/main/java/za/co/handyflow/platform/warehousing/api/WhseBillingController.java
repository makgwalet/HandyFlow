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
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhseBillingService;
import za.co.handyflow.platform.warehousing.application.internal.WhseInventoryService;
import za.co.handyflow.platform.warehousing.application.internal.WhseItemService;
import za.co.handyflow.platform.warehousing.application.internal.WhseLocationService;
import za.co.handyflow.platform.warehousing.application.internal.WhseClientService;
import za.co.handyflow.platform.warehousing.application.internal.WhseProfileService;
import za.co.handyflow.platform.warehousing.application.internal.WhsePdfService;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;
import za.co.handyflow.platform.warehousing.domain.model.WhseClient;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseItem;
import za.co.handyflow.platform.warehousing.domain.model.WhseLocation;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;
import za.co.handyflow.platform.warehousing.dto.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Billing invoice generation and settlement. Generating an invoice is the
 * financial commit point of this module (creates the invoice AND posts
 * real GL revenue), so it's ADMIN-gated only — same "financial commit
 * point needs a bigger permission than day-to-day case work" convention
 * CollAgencyTrustController's processRemittance() already established.
 */
@RestController
@RequestMapping("/api/v1/warehousing")
@RequiredArgsConstructor
@Tag(name = "Warehousing - Billing", description = "Storage + handling fee billing")
public class WhseBillingController {

    private final WhseBillingService billingService;
    private final WhseClientService clientService;
    private final WhseProfileService profileService;
    private final WhseInventoryService inventoryService;
    private final WhseItemService itemService;
    private final WhseLocationService locationService;
    private final WhsePdfService pdfService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/billing-invoices")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BillingInvoiceResponse>>> list(@PathVariable UUID clientId,
            @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(
                billingService.listForClient(TenantContext.getTenantIdAsObject(), clientId, pageable).map(this::toResponse)));
    }

    @GetMapping("/billing-invoices/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<BillingInvoiceResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(toResponse(billingService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients/{clientId}/billing-invoices/generate")
    @PreAuthorize("hasAuthority('WAREHOUSING_ADMIN')")
    @Operation(summary = "Generate and issue a billing invoice for storage + handling since the last invoice — posts real revenue to the GL. See WhseBillingService's own Javadoc for the deliberate simplifications this computation makes.")
    public ResponseEntity<ApiResponse<BillingInvoiceResponse>> generateInvoice(@PathVariable UUID clientId,
            @Valid @RequestBody GenerateInvoiceRequest req) {
        featureGuard.requireModule("warehousing");
        WhseBillingInvoice invoice = billingService.generateInvoice(TenantContext.getTenantIdAsObject(), clientId, req.periodEnd());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice generated", toResponse(invoice)));
    }

    @PostMapping("/billing-invoices/{id}/payments")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Record that this invoice has been settled — internal tracking only, does not post a second GL journal")
    public ResponseEntity<ApiResponse<BillingInvoiceResponse>> recordPayment(@PathVariable UUID id,
            @Valid @RequestBody RecordInvoicePaymentRequest req) {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success("Payment recorded",
                toResponse(billingService.recordPayment(TenantContext.getTenantIdAsObject(), id, req.amount()))));
    }

    @GetMapping(value = "/clients/{clientId}/inventory-statement/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    @Operation(summary = "Export the client's current inventory statement as a PDF")
    public ResponseEntity<byte[]> exportInventoryStatement(@PathVariable UUID clientId) {
        featureGuard.requireModule("warehousing");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        WhseClient client = clientService.get(tenantId, clientId);
        WhseProfile profile = profileService.get(tenantId);
        List<WhseInventory> positions = inventoryService.listForClient(tenantId, clientId);
        Map<UUID, WhseItem> itemsById = itemService.listAllActiveForClient(tenantId, clientId).stream()
                .collect(Collectors.toMap(WhseItem::getId, i -> i));
        Map<UUID, WhseLocation> locationsById = locationService.listAll(tenantId).stream()
                .collect(Collectors.toMap(WhseLocation::getId, l -> l));
        byte[] pdf = pdfService.generateInventoryStatement(profile != null ? profile.getWarehouseName() : null,
                client, positions, itemsById, locationsById);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"inventory-statement-" + client.getTradingName() + ".pdf\"")
                .body(pdf);
    }

    private BillingInvoiceResponse toResponse(WhseBillingInvoice i) {
        return new BillingInvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getPeriodStart(),
                i.getPeriodEnd(), i.getInvoiceDate(), i.getDueDate(), i.getStorageFee(), i.getHandlingFee(),
                i.getVatAmount(), i.getSubtotal(), i.getTotal(), i.getAmountPaid(), i.balance(), i.getStatus(),
                i.getSentAt(), i.getPaidAt());
    }
}
