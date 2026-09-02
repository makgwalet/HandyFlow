package za.co.handyflow.platform.bookkeeping.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One debit or credit line of a {@link BkJournalEntry} — mirrors
 * {@code accounting.AccJournalLine}'s debit()/credit() factory shape
 * exactly.
 */
@Entity
@Table(name = "bk_journal_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkJournalLine {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    // Mirrors AccJournalLine's own shape exactly: a raw journalEntryId
    // column for querying, PLUS this read-only association so
    // BkJournalEntry's own @OneToMany(mappedBy = "journalEntry") can
    // resolve — mappedBy must name a relationship field, not a bare
    // column, or the entity fails to map at all.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    private BkJournalEntry journalEntry;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    private String description;

    @Column(name = "debit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static BkJournalLine debit(UUID journalEntryId, UUID accountId, BigDecimal amount,
                                       String description, int sortOrder) {
        BkJournalLine l = new BkJournalLine();
        l.journalEntryId = journalEntryId;
        l.accountId = accountId;
        l.debitAmount = amount;
        l.creditAmount = BigDecimal.ZERO;
        l.description = description;
        l.sortOrder = sortOrder;
        l.createdAt = Instant.now();
        return l;
    }

    public static BkJournalLine credit(UUID journalEntryId, UUID accountId, BigDecimal amount,
                                        String description, int sortOrder) {
        BkJournalLine l = new BkJournalLine();
        l.journalEntryId = journalEntryId;
        l.accountId = accountId;
        l.debitAmount = BigDecimal.ZERO;
        l.creditAmount = amount;
        l.description = description;
        l.sortOrder = sortOrder;
        l.createdAt = Instant.now();
        return l;
    }
}
