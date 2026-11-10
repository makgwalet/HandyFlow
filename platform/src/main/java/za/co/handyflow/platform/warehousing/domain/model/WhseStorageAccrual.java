package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One day of storage for one stock position, with the quantity and the rate frozen on the row. Figures never change
 * after creation (the database enforces this); the only later change is linking the row to the invoice that billed it.
 */
@Entity
@Table(name = "whse_storage_accruals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseStorageAccrual {

    public static final int DAYS_PER_MONTH = 30;

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "item_id", nullable = false) private UUID itemId;
    @Column(name = "location_id", nullable = false) private UUID locationId;
    @Column(name = "inventory_id", nullable = false) private UUID inventoryId;
    @Column(name = "status", nullable = false) private String status;
    @Column(name = "accrual_date", nullable = false) private LocalDate accrualDate;
    @Column(name = "qty_on_hand", nullable = false, precision = 12, scale = 3) private BigDecimal qtyOnHand;
    @Column(name = "monthly_rate", nullable = false, precision = 12, scale = 4) private BigDecimal monthlyRate;
    @Column(name = "rate_source", nullable = false) private String rateSource;
    @Column(name = "days_per_month", nullable = false) private int daysPerMonth = DAYS_PER_MONTH;
    @Column(name = "amount", nullable = false, precision = 15, scale = 4) private BigDecimal amount;
    @Column(name = "billing_invoice_id") private UUID billingInvoiceId;
    @Column(name = "tariff_id") private UUID tariffId;
    /** The part of {@code qtyOnHand} that was inside its free days; the amount excludes it. */
    @Column(name = "free_qty", nullable = false, precision = 12, scale = 3) private BigDecimal freeQty = BigDecimal.ZERO;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public static WhseStorageAccrual of(WhseInventory position, LocalDate date, BigDecimal monthlyRate, String rateSource) {
        return of(position, date, position.getQtyOnHand(), monthlyRate, rateSource);
    }

    /** {@code billableQty} is the most the position held on that day (see WhseStorageAccrualRepository.billableQuantities). */
    public static WhseStorageAccrual of(WhseInventory position, LocalDate date, BigDecimal billableQty,
                                        BigDecimal monthlyRate, String rateSource) {
        if (billableQty == null || billableQty.signum() <= 0) {
            throw new IllegalArgumentException("Nothing on hand to accrue");
        }
        WhseStorageAccrual a = new WhseStorageAccrual();
        a.tenantId = position.getTenantId();
        a.clientId = position.getClientId();
        a.itemId = position.getItemId();
        a.locationId = position.getLocationId();
        a.inventoryId = position.getId();
        a.status = position.getStatus();
        a.accrualDate = date;
        a.qtyOnHand = billableQty;
        a.monthlyRate = monthlyRate;
        a.rateSource = rateSource;
        a.amount = billableQty.multiply(monthlyRate)
                .divide(BigDecimal.valueOf(DAYS_PER_MONTH), 4, RoundingMode.HALF_UP);
        return a;
    }

    /** Marks part of the day's quantity as free (stock still inside its free days) and prices only the rest. */
    public WhseStorageAccrual withFreeQty(BigDecimal free) {
        BigDecimal f = free == null ? BigDecimal.ZERO : free;
        if (f.signum() < 0 || f.compareTo(qtyOnHand) > 0) throw new IllegalArgumentException("Free quantity must be between 0 and the quantity held");
        this.freeQty = f;
        this.amount = qtyOnHand.subtract(f).multiply(monthlyRate).divide(BigDecimal.valueOf(DAYS_PER_MONTH), 4, RoundingMode.HALF_UP);
        return this;
    }

    /** The tariff version that priced this day, when a version (not a plain rate field) did. */
    public WhseStorageAccrual pricedBy(UUID tariffVersionId) {
        this.tariffId = tariffVersionId;
        return this;
    }

    public void markBilled(UUID invoiceId) {
        if (billingInvoiceId != null) {
            throw new IllegalStateException("Accrual " + id + " is already billed");
        }
        this.billingInvoiceId = invoiceId;
    }
}
