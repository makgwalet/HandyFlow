package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpClient;
import za.co.handyflow.platform.legalpractice.domain.model.LpInvoice;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustTransaction;
import za.co.handyflow.platform.legalpractice.domain.repository.LpClientRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpInvoiceRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpMatterRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpTrustTransactionRepository;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
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
 * <p>
 * Controls added in V821: a transfer can never exceed what the invoice still owes; a matter on a movement must be
 * this client's own matter; amounts carry at most cents; every row records the balance it left behind; and an optional
 * idempotency key makes a repeated request return the first result instead of posting again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpTrustTransactionService {

    private static final Set<String> INVOICE_STATUSES_ELIGIBLE_FOR_TRUST_SETTLEMENT = Set.of("SENT", "PARTIALLY_PAID");

    private final LpTrustTransactionRepository trustTxRepo;
    private final LpClientRepository clientRepo;
    private final LpInvoiceRepository invoiceRepo;
    private final LpMatterRepository matterRepo;
    private final LpAccountingPoster accountingPoster;
    private final LpWallGuard walls;
    private final LpKycGate kyc;

    @Transactional(readOnly = true)
    public Page<LpTrustTransactionResponse> listByClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        walls.requireClientAccess(tenantId, clientId);
        var barred = walls.barred(tenantId);
        return trustTxRepo.findByClientExcluding(tenantId, clientId, barred.matterIds(), pageable).map(this::toResponse);
    }

    @Transactional
    public LpTrustTransactionResponse recordReceipt(TenantId tenantId, UUID clientId, RecordTrustReceiptRequest req,
                                                     UUID capturedBy, String capturedByName) {
        String key = cleanKey(req.idempotencyKey());
        Optional<LpTrustTransactionResponse> again = replay(tenantId, clientId, "RECEIPT", req.amount(), key);
        if (again.isPresent()) return again.get();
        requireCents(req.amount());
        LpClient client = findOwnClient(tenantId, clientId);
        requireMatterOfClient(tenantId, clientId, req.matterId());
        // Money is only taken into trust for a client whose due diligence is verified, when the firm has switched the gate on.
        kyc.requireCleared(tenantId, clientId, "receive money into trust");

        LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, req.matterId(), "RECEIPT",
                req.amount(), req.transactionDate(), null, null, req.reference(), capturedBy, capturedByName, req.notes());

        client.increaseTrustBalance(req.amount());
        tx.seal(client.getTrustBalance(), key);
        trustTxRepo.save(tx);
        clientRepo.save(client);

        log.info("Recorded trust RECEIPT={} client={} amount={} tenant={}", tx.getId(), clientId, req.amount(), tenantId);
        return toResponse(tx);
    }

    @Transactional
    public LpTrustTransactionResponse transferToBusiness(TenantId tenantId, UUID clientId, TransferToBusinessRequest req,
                                                          UUID capturedBy, String capturedByName, TrustApproval approval) {
        requireApproval(approval);
        String key = cleanKey(req.idempotencyKey());
        Optional<LpTrustTransactionResponse> again = replay(tenantId, clientId, "TRANSFER_TO_BUSINESS", req.amount(), key);
        if (again.isPresent()) return again.get();
        requireCents(req.amount());
        LpClient client = findOwnClient(tenantId, clientId);
        requireMatterOfClient(tenantId, clientId, req.matterId());
        LpInvoice invoice = loadInvoiceForTransfer(tenantId, clientId, req.invoiceId(), req.amount());

        LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, req.matterId(), "TRANSFER_TO_BUSINESS",
                req.amount(), req.transactionDate(), req.invoiceId(), null, req.reference(), capturedBy, capturedByName, req.notes());

        client.decreaseTrustBalance(req.amount());
        tx.seal(client.getTrustBalance(), key);
        tx.recordApproval(approval.requestId(), approval.approvedBy(), approval.approvedByName());
        trustTxRepo.save(tx);
        clientRepo.save(client);

        invoice.applyPayment(req.amount());
        invoiceRepo.save(invoice);

        accountingPoster.postInvoiceRevenue(tenantId, invoice.getInvoiceNumber(), req.amount());

        log.info("Recorded trust TRANSFER_TO_BUSINESS={} client={} invoice={} amount={} tenant={}",
                tx.getId(), clientId, req.invoiceId(), req.amount(), tenantId);
        return toResponse(tx);
    }

    @Transactional
    public LpTrustTransactionResponse payDisbursement(TenantId tenantId, UUID clientId, PayDisbursementFromTrustRequest req,
                                                       UUID capturedBy, String capturedByName, TrustApproval approval) {
        requireApproval(approval);
        String key = cleanKey(req.idempotencyKey());
        Optional<LpTrustTransactionResponse> again = replay(tenantId, clientId, "DISBURSEMENT_PAYMENT", req.amount(), key);
        if (again.isPresent()) return again.get();
        requireCents(req.amount());
        LpClient client = findOwnClient(tenantId, clientId);
        requireMatterOfClient(tenantId, clientId, req.matterId());

        LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, req.matterId(), "DISBURSEMENT_PAYMENT",
                req.amount(), req.transactionDate(), null, req.payee(), req.reference(), capturedBy, capturedByName, req.notes());

        client.decreaseTrustBalance(req.amount());
        tx.seal(client.getTrustBalance(), key);
        tx.recordApproval(approval.requestId(), approval.approvedBy(), approval.approvedByName());
        trustTxRepo.save(tx);
        clientRepo.save(client);

        log.info("Recorded trust DISBURSEMENT_PAYMENT={} client={} payee={} amount={} tenant={}",
                tx.getId(), clientId, req.payee(), req.amount(), tenantId);
        return toResponse(tx);
    }

    @Transactional
    public LpTrustTransactionResponse refund(TenantId tenantId, UUID clientId, RefundTrustRequest req,
                                             UUID capturedBy, String capturedByName, TrustApproval approval) {
        requireApproval(approval);
        String key = cleanKey(req.idempotencyKey());
        Optional<LpTrustTransactionResponse> again = replay(tenantId, clientId, "REFUND", req.amount(), key);
        if (again.isPresent()) return again.get();
        requireCents(req.amount());
        LpClient client = findOwnClient(tenantId, clientId);
        requireMatterOfClient(tenantId, clientId, req.matterId());

        LpTrustTransaction tx = LpTrustTransaction.create(tenantId, clientId, req.matterId(), "REFUND",
                req.amount(), req.transactionDate(), null, req.payee(), req.reference(), capturedBy, capturedByName, req.notes());

        client.decreaseTrustBalance(req.amount());
        tx.seal(client.getTrustBalance(), key);
        tx.recordApproval(approval.requestId(), approval.approvedBy(), approval.approvedByName());
        trustTxRepo.save(tx);
        clientRepo.save(client);

        log.info("Recorded trust REFUND={} client={} payee={} amount={} tenant={}",
                tx.getId(), clientId, req.payee(), req.amount(), tenantId);
        return toResponse(tx);
    }


    /** Who approved a payment out of trust, and the request it came from. Required for every payment; receipts have none. */
    public record TrustApproval(UUID requestId, UUID approvedBy, String approvedByName) {}

    private static void requireApproval(TrustApproval approval) {
        if (approval == null || approval.requestId() == null || approval.approvedBy() == null) {
            throw new IllegalStateException("A payment out of trust must come from an approved request");
        }
    }

    /**
     * Checks everything a payment would be checked against, without posting anything, so a request that could never be
     * paid is refused when it is made instead of sitting in the queue. Approval runs the same checks again, because the
     * balance and the invoice can change in between.
     */
    @Transactional(readOnly = true)
    public void preflight(TenantId tenantId, UUID clientId, String type, UUID matterId, UUID invoiceId, BigDecimal amount) {
        requireCents(amount);
        LpClient client = findOwnClient(tenantId, clientId);
        requireMatterOfClient(tenantId, clientId, matterId);
        if ("TRANSFER_TO_BUSINESS".equals(type)) {
            if (invoiceId == null) throw new IllegalArgumentException("A transfer to the business account must name the invoice it pays");
            loadInvoiceForTransfer(tenantId, clientId, invoiceId, amount);
        }
        if (client.getTrustBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("The client's trust balance (" + client.getTrustBalance().toPlainString()
                    + ") is less than the amount");
        }
    }

    private LpInvoice loadInvoiceForTransfer(TenantId tenantId, UUID clientId, UUID invoiceId, BigDecimal amount) {
        LpInvoice invoice = invoiceRepo.findActiveById(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("LpInvoice", invoiceId.toString()));
        if (!invoice.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Invoice " + invoiceId + " does not belong to client " + clientId);
        }
        if (!INVOICE_STATUSES_ELIGIBLE_FOR_TRUST_SETTLEMENT.contains(invoice.getStatus())) {
            throw new IllegalStateException(
                    "Invoice must be SENT or PARTIALLY_PAID to settle it from trust, current status: " + invoice.getStatus());
        }
        // The firm may only draw what it has earned and invoiced. The screen checks this too; the server must not rely on it.
        if (amount.compareTo(invoice.getOutstandingBalance()) > 0) {
            throw new IllegalArgumentException("A transfer to the business account cannot be more than the invoice still owes ("
                    + invoice.getOutstandingBalance().toPlainString() + ")");
        }
        return invoice;
    }

    private LpClient findOwnClient(TenantId tenantId, UUID clientId) {
        return clientRepo.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("LpClient", clientId.toString()));
    }

    /** A movement tagged to a matter must be tagged to one of this firm's matters, and to this client's own. */
    private void requireMatterOfClient(TenantId tenantId, UUID clientId, UUID matterId) {
        // Every money movement on a client, and on a matter, passes here: a wall on either refuses it.
        walls.requireScopeAccess(tenantId, matterId, clientId);
        if (matterId == null) return;
        LpMatter matter = matterRepo.findActiveById(tenantId, matterId)
                .orElseThrow(() -> new ResourceNotFoundException("LpMatter", matterId.toString()));
        if (!matter.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("That matter belongs to a different client");
        }
    }

    /** The database stores cents; a third decimal would be rounded away silently, so it is refused instead. */
    private static void requireCents(BigDecimal amount) {
        if (amount != null && amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Use at most two decimal places");
        }
    }

    private static String cleanKey(String key) {
        return key == null || key.isBlank() ? null : key.trim();
    }

    /**
     * A key seen before returns the first result untouched. The same key with a different client, type or amount is a
     * mistake or an attack, and is refused rather than quietly answered with someone else's transaction.
     */
    private Optional<LpTrustTransactionResponse> replay(TenantId tenantId, UUID clientId, String type, BigDecimal amount, String key) {
        if (key == null) return Optional.empty();
        return trustTxRepo.findByIdempotencyKey(tenantId, key).map(existing -> {
            boolean same = existing.getClientId().equals(clientId) && existing.getTransactionType().equals(type)
                    && amount != null && existing.getAmount().compareTo(amount) == 0;
            if (!same) {
                throw new IllegalStateException("That request key was already used for a different trust transaction");
            }
            return toResponse(existing);
        });
    }

    private LpTrustTransactionResponse toResponse(LpTrustTransaction t) {
        return new LpTrustTransactionResponse(t.getId(), t.getClientId(), t.getMatterId(), t.getTransactionType(),
                t.getAmount(), t.getTransactionDate(), t.getInvoiceId(), t.getPayee(), t.getReference(),
                t.getCapturedBy(), t.getCapturedByName(), t.getNotes(), t.getBalanceAfter(), t.getApprovedByName(), t.getCreatedAt());
    }
}
