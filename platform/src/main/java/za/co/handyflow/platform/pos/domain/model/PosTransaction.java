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

    // ── Customer ──────────────────────────────────────────────────────────────
    @Column(name = "customer_id")    private UUID   customerId;
    @Column(name = "customer_name")  private String customerName;

    // ── Amounts ───────────────────────────────────────────────────────────────
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal subtotal       = BigDecimal.ZERO;
    @Column(name = "vat_amount",      nullable = false, precision = 15, scale = 2) private BigDecimal vatAmount      = BigDecimal.ZERO;
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "total_amount",    nullable = false, precision = 15, scale = 2) private BigDecimal totalAmount    = BigDecimal.ZERO;

    // ── Payment ───────────────────────────────────────────────────────────────
    @Column(name = "payment_method", nullable = false) private String    paymentMethod = "CASH";
    @Column(name = "amount_tendered", precision = 15, scale = 2) private BigDecimal amountTendered;
    @Column(name = "change_given",    precision = 15, scale = 2) private BigDecimal changeGiven;
    @Column(name = "payment_ref")    private String  paymentRef;

    /**
     * JSON blob for split payments — stored as TEXT.
     * Format: [{"paymentMethod":"CASH","amount":200.00,"amountTendered":200.00},
     *          {"paymentMethod":"CARD","amount":150.00,"paymentRef":"AUTH-123"}]
     * Serialised/deserialised by PosService using Jackson ObjectMapper.
     */
    @Column(name = "split_payments_json", columnDefinition = "TEXT")
    private String splitPaymentsJson;

    // ── Status ────────────────────────────────────────────────────────────────
    /** DRAFT | COMPLETED | VOIDED | REFUNDED */
    @Column(nullable = false) private String status = "COMPLETED";

    // ── Refund linkage ────────────────────────────────────────────────────────
    /** For REFUND transactions: the ID of the original sale being refunded */
    @Column(name = "original_transaction_id") private UUID   originalTransactionId;
    @Column(name = "refund_reason")           private String refundReason;

    // ── Cash session ──────────────────────────────────────────────────────────
    @Column(name = "cash_session_id") private UUID cashSessionId;

    // ── Accounting ────────────────────────────────────────────────────────────
    @Column(name = "journal_entry_id") private UUID journalEntryId;

    // ── Staff ─────────────────────────────────────────────────────────────────
    @Column(name = "served_by")      private UUID   servedBy;
    @Column(name = "served_by_name") private String servedByName;
    private String notes;

    // ── Timestamps ────────────────────────────────────────────────────────────
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "voided_at")  private Instant voidedAt;
    @Column(name = "voided_reason") private String voidedReason;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static PosTransaction create(TenantId tenantId, String transactionNumber,
                                        UUID customerId, String customerName,
                                        String paymentMethod, BigDecimal amountTendered,
                                        String paymentRef, String splitPaymentsJson,
                                        UUID cashSessionId,
                                        UUID servedBy, String servedByName, String notes) {
        PosTransaction t      = new PosTransaction();
        t.tenantId            = tenantId;
        t.transactionNumber   = transactionNumber;
        t.customerId          = customerId;
        t.customerName        = customerName;
        t.paymentMethod       = paymentMethod != null ? paymentMethod : "CASH";
        t.amountTendered      = amountTendered;
        t.paymentRef          = paymentRef;
        t.splitPaymentsJson   = splitPaymentsJson;
        t.cashSessionId       = cashSessionId;
        t.servedBy            = servedBy;
        t.servedByName        = servedByName;
        t.notes               = notes;
        t.status              = "COMPLETED";
        t.createdAt           = Instant.now();
        t.updatedAt           = Instant.now();
        return t;
    }

    /** Creates a REFUND transaction linked to an original sale */
    public static PosTransaction createRefund(TenantId tenantId, String transactionNumber,
                                              UUID originalTransactionId, String refundReason,
                                              UUID customerId, String customerName,
                                              String refundMethod, UUID cashSessionId,
                                              UUID servedBy, String servedByName) {
        PosTransaction t          = new PosTransaction();
        t.tenantId                = tenantId;
        t.transactionNumber       = transactionNumber;
        t.originalTransactionId   = originalTransactionId;
        t.refundReason            = refundReason;
        t.customerId              = customerId;
        t.customerName            = customerName;
        t.paymentMethod           = refundMethod != null ? refundMethod : "CASH";
        t.cashSessionId           = cashSessionId;
        t.servedBy                = servedBy;
        t.servedByName            = servedByName;
        t.status                  = "COMPLETED";
        t.createdAt               = Instant.now();
        t.updatedAt               = Instant.now();
        return t;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

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
        this.updatedAt      = Instant.now();
    }

    public void voidTransaction(String reason) {
        this.status        = "VOIDED";
        this.voidedAt      = Instant.now();
        this.voidedReason  = reason;
        this.updatedAt     = Instant.now();
    }

    public void markRefunded() {
        this.status    = "REFUNDED";
        this.updatedAt = Instant.now();
    }

    public boolean isVoided()    { return "VOIDED".equals(status); }
    public boolean isRefunded()  { return "REFUNDED".equals(status); }
    public boolean isCompleted() { return "COMPLETED".equals(status); }
    public boolean isCashPayment() { return "CASH".equals(paymentMethod); }
}
