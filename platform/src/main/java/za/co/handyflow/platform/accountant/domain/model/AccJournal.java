package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "AccountantJournal")
@Table(name = "prac_journals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccJournal {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id",   nullable = false) private UUID    tenantId;
    @Column(name = "client_id",   nullable = false) private UUID    clientId;
    @Column(name = "period_id",   nullable = false) private UUID    periodId;
    @Column(name = "reference",   nullable = false) private String  reference;
    @Column(name = "description", nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "journal_type",nullable = false) private String  journalType = "STANDARD";
    @Column(name = "status",      nullable = false) private String  status      = "DRAFT";

    @Column(name = "prepared_by")  private UUID    preparedBy;
    @Column(name = "prepared_at")  private Instant preparedAt;
    @Column(name = "reviewed_by")  private UUID    reviewedBy;
    @Column(name = "reviewed_at")  private Instant reviewedAt;
    @Column(name = "approved_by")  private UUID    approvedBy;
    @Column(name = "approved_at")  private Instant approvedAt;
    @Column(name = "posted_at")    private Instant postedAt;

    @Column(name = "reversed_by_journal_id") private UUID reversedByJournalId;

    @Column(name = "journal_date", nullable = false) private LocalDate journalDate;
    @Column(name = "created_at",   nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at",   nullable = false) private Instant updatedAt;

    @Version private Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "journal_id")
    @OrderBy("line_order ASC")
    private List<AccJournalLine> lines = new ArrayList<>();

    // ── Factory ───────────────────────────────────────────────────────────────

    public static AccJournal create(UUID tenantId, UUID clientId, UUID periodId,
                                    String reference, String description, String journalType,
                                    LocalDate journalDate, UUID preparedBy) {
        AccJournal j = new AccJournal();
        j.tenantId    = tenantId;
        j.clientId    = clientId;
        j.periodId    = periodId;
        j.reference   = reference;
        j.description = description;
        j.journalType = journalType;
        j.journalDate = journalDate;
        j.preparedBy  = preparedBy;
        j.preparedAt  = Instant.now();
        j.status      = "PREPARED";
        j.createdAt   = Instant.now();
        j.updatedAt   = Instant.now();
        return j;
    }

    // ── Computed ──────────────────────────────────────────────────────────────

    public BigDecimal totalDebits() {
        return lines.stream()
                .map(AccJournalLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCredits() {
        return lines.stream()
                .map(AccJournalLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isBalanced() {
        return totalDebits().compareTo(totalCredits()) == 0;
    }

    // ── State machine ─────────────────────────────────────────────────────────

    public void submitForReview(UUID reviewer) {
        if (!"PREPARED".equals(status))
            throw new IllegalStateException("Journal must be PREPARED before review. Current: " + status);
        this.status     = "REVIEWED";
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.updatedAt  = Instant.now();
    }

    public void approve(UUID approver) {
        if (!"REVIEWED".equals(status))
            throw new IllegalStateException("Journal must be REVIEWED before approval. Current: " + status);
        this.status     = "APPROVED";
        this.approvedBy = approver;
        this.approvedAt = Instant.now();
        this.updatedAt  = Instant.now();
    }

    public void post() {
        if (!isBalanced())
            throw new IllegalStateException(
                    String.format("Journal cannot be posted — debits R%s ≠ credits R%s",
                            totalDebits(), totalCredits()));
        if (!"APPROVED".equals(status))
            throw new IllegalStateException("Journal must be APPROVED before posting. Current: " + status);
        this.status    = "POSTED";
        this.postedAt  = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markReversed(UUID reversalJournalId) {
        this.status                 = "REVERSED";
        this.reversedByJournalId    = reversalJournalId;
        this.updatedAt              = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
