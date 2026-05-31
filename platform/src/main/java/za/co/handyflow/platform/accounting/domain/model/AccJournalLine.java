package za.co.handyflow.platform.accounting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acc_journal_lines")
@Getter
@NoArgsConstructor
public class AccJournalLine {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    @Column(name = "journal_entry_id") UUID journalEntryId;
    @Column(name = "account_id")       UUID accountId;
    String description;
    @Column(name = "debit_amount")     BigDecimal debitAmount  = BigDecimal.ZERO;
    @Column(name = "credit_amount")    BigDecimal creditAmount = BigDecimal.ZERO;
    @Column(name = "sort_order")       int sortOrder;
    @Column(name = "created_at")       Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    AccJournalEntry journalEntry;

    public static AccJournalLine debit(UUID tenantId, UUID journalEntryId,
                                       UUID accountId, BigDecimal amount,
                                       String description, int sortOrder) {
        AccJournalLine l = new AccJournalLine();
        l.id             = UUID.randomUUID();
        l.tenantId       = tenantId;
        l.journalEntryId = journalEntryId;
        l.accountId      = accountId;
        l.debitAmount    = amount;
        l.creditAmount   = BigDecimal.ZERO;
        l.description    = description;
        l.sortOrder      = sortOrder;
        l.createdAt      = Instant.now();
        return l;
    }

    public static AccJournalLine credit(UUID tenantId, UUID journalEntryId,
                                        UUID accountId, BigDecimal amount,
                                        String description, int sortOrder) {
        AccJournalLine l = new AccJournalLine();
        l.id             = UUID.randomUUID();
        l.tenantId       = tenantId;
        l.journalEntryId = journalEntryId;
        l.accountId      = accountId;
        l.debitAmount    = BigDecimal.ZERO;
        l.creditAmount   = amount;
        l.description    = description;
        l.sortOrder      = sortOrder;
        l.createdAt      = Instant.now();
        return l;
    }
}