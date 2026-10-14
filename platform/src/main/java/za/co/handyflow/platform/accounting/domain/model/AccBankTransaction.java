package za.co.handyflow.platform.accounting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "acc_bank_transactions")
@Getter
@NoArgsConstructor
public class AccBankTransaction {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    @Column(name = "bank_account_id")  UUID bankAccountId;
    @Column(name = "transaction_date") LocalDate transactionDate;
    String description;
    String reference;
    BigDecimal amount;
    @Column(name = "transaction_type") String transactionType;
    @Column(name = "balance_after")    BigDecimal balanceAfter;
    boolean reconciled = false;
    @Column(name = "reconciled_at")    Instant reconciledAt;
    @Column(name = "journal_line_id")  UUID journalLineId;
    // Finance patch F12: who matched this line, how (MATCHED, NEW_JOURNAL or RULE), and by which rule.
    @Column(name = "reconciled_by")    UUID reconciledBy;
    @Column(name = "reconcile_kind")   String reconcileKind;
    @Column(name = "rule_id")          UUID ruleId;
    @Column(name = "created_at")       Instant createdAt;
    @Column(name = "updated_at")       Instant updatedAt;

    public static AccBankTransaction create(TenantId tenantId, UUID bankAccountId,
                                            LocalDate transactionDate, String description,
                                            String reference, BigDecimal amount,
                                            String transactionType, BigDecimal balanceAfter) {
        AccBankTransaction t = new AccBankTransaction();
        t.id              = UUID.randomUUID();
        t.tenantId        = tenantId.getValue();
        t.bankAccountId   = bankAccountId;
        t.transactionDate = transactionDate;
        t.description     = description;
        t.reference       = reference;
        t.amount          = amount;
        t.transactionType = transactionType;
        t.balanceAfter    = balanceAfter;
        t.reconciled      = false;
        t.createdAt       = Instant.now();
        t.updatedAt       = Instant.now();
        return t;
    }

    /** Finance patch F12: reconciles and records who did it and how. */
    public void reconcile(UUID journalLineId, UUID by, String kind, UUID ruleId) {
        reconcile(journalLineId);
        this.reconciledBy  = by;
        this.reconcileKind = kind;
        this.ruleId        = ruleId;
    }

    public void reconcile(UUID journalLineId) {
        this.reconciled    = true;
        this.journalLineId = journalLineId;
        this.reconciledAt  = Instant.now();
        this.updatedAt     = Instant.now();
    }
}