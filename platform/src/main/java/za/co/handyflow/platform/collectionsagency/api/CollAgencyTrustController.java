package za.co.handyflow.platform.collectionsagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyTrustTransactionService;
import za.co.handyflow.platform.collectionsagency.application.internal.CollAgencyTrustTransactionService.RemittanceResult;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCommissionInvoice;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyTrustTransaction;
import za.co.handyflow.platform.collectionsagency.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * The trust ledger's write and read surface. Debtor payments can be
 * recorded by ordinary MANAGE staff (day-to-day operational work);
 * processing a remittance — which clears a client's whole trust balance,
 * issues a commission invoice, and posts real revenue to the GL — is
 * restricted to ADMIN only, same "financial commit point needs a bigger
 * permission than day-to-day case work" gating debtcollection's own
 * write-off endpoint already established.
 */
@RestController
@RequestMapping("/api/v1/collections-agency")
@RequiredArgsConstructor
@Tag(name = "Collections Agency - Trust Ledger", description = "Debtor payments held in trust and client remittances")
public class CollAgencyTrustController {

    private final CollAgencyTrustTransactionService trustService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/trust-transactions")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "List trust ledger movements for a client — the basis of a trust reconciliation/statement")
    public ResponseEntity<ApiResponse<List<TrustTransactionResponse>>> listForClient(@PathVariable UUID clientId) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                trustService.listForClient(TenantContext.getTenantIdAsObject(), clientId)
                        .stream().map(this::toResponse).toList()));
    }

    @GetMapping("/debtor-accounts/{debtorAccountId}/trust-transactions")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_READ','COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    public ResponseEntity<ApiResponse<List<TrustTransactionResponse>>> listForDebtorAccount(
            @PathVariable UUID debtorAccountId) {
        featureGuard.requireModule("collectionsagency");
        return ResponseEntity.ok(ApiResponse.success(
                trustService.listForDebtorAccount(TenantContext.getTenantIdAsObject(), debtorAccountId)
                        .stream().map(this::toResponse).toList()));
    }

    @PostMapping("/debtor-accounts/{debtorAccountId}/trust-transactions/receipt")
    @PreAuthorize("hasAnyAuthority('COLLECTIONSAGENCY_MANAGE','COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Record a payment received from a debtor and held in trust — never touches the tenant's own GL")
    public ResponseEntity<ApiResponse<TrustTransactionResponse>> recordDebtorPayment(
            @PathVariable UUID debtorAccountId, @Valid @RequestBody RecordDebtorPaymentRequest req) {
        featureGuard.requireModule("collectionsagency");
        CollAgencyTrustTransaction txn = trustService.recordDebtorPayment(TenantContext.getTenantIdAsObject(),
                debtorAccountId, req.amount(), req.transactionDate(), req.reference(), req.notes(),
                TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment recorded", toResponse(txn)));
    }

    @PostMapping("/clients/{clientId}/trust-transactions/remittance")
    @PreAuthorize("hasAuthority('COLLECTIONSAGENCY_ADMIN')")
    @Operation(summary = "Process a remittance — clears the client's whole trust balance, issues and posts a commission invoice, pays out the net")
    public ResponseEntity<ApiResponse<RemittanceResultResponse>> processRemittance(@PathVariable UUID clientId,
            @Valid @RequestBody ProcessRemittanceRequest req) {
        featureGuard.requireModule("collectionsagency");
        RemittanceResult result = trustService.processRemittance(TenantContext.getTenantIdAsObject(), clientId,
                req.remittanceDate() != null ? req.remittanceDate() : java.time.LocalDate.now(),
                req.commissionRatePctOverride(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Remittance processed", toResultResponse(result)));
    }

    private TrustTransactionResponse toResponse(CollAgencyTrustTransaction t) {
        return new TrustTransactionResponse(t.getId(), t.getClientId(), t.getDebtorAccountId(),
                t.getTransactionType(), t.getAmount(), t.getTransactionDate(), t.getReference(), t.getNotes(),
                t.getRecordedByUserId(), t.getCreatedAt());
    }

    private CommissionInvoiceResponse toInvoiceResponse(CollAgencyCommissionInvoice i) {
        return new CommissionInvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getDescription(),
                i.getInvoiceDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(),
                i.getAmountPaid(), i.balance(), i.getStatus(), i.getSentAt(), i.getPaidAt());
    }

    private RemittanceResultResponse toResultResponse(RemittanceResult r) {
        return new RemittanceResultResponse(toResponse(r.transaction()), toInvoiceResponse(r.invoice()),
                r.netPaidToClient(), r.commissionRetained());
    }
}
