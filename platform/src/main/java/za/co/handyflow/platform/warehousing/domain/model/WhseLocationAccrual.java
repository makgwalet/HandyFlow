package za.co.handyflow.platform.warehousing.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One day of the fixed pallet/bin fee for one position a client occupies. Figures never change after creation (the database
 * enforces it); the only later change is linking the row to the invoice that billed it.
 */
@Entity
@Table(name = "whse_location_accruals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhseLocationAccrual {

    public static final int DAYS_PER_MONTH = 30;

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false) private UUID clientId;
    @Column(name = "location_id", nullable = false) private UUID locationId;
    @Column(name = "accrual_date", nullable = false) private LocalDate accrualDate;
    @Column(name = "monthly_fee", nullable = false, precision = 12, scale = 2) private BigDecimal monthlyFee;
    @Column(name = "days_per_month", nullable = false) private int daysPerMonth = DAYS_PER_MONTH;
    @Column(name = "amount", nullable = false, precision = 15, scale = 4) private BigDecimal amount;
    @Column(name = "free_days_waived", nullable = false) private boolean freeDaysWaived;
    @Column(name = "billing_invoice_id") private UUID billingInvoiceId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    /** {@code waived}: everything held in the position that day was still inside its free days, so nothing is charged. */
    public static WhseLocationAccrual of(UUID tenantId, UUID clientId, UUID locationId, LocalDate date, BigDecimal monthlyFee, boolean waived) {
        if (monthlyFee == null || monthlyFee.signum() <= 0) throw new IllegalArgumentException("No fixed fee to accrue");
        WhseLocationAccrual a = new WhseLocationAccrual();
        a.tenantId = tenantId;
        a.clientId = clientId;
        a.locationId = locationId;
        a.accrualDate = date;
        a.monthlyFee = monthlyFee;
        a.freeDaysWaived = waived;
        a.amount = waived ? BigDecimal.ZERO.setScale(4)
                : monthlyFee.divide(BigDecimal.valueOf(DAYS_PER_MONTH), 4, RoundingMode.HALF_UP);
        return a;
    }

    public void markBilled(UUID invoiceId) {
        if (billingInvoiceId != null) throw new IllegalStateException("Location accrual " + id + " is already billed");
        this.billingInvoiceId = invoiceId;
    }
}
