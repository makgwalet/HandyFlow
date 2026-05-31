package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pos_transactions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosTransaction {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "transaction_number", nullable = false) private String transactionNumber;
    @Column(name = "customer_id")    private UUID   customerId;
    @Column(name = "customer_name")  private String customerName;

    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal subtotal       = BigDecimal.ZERO;
    @Column(name = "vat_amount",      nullable = false, precision = 15, scale = 2) private BigDecimal vatAmount      = BigDecimal.ZERO;
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "total_amount",    nullable = false, precision = 15, scale = 2) private BigDecimal totalAmount    = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false) private String    paymentMethod = "CASH";
    @Column(name = "amount_tendered", precision = 15, scale = 2) private BigDecimal amountTendered;
    @Column(name = "change_given",    precision = 15, scale = 2) private BigDecimal changeGiven;
    @Column(name = "payment_ref")    private String  paymentRef;

    @Column(nullable = false)        private String status = "COMPLETED";
    @Column(name = "journal_entry_id") private UUID journalEntryId;
    @Column(name = "served_by")      private UUID   servedBy;
    @Column(name = "served_by_name") private String servedByName;
    private String notes;

    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "voided_at")  private Instant voidedAt;
    @Column(name = "voided_reason") private String voidedReason;

    public static PosTransaction create(TenantId tenantId, String transactionNumber,
                                         UUID customerId, String customerName,
                                         String paymentMethod, BigDecimal amountTendered,
                                         String paymentRef, UUID servedBy,
                                         String servedByName, String notes) {
        PosTransaction t      = new PosTransaction();
        t.tenantId            = tenantId;
        t.transactionNumber   = transactionNumber;
        t.customerId          = customerId;
        t.customerName        = customerName;
        t.paymentMethod       = paymentMethod != null ? paymentMethod : "CASH";
        t.amountTendered      = amountTendered;
        t.paymentRef          = paymentRef;
        t.servedBy            = servedBy;
        t.servedByName        = servedByName;
        t.notes               = notes;
        t.status              = "COMPLETED";
        t.createdAt           = Instant.now();
        t.updatedAt           = Instant.now();
        return t;
    }

    public void setTotals(BigDecimal subtotal, BigDecimal vatAmount,
                           BigDecimal discountAmount, BigDecimal totalAmount) {
        this.subtotal        = subtotal;
        this.vatAmount       = vatAmount;
        this.discountAmount  = discountAmount;
        this.totalAmount     = totalAmount;
        if ("CASH".equals(paymentMethod) && amountTendered != null) {
            this.changeGiven = amountTendered.subtract(totalAmount).max(BigDecimal.ZERO);
        }
        this.updatedAt = Instant.now();
    }

    public void setJournalEntry(UUID journalEntryId) {
        this.journalEntryId = journalEntryId;
        this.updatedAt = Instant.now();
    }

    public void voidTransaction(String reason) {
        this.status       = "VOIDED";
        this.voidedAt     = Instant.now();
        this.voidedReason = reason;
        this.updatedAt    = Instant.now();
    }

    public boolean isVoided() { return "VOIDED".equals(status); }
}
