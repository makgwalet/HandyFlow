package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A supplier's invoice recorded against a purchase order. RECORDED until someone other than the person who captured it
 * approves it (an admin may approve their own) or disputes it with a reason; a disputed invoice can still be approved
 * later once sorted out. Amounts exclude VAT for matching ({@code subtotal}); {@code vatAmount} is kept for the record.
 */
@Entity
@Table(name = "facility_vendor_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityVendorInvoice {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "invoice_number", nullable = false, length = 80)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "RECORDED";

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static FacilityVendorInvoice create(TenantId tenantId, UUID purchaseOrderId, UUID vendorId, String invoiceNumber,
                                               LocalDate invoiceDate, BigDecimal subtotal, BigDecimal vatAmount, UUID recordedBy) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) throw new IllegalArgumentException("invoice number is required");
        if (invoiceNumber.trim().length() > 80) throw new IllegalArgumentException("invoice number must be 80 characters or fewer");
        if (invoiceDate == null) throw new IllegalArgumentException("invoice date is required");
        if (subtotal == null || subtotal.signum() <= 0) throw new IllegalArgumentException("The invoice amount (excluding VAT) must be more than zero");
        if (vatAmount != null && vatAmount.signum() < 0) throw new IllegalArgumentException("VAT must not be negative");
        FacilityVendorInvoice i = new FacilityVendorInvoice();
        i.tenantId = tenantId;
        i.purchaseOrderId = purchaseOrderId;
        i.vendorId = vendorId;
        i.invoiceNumber = invoiceNumber.trim();
        i.invoiceDate = invoiceDate;
        i.subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        i.vatAmount = (vatAmount == null ? BigDecimal.ZERO : vatAmount).setScale(2, RoundingMode.HALF_UP);
        i.recordedByUserId = recordedBy;
        i.createdAt = Instant.now();
        i.updatedAt = i.createdAt;
        return i;
    }

    /** Counts toward what the supplier has billed us: anything not disputed. */
    public boolean counts() { return !"DISPUTED".equals(status); }

    public BigDecimal total() { return subtotal.add(vatAmount); }

    public void approve(UUID by, String note) {
        if ("APPROVED".equals(status)) throw new IllegalStateException("This invoice is already approved");
        this.status = "APPROVED";
        decide(by, note);
    }

    public void dispute(UUID by, String reason) {
        if ("APPROVED".equals(status)) throw new IllegalStateException("An approved invoice cannot be disputed");
        if ("DISPUTED".equals(status)) throw new IllegalStateException("This invoice is already disputed");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to dispute an invoice");
        this.status = "DISPUTED";
        decide(by, reason);
    }

    private void decide(UUID by, String note) {
        this.decidedByUserId = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note == null || note.isBlank() ? null : note.trim();
        this.updatedAt = this.decidedAt;
    }
}
