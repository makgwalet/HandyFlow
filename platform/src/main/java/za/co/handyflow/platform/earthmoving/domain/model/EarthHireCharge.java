package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One billing period of one deployment: days, weeks or months on hire, hours worked or kilometres driven, at the rate that applied when it was raised.
 * PENDING until it goes onto a draft invoice (INVOICED). A PENDING charge can be voided with a reason; an INVOICED one
 * can be reopened only once its draft invoice no longer exists.
 */
@Entity
@Table(name = "earthmoving_hire_charges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthHireCharge {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "deployment_id", nullable = false)
    private UUID deploymentId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(nullable = false)
    private String basis;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitRate;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    /** Excluding VAT. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String status = HireRules.PENDING;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    /** Where the rate came from: the contract, the machine, or agreed for this charge. */
    @Column(name = "rate_source")
    private String rateSource;

    /** Kilometre charges only: the allowance, and the odometer readings (and which readings they were) the quantity came from. */
    @Column(name = "included_km", precision = 10, scale = 2)
    private BigDecimal includedKm;

    @Column(name = "start_reading", precision = 10, scale = 1)
    private BigDecimal startReading;

    @Column(name = "end_reading", precision = 10, scale = 1)
    private BigDecimal endReading;

    @Column(name = "start_reading_id")
    private UUID startReadingId;

    @Column(name = "end_reading_id")
    private UUID endReadingId;

    /** Why a pending charge cannot go on an invoice yet (a disputed reading, a reading that no longer adds up). Null when it can. */
    private String attention;

    @Column(name = "recalculated_at")
    private Instant recalculatedAt;

    /** Why it was voided or reopened. */
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static EarthHireCharge create(TenantId tenantId, UUID deploymentId, UUID assetId, UUID customerId, LocalDate from, LocalDate to,
                                         String basis, BigDecimal quantity, BigDecimal unitRate, BigDecimal vatRate, String description, UUID actor) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Nothing to charge.");
        if (unitRate == null || unitRate.signum() <= 0) throw new IllegalArgumentException("Nothing to charge.");
        EarthHireCharge c = new EarthHireCharge();
        c.tenantId = tenantId;
        c.deploymentId = deploymentId;
        c.assetId = assetId;
        c.customerId = customerId;
        c.periodFrom = from;
        c.periodTo = to;
        c.basis = HireRules.basis(basis);
        c.quantity = quantity.setScale(2, java.math.RoundingMode.HALF_UP);
        c.unitRate = unitRate.setScale(2, java.math.RoundingMode.HALF_UP);
        c.vatRate = HireRules.vatRate(vatRate);
        c.amount = HireRules.amount(c.quantity, c.unitRate);
        c.description = description;
        c.createdBy = actor;
        c.createdAt = Instant.now();
        c.updatedAt = c.createdAt;
        return c;
    }

    /** Keeps why a different rate was charged. Void and reopen later write their own reason, and the audit trail keeps this one. */
    public void noteRateOverride(String text) { if (text != null) this.note = text; }

    public void recordRate(String source) { this.rateSource = source; }

    public boolean isKilometre() { return HireRules.KILOMETRE.equals(basis); }

    /** Remembers the readings a kilometre quantity came from, so it can be worked out again if one is corrected. */
    public void recordKilometres(BigDecimal included, UUID startId, BigDecimal start, UUID endId, BigDecimal end) {
        this.includedKm = included == null ? null : included.setScale(2, java.math.RoundingMode.HALF_UP);
        this.startReadingId = startId;
        this.startReading = start;
        this.endReadingId = endId;
        this.endReading = end;
    }

    /** A kilometre charge worked out again from corrected readings. The rate and VAT stay as they were. */
    public void recalculate(BigDecimal quantity, String description, UUID startId, BigDecimal start, UUID endId, BigDecimal end) {
        if (!isPending()) throw new IllegalStateException("Only a charge that is not yet on an invoice can be recalculated.");
        if (!isKilometre()) throw new IllegalStateException("Only kilometre charges are worked out from readings.");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Nothing to charge.");
        this.quantity = quantity.setScale(2, java.math.RoundingMode.HALF_UP);
        this.amount = HireRules.amount(this.quantity, unitRate);
        this.description = description;
        this.startReadingId = startId;
        this.startReading = start;
        this.endReadingId = endId;
        this.endReading = end;
        this.attention = null;
        this.recalculatedAt = Instant.now();
        this.updatedAt = this.recalculatedAt;
    }

    /** Holds a pending charge back from invoicing, saying why. */
    public void hold(String why) {
        if (!isPending()) throw new IllegalStateException("Only a charge that is not yet on an invoice can be held.");
        this.attention = why;
        this.updatedAt = Instant.now();
    }

    public void release() {
        this.attention = null;
        this.updatedAt = Instant.now();
    }

    public boolean needsAttention() { return attention != null; }

    public BigDecimal getVatAmount() { return HireRules.vatAmount(amount, vatRate); }

    public BigDecimal getTotal() { return amount.add(getVatAmount()); }

    public boolean isPending() { return HireRules.PENDING.equals(status); }

    public void markInvoiced(UUID invoiceId, String invoiceNumber) {
        if (!isPending()) throw new IllegalStateException("This charge is " + status.toLowerCase() + ", so it cannot go on an invoice.");
        if (needsAttention()) throw new IllegalStateException("This charge is on hold: " + attention);
        this.status = HireRules.INVOICED;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.updatedAt = Instant.now();
    }

    public void voidCharge(String reason) {
        if (!isPending()) throw new IllegalStateException("Only a charge that is not yet on an invoice can be voided.");
        this.note = EarthmovingRules.requireReason(reason, "voiding a hire charge");
        this.status = HireRules.VOIDED;
        this.attention = null;
        this.updatedAt = Instant.now();
    }

    /** The draft invoice is gone: put the charge back so it can be invoiced again. */
    public void reopen(String reason) {
        if (!HireRules.INVOICED.equals(status)) throw new IllegalStateException("Only an invoiced charge can be reopened.");
        this.note = EarthmovingRules.requireReason(reason, "reopening a hire charge");
        this.status = HireRules.PENDING;
        this.invoiceId = null;
        this.invoiceNumber = null;
        this.updatedAt = Instant.now();
    }
}
