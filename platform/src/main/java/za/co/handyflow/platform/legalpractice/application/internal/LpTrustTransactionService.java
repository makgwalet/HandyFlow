package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpClient;
import za.co.handyflow.platform.legalpractice.domain.model.LpInvoice;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustTransaction;
import za.co.handyflow.platform.legalpractice.domain.repository.LpClientRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpInvoiceRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpTrustTransactionRepository;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Set;
import java.util.UUID;

/**
 * The compliance-critical centerpiece of this module — every trust money
 * movement, mirroring {@code CollAgencyTrustTransactionService}'s own
 * split of responsibility: this class validates inputs, calls
 * {@link LpTrustTransaction#create}, persists the (append-only, immutable)
 * row, then adjusts {@link LpClient#getTrustBalance()} via its own
 * overdraw-guarded increase/decrease pair, IN THE SAME {@code @Transactional}
 * method — so a trust row can never exist without the client's balance
 * reflecting it, and vice versa.
 * <p>
 * Deliberately does NOT re-validate the RECEIPT/TRANSFER_TO_BUSINESS/
 * DISBURSEMENT_PAYMENT/REFUND required/forbidden field rules —
 * {@link LpTrustTransaction#create} already enforces those itself and
 * throws {@code IllegalArgumentException} on violation; this class lets
 * that exception propagate to {@code GlobalExceptionHandler} unchanged,
 * exactly as the module brief requires.
 * <p>
 * {@code transferToBusiness()} is the one type that also touches an
 * {@link LpInvoice}: it requires an existing SENT or PARTIALLY_PAID
 * invoice belonging to the same client, applies the payment to that
 * invoice (the trust deposit is settling real, earned fees — the same
 * economic event as a business-account payment, just funded from trust
 * instead of a bank transfer), and posts the same AR/Revenue GL journal
 * {@code LpBillingService.recordPayment()} posts for an ordinary payment,
 * via the shared {@link LpAccountingPoster} (try/catch, non-blocking).
 * RECEIPT/DISBURSEMENT_PAYMENT/REFUND never touch the GL — that money was
 * never the firm's own revenue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpTrustTransactionService {

    private static final Set<String> INVOICE_STATUSES_ELIGIBLE_FOR_TRUST_SETTLEMENT = Set.of("SENT", "PARTIALLY_PAID");

    private final LpTrustTransactionRepository trustTxRepo;
    private final LpClientRepository clientRepo;
    private final LpInvoiceRepository invoiceRepo;
    private final LpAccountingPoster accountingPoster;

    @Transactional(readOnly = true)
    public Page<LpTrustTransactionResponse> listByClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        return trustTxRepo.findByClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional
    public LpTrustTransactionResponse recordReceipt(TenantId tenantId, UUID clientId, RecordTrustReceiptRequest req,
                                                     UUID capturedBy, String capturedByName) {
        LpClient client = findOwnClient(tenantId, clientId);

        LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, req.matterId(), "RECEIPT",
                req.amount(), req.transactionDate(), null, null, req.reference(), capturedBy, capturedByName, req.notes());
        trustTxRepo.save(tx);

        client.increaseTrustBalance(req.amount());
        clientRepo.save(client);

        log.info("Recorded trust RECEIPT={} client={} amount={} tenant={}", tx.getId(), clientId, req.amount(), tenantId);
        return toResponse(tx, client.getTrustBalance());
    }

    @Transactional
    public LpTrustTransactionResponse transferToBusiness(TenantId tenantId, UUID clientId, TransferToBusinessRequest req,
                                                          UUID capturedBy, String capturedByName) {
        LpClient client = findOwnClient(tenantId, clientId);
        LpInvoice invoice = invoiceRepo.findActiveById(tenantId, req.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("LpInvoice", req.invoiceId().toString()));
        if (!invoice.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Invoice " + req.invoiceId() + " does not belong to client " + clientId);
        }
        if (!INVOICE_STATUSES_ELIGIBLE_FOR_TRUST_SETTLEMENT.contains(invoice.getStatus())) {
            throw new IllegalStateException(
                    "Invoice must be SENT or PARTIALLY_PAID to settle it from trust, current status: " + invoice.getStatus());
        }

        LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, req.matterId(), "TRANSFER_TO_BUSINESS",
                req.amount(), req.transactionDate(), req.invoiceId(), null, req.reference(), capturedBy, capturedByName, req.notes());
        trustTxRepo.save(tx);

        client.decreaseTrustBalance(req.amount());
        clientRepo.save(client);

        invoice.applyPayment(req.amount());
        invoiceRepo.save(invoice);

        accountingPoster.postInvoiceRevenue(tenantId, invoice.getInvoiceNumber(), req.amount());

        log.info("Recorded trust TRANSFER_TO_BUSINESS={} client={} invoice={} amount={} tenant={}",
                tx.getId(), clientId, req.invoiceId(), req.amount(), tenantId);
        return toResponse(tx, client.getTrustBalance());
    }

    @Transactional
    public LpTrustTransactionResponse payDisbursement(TenantId tenantId, UUID clientId, PayDisbursementFromTrustRequest req,
                                                       UUID capturedBy, String capturedByName) {
        LpClient client = findOwnClient(tenantId, clientId);

        LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, req.matterId(), "DISBURSEMENT_PAYMENT",
                req.amount(), req.transactionDate(), null, req.payee(), req.reference(), capturedBy, capturedByName, req.notes());
        trustTxRepo.save(tx);

        client.decreaseTrustBalance(req.amount());
        clientRepo.save(client);

        log.info("Recorded trust DISBURSEMENT_PAYMENT={} client={} payee={} amount={} tenant={}",
                tx.getId(), clientId, req.payee(), req.amount(), tenantId);
        return toResponse(tx, client.getTrustBalance());
    }

    @Transactional
    public LpTrustTransactionResponse refund(TenantId tenantId, UUID clientId, RefundTrustRequest req,
                                             UUID capturedBy, String capturedByName) {
        LpClient client = findOwnClient(tenantId, clientId);

        LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, req.matterId(), "REFUND",
                req.amount(), req.transactionDate(), null, req.payee(), req.reference(), capturedBy, capturedByName, req.notes());
        trustTxRepo.save(tx);

        client.decreaseTrustBalance(req.amount());
        clientRepo.save(client);

        log.info("Recorded trust REFUND={} client={} payee={} amount={} tenant={}",
                tx.getId(), clientId, req.payee(), req.amount(), tenantId);
        return toResponse(tx, client.getTrustBalance());
    }

    private LpClient findOwnClient(TenantId tenantId, UUID clientId) {
        return clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("LpClient", clientId.toString()));
    }

    private LpTrustTransactionResponse toResponse(LpTrustTransaction t) {
        return toResponse(t, null);
    }

    private LpTrustTransactionResponse toResponse(LpTrustTransaction t, java.math.BigDecimal balanceAfter) {
        return new LpTrustTransactionResponse(t.getId(), t.getClientId(), t.getMatterId(), t.getTransactionType(),
                t.getAmount(), t.getTransactionDate(), t.getInvoiceId(), t.getPayee(), t.getReference(),
                t.getCapturedBy(), t.getCapturedByName(), t.getNotes(), balanceAfter, t.getCreatedAt());
    }
}
