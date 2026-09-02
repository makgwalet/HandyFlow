package za.co.handyflow.platform.bookkeeping.domain.model;

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
 * One imported bank statement line for a client's {@link BkBankAccount}
 * — mirrors {@code accounting.AccBankTransaction} exactly: CSV import,
 * duplicate-skip-not-error semantics, reconcile-against-existing-
 * journal-line, always-positive amount + transactionType shape.
 */
@Entity
@Table(name = "bk_bank_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkBankTransaction {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    private String description;
    private String reference;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType; // CREDIT (money in), DEBIT (money out)

    @Column(name = "balance_after", precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private boolean reconciled = false;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "journal_line_id")
    private UUID journalLineId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BkBankTransaction create(TenantId tenantId, UUID clientId, UUID bankAccountId,
                                            LocalDate transactionDate, String description, String reference,
                                            BigDecimal amount, String transactionType, BigDecimal balanceAfter) {
        BkBankTransaction t = new BkBankTransaction();
        t.tenantId = tenantId;
        t.clientId = clientId;
        t.bankAccountId = bankAccountId;
        t.transactionDate = transactionDate;
        t.description = description;
        t.reference = reference;
        t.amount = amount;
        t.transactionType = transactionType;
        t.balanceAfter = balanceAfter;
        t.reconciled = false;
        t.createdAt = Instant.now();
        t.updatedAt = Instant.now();
        return t;
    }

    public void reconcile(UUID journalLineId) {
        if (reconciled) throw new IllegalStateException("This transaction is already reconciled");
        this.reconciled = true;
        this.journalLineId = journalLineId;
        this.reconciledAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
