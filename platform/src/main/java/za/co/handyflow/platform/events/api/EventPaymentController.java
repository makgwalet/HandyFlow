package za.co.handyflow.platform.events.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.events.application.internal.*;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Paid ticketing: checkout, payments, refunds, and the profitability view.
 * Provider callbacks arrive separately, at PaymentWebhookController.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event payments", description = "Paid checkout, payment confirmation, refunds and profitability")
public class EventPaymentController {

    private final PaymentCheckoutService   checkoutService;
    private final PaymentLedgerService     ledger;
    private final PaymentRefundService     refundService;
    private final EventProfitabilityService profitability;
    private final EventPaymentReportService paymentReport;

    // ── Checkout ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/checkout")
    @PreAuthorize(EventsPermissions.REGISTRATION)
    @Operation(summary = "Reserve a ticket on a paid event and start payment — the seat is held until paid or the hold expires")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @PathVariable UUID id, @Valid @RequestBody StartCheckoutRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Checkout started",
                checkoutService.startCheckout(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/payments")
    @PreAuthorize(EventsPermissions.PAYMENTS_READ)
    @Operation(summary = "List payments, newest first; filter by status")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> listPayments(
            @PathVariable UUID id,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                ledger.listPayments(TenantContext.getTenantIdAsObject(), id, status, pageable)));
    }

    @GetMapping("/{id}/payments/{paymentId}")
    @PreAuthorize(EventsPermissions.PAYMENTS_READ)
    @Operation(summary = "One payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID id, @PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                ledger.getPayment(TenantContext.getTenantIdAsObject(), id, paymentId)));
    }

    @PostMapping("/{id}/payments/{paymentId}/confirm")
    @PreAuthorize(EventsPermissions.PAYMENTS_MANAGE)
    @Operation(summary = "Confirm money received outside a provider (EFT, cash, card machine). The amount must equal the price; "
            + "any other amount does not issue the ticket and flags the payment REQUIRES_REFUND")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(
            @PathVariable UUID id, @PathVariable UUID paymentId, @Valid @RequestBody ConfirmPaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Payment recorded",
                ledger.confirmManually(TenantContext.getTenantIdAsObject(), id, paymentId,
                        req.amountReceived(), req.reference(), req.fee())));
    }

    @PostMapping("/{id}/payments/{paymentId}/cancel")
    @PreAuthorize(EventsPermissions.PAYMENTS_MANAGE)
    @Operation(summary = "Cancel an open checkout — releases the held seat")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelCheckout(
            @PathVariable UUID id, @PathVariable UUID paymentId, @Valid @RequestBody FailRefundRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Checkout cancelled",
                ledger.cancelCheckout(TenantContext.getTenantIdAsObject(), id, paymentId, req.reason().trim())));
    }

    // ── Refunds ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/refunds")
    @PreAuthorize(EventsPermissions.PAYMENTS_READ)
    @Operation(summary = "List refunds, newest first")
    public ResponseEntity<ApiResponse<List<RefundResponse>>> listRefunds(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                ledger.listRefunds(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/payments/{paymentId}/refunds")
    @PreAuthorize(EventsPermissions.PAYMENTS_REFUND)
    @Operation(summary = "Refund all or part of a payment, optionally cancelling the ticket. For MANUAL payments the refund stays "
            + "REQUESTED until you mark it complete")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @PathVariable UUID id, @PathVariable UUID paymentId, @Valid @RequestBody RefundPaymentRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Refund requested",
                refundService.requestRefund(TenantContext.getTenantIdAsObject(), id, paymentId, req)));
    }

    @PostMapping("/{id}/refunds/{refundId}/complete")
    @PreAuthorize(EventsPermissions.PAYMENTS_REFUND)
    @Operation(summary = "Mark a requested refund as paid out")
    public ResponseEntity<ApiResponse<RefundResponse>> completeRefund(
            @PathVariable UUID id, @PathVariable UUID refundId,
            @Valid @RequestBody(required = false) CompleteRefundRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Refund completed",
                refundService.completeRefund(TenantContext.getTenantIdAsObject(), id, refundId,
                        req != null ? req.reference() : null)));
    }

    @PostMapping("/{id}/refunds/{refundId}/fail")
    @PreAuthorize(EventsPermissions.PAYMENTS_REFUND)
    @Operation(summary = "Mark a requested refund as failed — frees the amount so it can be refunded again")
    public ResponseEntity<ApiResponse<RefundResponse>> failRefund(
            @PathVariable UUID id, @PathVariable UUID refundId, @Valid @RequestBody FailRefundRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Refund marked failed",
                refundService.failRefund(TenantContext.getTenantIdAsObject(), id, refundId, req.reason())));
    }

    @PostMapping("/{id}/payments/refund-all")
    @PreAuthorize(EventsPermissions.PAYMENTS_REFUND)
    @Operation(summary = "CANCELLED events only: raise a full refund for every payment still holding money (max 200 per call)")
    public ResponseEntity<ApiResponse<BulkRefundResponse>> refundAll(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Refunds raised",
                refundService.refundAllForCancelledEvent(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Profitability & report ────────────────────────────────────────────────

    @GetMapping("/{id}/dashboard/profitability")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "Net revenue against forecast and invoiced cost, margin and per-attendee figures")
    public ResponseEntity<ApiResponse<ProfitabilityResponse>> profitability(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                profitability.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/reports/payments.csv")
    @PreAuthorize(EventsPermissions.PAYMENTS_READ)
    @Operation(summary = "Payments CSV for reconciliation — ticket numbers and amounts, no guest names; audited")
    public ResponseEntity<byte[]> paymentsCsv(@PathVariable UUID id) {
        EventReportService.Export export = paymentReport.paymentsCsv(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.content());
    }
}