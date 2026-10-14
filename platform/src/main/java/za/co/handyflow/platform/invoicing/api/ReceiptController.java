package za.co.handyflow.platform.invoicing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.invoicing.application.internal.ReceiptService;
import za.co.handyflow.platform.invoicing.dto.ReceiptDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Finance patch F8: money received from customers, allocating it to invoices, and customer credit. */
@RestController
@RequestMapping("/api/v1/invoicing")
@RequiredArgsConstructor
@Tag(name = "Receipts", description = "Customer payments, allocation to invoices and customer credit")
public class ReceiptController {

    private final ReceiptService receiptService;

    @PostMapping("/receipts")
    @PreAuthorize("hasAuthority('INVOICE_PAYMENT')")
    @Operation(summary = "Record money from a customer, settle one or more invoices, keep the rest as their credit")
    public ResponseEntity<ApiResponse<ReceiptResponse>> receive(@RequestBody ReceiveMoneyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment received",
                receiptService.receive(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/receipts/{id}/allocations")
    @PreAuthorize("hasAuthority('INVOICE_PAYMENT')")
    @Operation(summary = "Use a receipt's remaining credit to settle more of the customer's invoices")
    public ResponseEntity<ApiResponse<ReceiptResponse>> allocate(@PathVariable UUID id, @RequestBody AllocateRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Credit applied",
                receiptService.allocate(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/receipts/{id}/refunds")
    @PreAuthorize("hasAuthority('INVOICE_REFUND')")
    @Operation(summary = "Pay (part of) this credit back to the customer from a bank account, with the reason")
    public ResponseEntity<ApiResponse<ReceiptResponse>> refund(@PathVariable UUID id, @RequestBody RefundRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Refund recorded",
                receiptService.refund(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/invoices/{invoiceId}/credit")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Credit that came from this invoice's credit notes (a walk-in client's credit is found here)")
    public ResponseEntity<ApiResponse<List<ReceiptResponse>>> creditFromInvoice(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                receiptService.creditFromInvoice(TenantContext.getTenantIdAsObject(), invoiceId)));
    }

    @GetMapping("/receipts/{id}")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "A receipt and the invoices it settled")
    public ResponseEntity<ApiResponse<ReceiptResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", receiptService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/customers/{customerId}/account")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "What a customer owes, their open invoices (oldest first) and the credit they have to use")
    public ResponseEntity<ApiResponse<CustomerAccount>> account(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                receiptService.account(TenantContext.getTenantIdAsObject(), customerId)));
    }

    @GetMapping("/customers/{customerId}/receipts")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "A customer's receipts, newest first")
    public ResponseEntity<ApiResponse<List<ReceiptResponse>>> receipts(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                receiptService.forCustomer(TenantContext.getTenantIdAsObject(), customerId)));
    }
}
