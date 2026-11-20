package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One month-end (or other date) reconciliation of the trust bank account to the trust ledger.
 * DRAFT (being prepared; the screen works the figures out live) → SUBMITTED (figures frozen, balanced) → APPROVED (period locked).
 * A submitted one can be returned to draft. Never deleted; an approved one never changes (the database enforces both).
 */
@Entity
@Table(name = "lp_trust_reconciliations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpTrustReconciliation {

    public static final int MIN_NOTE = 10;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "statement_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal statementBalance;

    @Column(name = "statement_note", length = 500)
    private String statementNote;

    @Column(name = "cash_book_balance", precision = 15, scale = 2)
    private BigDecimal cashBookBalance;

    @Column(name = "client_ledger_total", precision = 15, scale = 2)
    private BigDecimal clientLedgerTotal;

    @Column(name = "outstanding_deposits", precision = 15, scale = 2)
    private BigDecimal outstandingDeposits;

    @Column(name = "outstanding_payments", precision = 15, scale = 2)
    private BigDecimal outstandingPayments;

    @Column(name = "adjusted_bank_balance", precision = 15, scale = 2)
    private BigDecimal adjustedBankBalance;

    @Column(precision = 15, scale = 2)
    private BigDecimal difference;

    @Column(name = "ledger_mismatches")
    private Integer ledgerMismatches;

    @Column(name = "prepared_by", nullable = false)
    private UUID preparedBy;

    @Column(name = "prepared_by_name")
    private String preparedByName;

    @Column(name = "prepared_at", nullable = false)
    private Instant preparedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "return_reason")
    private String returnReason;

    @Column(name = "returned_by_name")
    private String returnedByName;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_by_name")
    private String approvedByName;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_note")
    private String approvalNote;

    @Column(name = "self_approved", nullable = false)
    private boolean selfApproved;

    @Version
    private Long version;

    public static LpTrustReconciliation start(TenantId tenantId, LocalDate periodEnd, BigDecimal statementBalance, String note, UUID by, String byName) {
        LpTrustReconciliation r = new LpTrustReconciliation();
        r.tenantId = tenantId;
        r.periodEnd = periodEnd;
        r.status = "DRAFT";
        r.statementBalance = scale(statementBalance);
        r.statementNote = note;
        r.preparedBy = by;
        r.preparedByName = byName;
        r.preparedAt = Instant.now();
        return r;
    }

    public void updateStatement(BigDecimal balance, String note) {
        requireStatus("DRAFT", "The statement balance can only be changed while the reconciliation is a draft");
        this.statementBalance = scale(balance);
        this.statementNote = note;
    }

    /** Freezes the figures. Only a balanced reconciliation can be submitted. */
    public void submit(BigDecimal cashBook, BigDecimal clientTotal, BigDecimal deposits, BigDecimal payments, BigDecimal adjusted,
                       BigDecimal difference, int mismatches, UUID by) {
        requireStatus("DRAFT", "Only a draft can be submitted");
        if (difference.signum() != 0) throw new IllegalStateException("The reconciliation does not balance: " + difference.toPlainString() + " is unexplained");
        if (cashBook.compareTo(clientTotal) != 0) throw new IllegalStateException("The client ledgers do not add up to the cash book");
        this.cashBookBalance = scale(cashBook);
        this.clientLedgerTotal = scale(clientTotal);
        this.outstandingDeposits = scale(deposits);
        this.outstandingPayments = scale(payments);
        this.adjustedBankBalance = scale(adjusted);
        this.difference = scale(difference);
        this.ledgerMismatches = mismatches;
        this.submittedBy = by;
        this.submittedAt = Instant.now();
        this.status = "SUBMITTED";
    }

    public void returnToDraft(String reason, String byName) {
        requireStatus("SUBMITTED", "Only a submitted reconciliation can be returned");
        if (reason == null || reason.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Say why it is being returned (at least " + MIN_NOTE + " characters)");
        this.returnReason = reason.trim();
        this.returnedByName = byName;
        this.cashBookBalance = null; this.clientLedgerTotal = null; this.outstandingDeposits = null; this.outstandingPayments = null;
        this.adjustedBankBalance = null; this.difference = null; this.ledgerMismatches = null;
        this.submittedBy = null; this.submittedAt = null;
        this.status = "DRAFT";
    }

    /** {@code selfAllowed} is the firm's written-down setting; without it the approver must not be the person who submitted. */
    public void approve(UUID by, String byName, String note, boolean selfAllowed) {
        requireStatus("SUBMITTED", "Only a submitted reconciliation can be approved");
        boolean self = by.equals(submittedBy);
        if (self && !selfAllowed) throw new IllegalStateException("A different person must approve this reconciliation");
        if (ledgerMismatches != null && ledgerMismatches > 0 && (note == null || note.trim().length() < MIN_NOTE)) {
            throw new IllegalArgumentException("Some client balances disagree with the ledger; say what you checked (at least " + MIN_NOTE + " characters)");
        }
        this.approvedBy = by;
        this.approvedByName = byName;
        this.approvedAt = Instant.now();
        this.approvalNote = note == null || note.isBlank() ? null : note.trim();
        this.selfApproved = self;
        this.status = "APPROVED";
    }

    private void requireStatus(String expected, String message) {
        if (!expected.equals(status)) throw new IllegalStateException(message);
    }

    private static BigDecimal scale(BigDecimal v) { return v.setScale(2, java.math.RoundingMode.HALF_UP); }
}
