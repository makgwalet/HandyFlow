package za.co.handyflow.platform.bookkeeping.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
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
 * A client's own journal entry — mirrors {@code accounting.AccJournalEntry}'s
 * shape/lifecycle (DRAFT -> POSTED, balance validation) but scoped by
 * {@code clientId} and belonging to a {@code BkPeriod}, matching
 * {@code accountant}'s own {@code prac_journals} concept (a client-scoped
 * journal tied to a period) rather than {@code accounting}'s tenant-owned
 * one. Lines are a child collection, cascade ALL, exactly like {@code
 * AccJournalEntry.lines}.
 */
@Entity
@Table(name = "bk_journal_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkJournalEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "period_id", nullable = false)
    private UUID periodId;

    @Column(name = "entry_number", nullable = false, unique = true)
    private String entryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    private String description;
    private String reference;

    @Column(name = "entry_type", nullable = false)
    private String entryType = "MANUAL";

    @Column(nullable = false)
    private String status = "DRAFT"; // DRAFT, POSTED

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC")
    private List<BkJournalLine> lines = new ArrayList<>();

    public static BkJournalEntry create(TenantId tenantId, UUID clientId, UUID periodId, String entryNumber,
                                         LocalDate entryDate, String description, String reference,
                                         String entryType, UUID createdBy) {
        BkJournalEntry e = new BkJournalEntry();
        e.tenantId = tenantId;
        e.clientId = clientId;
        e.periodId = periodId;
        e.entryNumber = entryNumber;
        e.entryDate = entryDate;
        e.description = description;
        e.reference = reference;
        e.entryType = entryType != null ? entryType : "MANUAL";
        e.createdBy = createdBy;
        e.status = "DRAFT";
        e.createdAt = Instant.now();
        return e;
    }

    public void addLine(BkJournalLine line) { this.lines.add(line); }

    public BigDecimal getTotalDebit() {
        return lines.stream().map(BkJournalLine::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalCredit() {
        return lines.stream().map(BkJournalLine::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isBalanced() {
        return getTotalDebit().compareTo(getTotalCredit()) == 0;
    }

    public void post() {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only DRAFT entries can be posted — current status: " + status);
        if (!isBalanced()) throw new IllegalStateException("Journal does not balance");
        this.status = "POSTED";
        this.postedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
}
