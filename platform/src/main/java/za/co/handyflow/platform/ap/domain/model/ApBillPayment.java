package za.co.handyflow.platform.ap.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One payment made against a supplier bill (finance patch F10). Its id keys the ledger posting. */
@Entity
@Table(name = "ap_bill_payments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ApBillPayment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "bill_id", nullable = false) private UUID billId;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(name = "paid_on", nullable = false) private LocalDate paidOn;
    @Column(name = "bank_account_id") private UUID bankAccountId;
    private String reference;
    @Column(name = "batch_id") private UUID batchId;
    @Column(name = "journal_entry_id") private UUID journalEntryId;
    @Column(name = "paid_by") private UUID paidBy;
    @Column(name = "created_at") private Instant createdAt = Instant.now();

    public static ApBillPayment of(TenantId tenantId, UUID billId, BigDecimal amount, UUID bankAccountId,
                                   String reference, UUID batchId, UUID paidBy) {
        ApBillPayment p = new ApBillPayment();
        p.tenantId = tenantId;
        p.billId = billId;
        p.amount = amount;
        p.paidOn = LocalDate.now();
        p.bankAccountId = bankAccountId;
        p.reference = reference;
        p.batchId = batchId;
        p.paidBy = paidBy;
        return p;
    }

    public void postedAs(UUID journalEntryId) { this.journalEntryId = journalEntryId; }
}
