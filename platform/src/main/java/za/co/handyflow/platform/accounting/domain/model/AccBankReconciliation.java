package za.co.handyflow.platform.accounting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A bank account reconciled to a date and signed off (finance patch F12). */
@Entity
@Table(name = "acc_bank_reconciliations")
@Getter
@NoArgsConstructor
public class AccBankReconciliation {

    @Id UUID id;
    @Column(name = "tenant_id")            UUID tenantId;
    @Column(name = "bank_account_id")      UUID bankAccountId;
    @Column(name = "period_end")           LocalDate periodEnd;
    @Column(name = "statement_balance")    BigDecimal statementBalance;
    @Column(name = "book_balance")         BigDecimal bookBalance;
    @Column(name = "unmatched_book_count") int unmatchedBookCount;
    @Column(name = "unmatched_book_net")   BigDecimal unmatchedBookNet;
    BigDecimal difference;
    String note;
    String status = "SIGNED_OFF";
    @Column(name = "signed_by")            UUID signedBy;
    @Column(name = "signed_at")            Instant signedAt;
    @Column(name = "reopened_by")          UUID reopenedBy;
    @Column(name = "reopened_at")          Instant reopenedAt;
    @Column(name = "reopen_reason")        String reopenReason;

    public static AccBankReconciliation signOff(TenantId tenantId, UUID bankAccountId, LocalDate periodEnd,
                                                BigDecimal statementBalance, BigDecimal bookBalance,
                                                int unmatchedBookCount, BigDecimal unmatchedBookNet,
                                                BigDecimal difference, String note, UUID by) {
        AccBankReconciliation r = new AccBankReconciliation();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId.getValue();
        r.bankAccountId = bankAccountId;
        r.periodEnd = periodEnd;
        r.statementBalance = statementBalance;
        r.bookBalance = bookBalance;
        r.unmatchedBookCount = unmatchedBookCount;
        r.unmatchedBookNet = unmatchedBookNet;
        r.difference = difference;
        r.note = note == null || note.trim().isEmpty() ? null : note.trim();
        r.signedBy = by;
        r.signedAt = Instant.now();
        return r;
    }

    public void reopen(UUID by, String reason) {
        if (!"SIGNED_OFF".equals(status)) throw new IllegalStateException("Only a signed-off reconciliation can be reopened");
        this.status = "REOPENED";
        this.reopenedBy = by;
        this.reopenedAt = Instant.now();
        this.reopenReason = reason;
    }
}
