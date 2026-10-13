package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * End-of-morning snapshot of a tenant's collection exposure, one row per tenant per day. Outstanding balances have
 * no history of their own (they are read live from invoicing), so this is what makes a trend line possible.
 * It starts at deployment — earlier days cannot be reconstructed honestly. Re-running a day overwrites that day.
 */
@Entity
@Table(name = "debtcollection_exposure_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExposureSnapshot extends AggregateRoot<ExposureSnapshot> {

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "total_outstanding", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalOutstanding;

    @Column(name = "active_cases", nullable = false)
    private int activeCases;

    @Column(name = "bucket_0_30", nullable = false, precision = 15, scale = 2)
    private BigDecimal bucket0To30;

    @Column(name = "bucket_31_60", nullable = false, precision = 15, scale = 2)
    private BigDecimal bucket31To60;

    @Column(name = "bucket_61_90", nullable = false, precision = 15, scale = 2)
    private BigDecimal bucket61To90;

    @Column(name = "bucket_91_120", nullable = false, precision = 15, scale = 2)
    private BigDecimal bucket91To120;

    @Column(name = "bucket_121_plus", nullable = false, precision = 15, scale = 2)
    private BigDecimal bucket121Plus;

    public static ExposureSnapshot of(TenantId tenantId, LocalDate date, BigDecimal total, int activeCases, BigDecimal[] buckets) {
        if (buckets == null || buckets.length != 5) throw new IllegalArgumentException("Exactly five aging buckets are required");
        ExposureSnapshot s = new ExposureSnapshot();
        s.initTenantId(tenantId);
        s.snapshotDate = date;
        s.apply(total, activeCases, buckets);
        return s;
    }

    public void apply(BigDecimal total, int activeCases, BigDecimal[] b) {
        this.totalOutstanding = total;
        this.activeCases = activeCases;
        this.bucket0To30 = b[0];
        this.bucket31To60 = b[1];
        this.bucket61To90 = b[2];
        this.bucket91To120 = b[3];
        this.bucket121Plus = b[4];
    }
}
