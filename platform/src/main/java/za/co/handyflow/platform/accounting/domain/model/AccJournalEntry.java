package za.co.handyflow.platform.accounting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AccJournalEntry — this tenant's own internal general ledger.
 * <p>
 * ARCHITECTURAL DECISION (confirmed with the product owner, not a bug):
 * this ledger is deliberately independent from the Accountant module's
 * own journal (AccJournal / prac_journals / prac_journal_lines), which
 * represents an external accounting firm's engagement records for this
 * tenant — a different service, kept separately by design, not merely
 * a different table for the same books. A tenant using both modules
 * will have two trial balances that this system never reconciles
 * against each other; that is the intended behaviour, not a gap to
 * close. Do not add a reconciliation path or merge these two ledgers
 * without a fresh product decision — the separation exists specifically
 * because AccJournal represents work done BY an external party, not
 * work done substituting for the tenant's own internal bookkeeping.
 */
@Entity
@Table(name = "acc_journal_entries")
@Getter
@NoArgsConstructor
public class AccJournalEntry {

    @Id UUID id;
    @Column(name = "tenant_id")    UUID tenantId;
    @Column(name = "entry_number") String entryNumber;
    @Column(name = "entry_date")   LocalDate entryDate;
    String description;
    String reference;
    @Column(name = "entry_type")   String entryType;
    String status;
    @Column(name = "total_debit")  BigDecimal totalDebit  = BigDecimal.ZERO;
    @Column(name = "total_credit") BigDecimal totalCredit = BigDecimal.ZERO;
    @Column(name = "posted_at")    Instant postedAt;
    @Column(name = "created_by")   UUID createdBy;
    /** Finance patch F2: who posted it; null when the system posted it. */
    @Column(name = "posted_by")    UUID postedBy;
    /** Finance patch F2: on a reversal, the journal it reverses. */
    @Column(name = "reverses_entry_id") UUID reversesEntryId;
    @Column(name = "created_at")   Instant createdAt;
    @Column(name = "updated_at")   Instant updatedAt;
    @Column(name = "deleted_at")   Instant deletedAt;
    @Version long version;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC")
    List<AccJournalLine> lines = new ArrayList<>();

    public static AccJournalEntry create(TenantId tenantId, String entryNumber,
                                         LocalDate entryDate, String description,
                                         String reference, String entryType) {
        return create(tenantId, entryNumber, entryDate, description, reference, entryType, null);
    }

    // createdBy is the person who made the journal: the manual "New Journal Entry" flow, a drafted reversal, or a
    // bank line reconciled with a new journal (finance patch F2). It stays null for journals the system makes from a
    // transaction approved where it started (invoice, bill, expense claim, credit note, POS session). A journal with a
    // maker must be posted by a different person (JournalReviewRules); reconciliation journals are the one exception,
    // see AccountingService.reconcileWithNewJournal.
    public static AccJournalEntry create(TenantId tenantId, String entryNumber,
                                         LocalDate entryDate, String description,
                                         String reference, String entryType,
                                         UUID createdBy) {
        AccJournalEntry e = new AccJournalEntry();
        e.id          = UUID.randomUUID();
        e.tenantId    = tenantId.getValue();
        e.entryNumber = entryNumber;
        e.entryDate   = entryDate;
        e.description = description;
        e.reference   = reference;
        e.entryType   = entryType != null ? entryType : "MANUAL";
        e.status      = "DRAFT";
        e.totalDebit  = BigDecimal.ZERO;
        e.totalCredit = BigDecimal.ZERO;
        e.createdBy   = createdBy;
        e.createdAt   = Instant.now();
        e.updatedAt   = Instant.now();
        return e;
    }

    public void addLine(AccJournalLine line) {
        lines.add(line);
        totalDebit  = totalDebit.add(line.getDebitAmount());
        totalCredit = totalCredit.add(line.getCreditAmount());
        updatedAt   = Instant.now();
    }

    public void post() {
        post(null);
    }

    /** Posts the journal and records who posted it (null for the system). */
    public void post(UUID postedBy) {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT entries can be posted");
        if (totalDebit.compareTo(totalCredit) != 0)
            throw new IllegalStateException("Journal does not balance — debits must equal credits");
        this.status   = "POSTED";
        this.postedAt = Instant.now();
        this.postedBy = postedBy;
        this.updatedAt = Instant.now();
    }

    public void markReversed(String reversalEntryNumber) {
        if (!"POSTED".equals(this.status))
            throw new IllegalStateException("Only POSTED entries can be marked reversed");
        this.status = "REVERSED";
        this.reference = (this.reference != null ? this.reference + " | " : "")
                + "Reversed by: " + reversalEntryNumber;
        this.updatedAt = java.time.Instant.now();
    }

    /** Marks a new draft as the reversal of {@code originalId} (finance patch F2). */
    public void markAsReversalOf(UUID originalId) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only a draft can be made a reversal");
        this.reversesEntryId = originalId;
    }

    /** Discards a draft that should not be posted. Posted journals are never discarded; they are reversed. */
    public void discard() {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only draft journals can be discarded");
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isBalanced() {
        return totalDebit.compareTo(totalCredit) == 0;
    }
}