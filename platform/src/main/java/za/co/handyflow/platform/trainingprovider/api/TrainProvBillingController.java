package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvBillingService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvClientService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPdfService;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvClient;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvInvoice;
import za.co.handyflow.platform.trainingprovider.dto.GenerateInvoiceRequest;
import za.co.handyflow.platform.trainingprovider.dto.InvoiceResponse;
import za.co.handyflow.platform.trainingprovider.dto.RecordPaymentRequest;

import java.util.UUID;

/**
 * Financial-commit-point gating: generating an invoice and recording a
 * payment are ADMIN-only — same "financial commit point needs a bigger
 * permission than day-to-day case work" convention
 * WhseBillingController/CollAgencyTrustController already establish
 * for this codebase.
 */
@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Billing", description = "Per-client invoicing for delegate training")
public class TrainProvBillingController {

    private final TrainProvBillingService billingService;
    private final TrainProvClientService clientService;
    private final TrainProvPdfService pdfService;
    private final JdbcTemplate jdbc;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/invoices")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<InvoiceResponse>>> list(
            @PathVariable UUID clientId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(
                billingService.list(TenantContext.getTenantIdAsObject(), clientId, pageable).map(this::toResponse)));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(billingService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/clients/{clientId}/invoices/generate")
    @PreAuthorize("hasAuthority('TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Generate an invoice covering every not-yet-invoiced billable enrollment for this client — ADMIN only")
    public ResponseEntity<ApiResponse<InvoiceResponse>> generate(@PathVariable UUID clientId, @Valid @RequestBody GenerateInvoiceRequest req) {
        featureGuard.requireModule("trainingprovider");
        TrainProvInvoice invoice = billingService.generateInvoice(TenantContext.getTenantIdAsObject(), clientId, req.periodEnd());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice generated", toResponse(invoice)));
    }

    @PostMapping("/invoices/{id}/send")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> markSent(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(toResponse(billingService.markSent(TenantContext.getTenantIdAsObject(), id))));
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAuthority('TRAININGPROVIDER_ADMIN')")
    @Operation(summary = "Record a payment against an invoice — ADMIN only")
    public ResponseEntity<ApiResponse<InvoiceResponse>> recordPayment(@PathVariable UUID id, @Valid @RequestBody RecordPaymentRequest req) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Payment recorded",
                toResponse(billingService.recordPayment(TenantContext.getTenantIdAsObject(), id, req.amount()))));
    }

    @GetMapping("/invoices/{id}/pdf")
    @PreAuthorize("hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        TrainProvInvoice invoice = billingService.get(tenantId, id);
        TrainProvClient client = clientService.get(tenantId, invoice.getClientId());
        byte[] pdf = pdfService.generateInvoice(invoice, client, fetchProviderName(tenantId));
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + invoice.getInvoiceNumber() + ".pdf\"")
                .body(pdf);
    }

    private String fetchProviderName(TenantId tenantId) {
        try {
            var row = jdbc.queryForMap("SELECT name FROM tenants WHERE id = ?", tenantId.getValue());
            Object name = row.get("name");
            return name != null ? name.toString() : "HandyFlow Training Provider";
        } catch (Exception e) {
            return "HandyFlow Training Provider";
        }
    }

    private InvoiceResponse toResponse(TrainProvInvoice i) {
        return new InvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getIssueDate(), i.getDueDate(), i.getDelegateCount(), i.getSubtotal(), i.getVatAmount(), i.getTotal(),
                i.getAmountPaid(), i.balance(), i.getStatus(), i.getCreatedAt());
    }
}
