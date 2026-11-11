package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A supplier's invoice for fuel, captured against a purchase order and the receipts it covers. The match (litres and price against
 * what was ordered and what was actually received) is worked out by SupplierInvoiceMatcher. Approving a mismatch needs a reason.
 * This is the Fuel module's own record: it does not post to accounts payable.
 */
@Entity
@Table(name = "fuel_supplier_invoices")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelSupplierInvoice {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "supplier_id", nullable = false, updatable = false) private UUID supplierId;
    @Column(name = "purchase_order_id", updatable = false) private UUID purchaseOrderId;
    @Column(name = "invoice_number", nullable = false, updatable = false, length = 60) private String invoiceNumber;
    @Column(name = "invoice_date", nullable = false, updatable = false) private LocalDate invoiceDate;
    @Column(name = "due_date", updatable = false) private LocalDate dueDate;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2) private BigDecimal litres;
    @Column(name = "price_per_litre", nullable = false, updatable = false, precision = 10, scale = 4) private BigDecimal pricePerLitre;
    @Column(name = "other_charges", nullable = false, updatable = false, precision = 12, scale = 2) private BigDecimal otherCharges;
    @Column(name = "vat_amount", nullable = false, updatable = false, precision = 12, scale = 2) private BigDecimal vatAmount;
    @Column(name = "total_amount", nullable = false, updatable = false, precision = 14, scale = 2) private BigDecimal totalAmount;
    @Column(nullable = false, length = 10) private String status = "CAPTURED";
    @Column(name = "match_status", nullable = false, length = 10) private String matchStatus = "UNMATCHED";
    @Column(name = "decision_reason", length = 500) private String decisionReason;
    @Column(length = 500) private String notes;
    @Column(name = "captured_by", updatable = false) private UUID capturedBy;
    @Column(name = "captured_at", nullable = false, updatable = false) private Instant capturedAt;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Version private Long version;

    public static FuelSupplierInvoice capture(TenantId tenantId, UUID supplierId, UUID purchaseOrderId, String invoiceNumber, LocalDate invoiceDate,
                                              LocalDate dueDate, BigDecimal litres, BigDecimal pricePerLitre, BigDecimal otherCharges, BigDecimal vatAmount,
                                              String notes, UUID by) {
        if (supplierId == null) throw new IllegalArgumentException("Choose the supplier.");
        if (invoiceNumber == null || invoiceNumber.isBlank()) throw new IllegalArgumentException("Enter the supplier's invoice number.");
        if (invoiceDate == null) throw new IllegalArgumentException("Enter the invoice date.");
        if (dueDate != null && dueDate.isBefore(invoiceDate)) throw new IllegalArgumentException("The due date is before the invoice date.");
        if (litres == null || litres.signum() <= 0) throw new IllegalArgumentException("Litres must be more than zero.");
        if (pricePerLitre == null || pricePerLitre.signum() < 0) throw new IllegalArgumentException("Price per litre cannot be negative.");
        BigDecimal other = otherCharges == null ? BigDecimal.ZERO : otherCharges;
        BigDecimal vat = vatAmount == null ? BigDecimal.ZERO : vatAmount;
        if (other.signum() < 0 || vat.signum() < 0) throw new IllegalArgumentException("Charges and VAT cannot be negative.");
        FuelSupplierInvoice i = new FuelSupplierInvoice();
        i.tenantId = tenantId; i.supplierId = supplierId; i.purchaseOrderId = purchaseOrderId;
        i.invoiceNumber = invoiceNumber.trim(); i.invoiceDate = invoiceDate; i.dueDate = dueDate;
        i.litres = litres; i.pricePerLitre = pricePerLitre; i.otherCharges = other; i.vatAmount = vat;
        i.totalAmount = litres.multiply(pricePerLitre).setScale(2, RoundingMode.HALF_UP).add(other).add(vat);
        i.notes = notes == null || notes.isBlank() ? null : notes.trim();
        i.capturedBy = by; i.capturedAt = Instant.now();
        return i;
    }

    public boolean isCaptured() { return "CAPTURED".equals(status); }

    public void recordMatch(String matchStatus) { this.matchStatus = matchStatus; }

    /** A matched invoice can be approved as it is; anything else needs a reason on record. */
    public void approve(String currentMatchStatus, String reason, UUID by) {
        if (!isCaptured()) throw new IllegalStateException("Invoice " + invoiceNumber + " is already " + status.toLowerCase() + ".");
        String r = reason == null || reason.isBlank() ? null : reason.trim();
        if (!"MATCHED".equals(currentMatchStatus) && (r == null || r.length() < 5)) {
            throw new IllegalArgumentException("This invoice does not match the order and receipts. Give a reason (at least 5 characters) to approve it anyway.");
        }
        this.matchStatus = currentMatchStatus;
        this.status = "APPROVED"; this.decisionReason = r; this.decidedBy = by; this.decidedAt = Instant.now();
    }

    public void reject(String reason, UUID by) {
        if (!isCaptured()) throw new IllegalStateException("Invoice " + invoiceNumber + " is already " + status.toLowerCase() + ".");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters for rejecting the invoice.");
        this.status = "REJECTED"; this.decisionReason = reason.trim(); this.decidedBy = by; this.decidedAt = Instant.now();
    }
}
