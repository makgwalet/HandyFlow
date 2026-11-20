package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * A request to pay money out of a client's trust account. One person asks, a different person approves, and only then does
 * the money move. Never deleted: the row is the permanent record of who asked, who decided, when, and why.
 * <p>
 * PENDING → APPROVED (the payment was posted; {@code resultTransactionId} points at the ledger row)
 *         → REJECTED (decider gives a reason) → CANCELLED (only the person who asked, while still pending).
 * <p>
 * Segregation of duties: the approver must not be the requester. A sole practitioner has no second person, so the firm may
 * switch on an explicit exception; even then the approver must write down why, and the request is marked
 * {@code selfApproved} so it can be found later.
 */
@Entity
@Table(name = "lp_trust_payment_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpTrustPaymentRequest {

    public static final Set<String> TYPES = Set.of("TRANSFER_TO_BUSINESS", "DISBURSEMENT_PAYMENT", "REFUND");
    public static final int MIN_REASON = 10;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "matter_id")
    private UUID matterId;

    /** TRANSFER_TO_BUSINESS | DISBURSEMENT_PAYMENT | REFUND — the same names as the ledger. */
    @Column(name = "payment_type", nullable = false, length = 30)
    private String paymentType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(length = 255)
    private String payee;

    @Column(length = 255)
    private String reference;

    private String notes;

    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_by_name", length = 255)
    private String requestedByName;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_by_name", length = 255)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Why it was rejected, or, for a self-approval, why one person was allowed to do both. */
    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "self_approved", nullable = false)
    private boolean selfApproved = false;

    @Column(name = "result_transaction_id")
    private UUID resultTransactionId;

    @Column(name = "idempotency_key", length = 80, updatable = false)
    private String idempotencyKey;

    @Version
    private Long version;

    public static LpTrustPaymentRequest request(TenantId tenantId, UUID clientId, UUID matterId, String paymentType,
                                                BigDecimal amount, LocalDate transactionDate, UUID invoiceId, String payee,
                                                String reference, String notes, UUID requestedBy, String requestedByName, String idempotencyKey) {
        if (!TYPES.contains(paymentType)) {
            throw new IllegalArgumentException("A trust payment must be TRANSFER_TO_BUSINESS, DISBURSEMENT_PAYMENT or REFUND");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("The amount must be above 0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Use at most two decimal places");
        }
        boolean needsInvoice = "TRANSFER_TO_BUSINESS".equals(paymentType);
        if (needsInvoice && invoiceId == null) {
            throw new IllegalArgumentException("A transfer to the business account must name the invoice it pays");
        }
        if (!needsInvoice && invoiceId != null) {
            throw new IllegalArgumentException("Only a transfer to the business account names an invoice");
        }
        if (!needsInvoice && (payee == null || payee.isBlank())) {
            throw new IllegalArgumentException("Say who is being paid");
        }
        if (needsInvoice && payee != null && !payee.isBlank()) {
            throw new IllegalArgumentException("A transfer to the business account has no payee");
        }
        if (requestedBy == null) {
            throw new IllegalArgumentException("A request must say who made it");
        }
        LpTrustPaymentRequest r = new LpTrustPaymentRequest();
        r.tenantId = tenantId;
        r.clientId = clientId;
        r.matterId = matterId;
        r.paymentType = paymentType;
        r.amount = amount;
        r.transactionDate = transactionDate;
        r.invoiceId = invoiceId;
        r.payee = needsInvoice ? null : payee.trim();
        r.reference = reference;
        r.notes = notes;
        r.requestedBy = requestedBy;
        r.requestedByName = requestedByName;
        r.requestedAt = Instant.now();
        r.idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        return r;
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isRequestedBy(UUID userId) {
        return userId != null && userId.equals(requestedBy);
    }

    /**
     * May this person approve? Anyone but the requester; the requester only when the firm allows a single person to act
     * for both, and only with a written reason.
     */
    public void requireCanBeApprovedBy(UUID approver, boolean selfApprovalAllowed, String selfApprovalReason) {
        requirePending();
        if (approver == null) {
            throw new IllegalArgumentException("An approval must say who made it");
        }
        if (isRequestedBy(approver)) {
            if (!selfApprovalAllowed) {
                throw new IllegalStateException("A trust payment must be approved by someone other than the person who requested it");
            }
            if (selfApprovalReason == null || selfApprovalReason.trim().length() < MIN_REASON) {
                throw new IllegalArgumentException("Explain in at least " + MIN_REASON + " characters why you are approving your own request");
            }
        }
    }

    public void markApproved(UUID approver, String approverName, String selfApprovalReason, UUID resultTransactionId) {
        requireCanBeApprovedBy(approver, true, isRequestedBy(approver) ? selfApprovalReason : null);
        this.status = "APPROVED";
        this.decidedBy = approver;
        this.decidedByName = approverName;
        this.decidedAt = Instant.now();
        this.selfApproved = isRequestedBy(approver);
        this.decisionReason = this.selfApproved ? selfApprovalReason.trim() : null;
        this.resultTransactionId = resultTransactionId;
    }

    public void reject(UUID decider, String deciderName, String reason) {
        requirePending();
        if (reason == null || reason.trim().length() < MIN_REASON) {
            throw new IllegalArgumentException("Give a reason of at least " + MIN_REASON + " characters");
        }
        this.status = "REJECTED";
        this.decidedBy = decider;
        this.decidedByName = deciderName;
        this.decidedAt = Instant.now();
        this.decisionReason = reason.trim();
    }

    public void cancel(UUID by, String byName) {
        requirePending();
        if (!isRequestedBy(by)) {
            throw new IllegalStateException("Only the person who made a request can withdraw it");
        }
        this.status = "CANCELLED";
        this.decidedBy = by;
        this.decidedByName = byName;
        this.decidedAt = Instant.now();
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("This request is already " + status.toLowerCase());
        }
    }
}
