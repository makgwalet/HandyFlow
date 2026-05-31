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
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT entries can be posted");
        if (totalDebit.compareTo(totalCredit) != 0)
            throw new IllegalStateException("Journal does not balance — debits must equal credits");
        this.status   = "POSTED";
        this.postedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isBalanced() {
        return totalDebit.compareTo(totalCredit) == 0;
    }
}