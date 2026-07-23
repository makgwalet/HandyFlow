package za.co.handyflow.platform.ap.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ap_bills")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ApBill {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "bill_number", nullable = false)
    private String billNumber;

    @Column(name = "bill_date",   nullable = false) private LocalDate billDate;
    @Column(name = "due_date",    nullable = false) private LocalDate dueDate;
    @Column(nullable = false)                        private String   category = "OTHER";
    @Column(nullable = false)                        private String   description;

    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(name = "vat_amount",   nullable = false, precision = 15, scale = 2) private BigDecimal vatAmount   = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false)                                                    private String     currency    = "ZAR";

    @Column(nullable = false) private String status = "DRAFT";

    // Accounting links
    @Column(name = "journal_entry_id")   private UUID journalEntryId;
    @Column(name = "payment_journal_id") private UUID paymentJournalId;

    // Evidence
    @Column(name = "attachment_url")  private String attachmentUrl;
    @Column(name = "attachment_name") private String attachmentName;
    @Column(name = "pop_url")         private String popUrl;
    @Column(name = "pop_name")        private String popName;
    @Column(name = "pop_uploaded_at") private Instant popUploadedAt;
    @Column(name = "pop_uploaded_by") private UUID    popUploadedBy;

    // Payment
    @Column(name = "paid_at")      private Instant paidAt;
    @Column(name = "paid_by")      private UUID    paidBy;
    @Column(name = "payment_ref")  private String  paymentRef;
    @Column(name = "batch_id")     private UUID    batchId;

    private String notes;

    // NEW: idempotency flag for the bill-due-soon reminder scheduler —
    // see migration comment for why this needs to exist at all.
    @Column(name = "due_soon_reminder_sent_at") private Instant dueSoonReminderSentAt;

    // NEW: maker-checker tracking for the amount-threshold second-approval
    // rule. Only ever set for bills that actually crossed the threshold —
    // stays null for the normal single-step approval path.
    @Column(name = "first_approved_by") private UUID    firstApprovedBy;
    @Column(name = "first_approved_at") private Instant firstApprovedAt;

    @Column(name = "created_by") private UUID    createdBy;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    @Version private Long version;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ApBill create(TenantId tenantId, UUID supplierId, String supplierName,
                                String billNumber, LocalDate billDate, LocalDate dueDate,
                                String category, String description,
                                BigDecimal amount, BigDecimal vatAmount,
                                String attachmentUrl, String attachmentName,
                                String notes, UUID createdBy) {
        ApBill b         = new ApBill();
        b.tenantId       = tenantId;
        b.supplierId     = supplierId;
        b.supplierName   = supplierName;
        b.billNumber     = billNumber;
        b.billDate       = billDate;
        b.dueDate        = dueDate;
        b.category       = category != null ? category : "OTHER";
        b.description    = description;
        b.amount         = amount;
        b.vatAmount      = vatAmount != null ? vatAmount : BigDecimal.ZERO;
        b.totalAmount    = amount.add(b.vatAmount);
        b.attachmentUrl  = attachmentUrl;
        b.attachmentName = attachmentName;
        b.notes          = notes;
        b.createdBy      = createdBy;
        b.status         = "DRAFT";
        b.createdAt      = Instant.now();
        b.updatedAt      = Instant.now();
        return b;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void approve(UUID journalEntryId) {
        if (!"DRAFT".equals(status) && !"OVERDUE".equals(status) && !"SECOND_APPROVAL".equals(status))
            throw new IllegalStateException("Only DRAFT, OVERDUE, or SECOND_APPROVAL bills can be approved");
        this.status         = "APPROVED";
        this.journalEntryId = journalEntryId;
        this.updatedAt      = Instant.now();
    }

    // First step of the maker-checker flow for bills above the approval
    // threshold — records who gave the first approval and moves the bill
    // into a holding status. Deliberately does NOT post a journal entry;
    // that only happens once approve() is called by a second, different
    // person. See ApService.approveBill() for the threshold check and the
    // "must be a different person" enforcement — this method only handles
    // the state transition itself.
    public void requestSecondApproval(UUID approvedBy) {
        if (!"DRAFT".equals(status) && !"OVERDUE".equals(status))
            throw new IllegalStateException("Only DRAFT or OVERDUE bills can be submitted for approval");
        this.status           = "SECOND_APPROVAL";
        this.firstApprovedBy  = approvedBy;
        this.firstApprovedAt  = Instant.now();
        this.updatedAt        = Instant.now();
    }

    public void markPaid(UUID paymentJournalId, String paymentRef, UUID paidBy) {
        if (!"APPROVED".equals(status))
            throw new IllegalStateException("Only APPROVED bills can be marked paid");
        this.status             = "PAID";
        this.paymentJournalId   = paymentJournalId;
        this.paymentRef         = paymentRef;
        this.paidBy             = paidBy;
        this.paidAt             = Instant.now();
        this.updatedAt          = Instant.now();
    }

    // KNOWN LIMITATION, not an oversight: only APPROVED bills get marked
    // OVERDUE. A bill stuck in SECOND_APPROVAL past its due date
    // will NOT show as overdue. Extending this to cover that status too
    // was considered and deliberately not done — markOverdue() would need
    // to transition SECOND_APPROVAL -> OVERDUE, but approve()'s
    // logic distinguishes "needs first approval" (DRAFT/OVERDUE) from
    // "needs second approval" (SECOND_APPROVAL) by status alone.
    // If an overdue SECOND_APPROVAL bill silently became OVERDUE,
    // the next approve() call would treat it as needing a FIRST approval
    // again, losing the fact it already got one — a real state-loss bug,
    // not a small gap. Fixing this properly needs the two concerns
    // (overdue-ness, approval-stage) to stop being conflated into one
    // status field, which is a bigger change than this pass.
    public void markOverdue() {
        if ("APPROVED".equals(status) && LocalDate.now().isAfter(dueDate)) {
            this.status    = "OVERDUE";
            this.updatedAt = Instant.now();
        }
    }

    public void cancel() {
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public void assignToBatch(UUID batchId) {
        this.batchId   = batchId;
        this.updatedAt = Instant.now();
    }

    public void removeFromBatch() {
        this.batchId   = null;
        this.updatedAt = Instant.now();
    }

    public void markDueSoonReminderSent() {
        this.dueSoonReminderSentAt = Instant.now();
    }

    // ── Evidence upload ───────────────────────────────────────────────────────

    public void uploadAttachment(String url, String name) {
        this.attachmentUrl  = url;
        this.attachmentName = name;
        this.updatedAt      = Instant.now();
    }

    public void uploadPop(String url, String name, UUID uploadedBy) {
        this.popUrl         = url;
        this.popName        = name;
        this.popUploadedBy  = uploadedBy;
        this.popUploadedAt  = Instant.now();
        this.updatedAt      = Instant.now();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isOverdue() {
        return !"PAID".equals(status) && !"CANCELLED".equals(status)
                && dueDate != null && LocalDate.now().isAfter(dueDate);
    }

    public int daysUntilDue() {
        if (dueDate == null) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }
}