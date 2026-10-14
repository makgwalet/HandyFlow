package za.co.handyflow.platform.ap.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.ap.domain.ApPaymentRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A credit note received from a supplier (finance patch F10). It waits for approval when large, is then issued (posted to
 * the ledger and applied to its bill), or is rejected. {@code totalAmount - allocated} is credit still held with the supplier.
 */
@Entity
@Table(name = "ap_credit_notes")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ApCreditNote {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "credit_note_number", nullable = false) private String creditNoteNumber;
    @Column(name = "supplier_id") private UUID supplierId;
    @Column(name = "supplier_name", nullable = false) private String supplierName;
    @Column(name = "bill_id") private UUID billId;
    @Column(nullable = false) private String category = "OTHER";
    @Column(name = "credit_date", nullable = false) private LocalDate creditDate;
    @Column(nullable = false) private String reason;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2) private BigDecimal vatAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal allocated = BigDecimal.ZERO;
    @Column(nullable = false) private String status = "PENDING_APPROVAL";
    @Column(name = "journal_entry_id") private UUID journalEntryId;
    @Column(name = "rejection_reason") private String rejectionReason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "created_at") private Instant createdAt = Instant.now();

    public static ApCreditNote create(TenantId tenantId, String number, UUID supplierId, String supplierName, UUID billId,
                                      String category, LocalDate creditDate, String reason,
                                      BigDecimal amount, BigDecimal vatAmount, UUID createdBy) {
        ApCreditNote c = new ApCreditNote();
        c.tenantId = tenantId;
        c.creditNoteNumber = number;
        c.supplierId = supplierId;
        c.supplierName = supplierName;
        c.billId = billId;
        c.category = category != null ? category : "OTHER";
        c.creditDate = creditDate != null ? creditDate : LocalDate.now();
        c.reason = reason;
        c.amount = ApPaymentRules.money(amount);
        c.vatAmount = ApPaymentRules.money(vatAmount);
        c.totalAmount = c.amount.add(c.vatAmount);
        c.createdBy = createdBy;
        return c;
    }

    /** Credit still held with the supplier: issued and not yet applied to bills. */
    public BigDecimal getAvailable() {
        return "ISSUED".equals(status) ? ApPaymentRules.money(totalAmount.subtract(allocated)) : BigDecimal.ZERO.setScale(2);
    }

    public void issue(UUID by, UUID journalEntryId) {
        if (!"PENDING_APPROVAL".equals(status)) throw new IllegalStateException("Only a credit note waiting for approval can be issued");
        this.status = "ISSUED";
        this.journalEntryId = journalEntryId;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
    }

    public void reject(UUID by, String reason) {
        if (!"PENDING_APPROVAL".equals(status)) throw new IllegalStateException("Only a credit note waiting for approval can be rejected");
        this.status = "REJECTED";
        this.rejectionReason = reason;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
    }

    public void allocate(BigDecimal amount) {
        if (!"ISSUED".equals(status)) throw new IllegalStateException("Only an issued credit note can be applied to bills");
        BigDecimal next = ApPaymentRules.money(allocated).add(ApPaymentRules.money(amount));
        if (next.compareTo(totalAmount) > 0) throw new IllegalStateException("More than the credit note is for");
        this.allocated = next;
    }
}
