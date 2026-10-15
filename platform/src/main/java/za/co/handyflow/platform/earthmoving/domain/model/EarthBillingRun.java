package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** One entry in the history of monthly billing runs: which month, who or what started it, and what it did. */
@Entity
@Table(name = "earthmoving_billing_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthBillingRun {

    public static final String MANUAL = "MANUAL";
    public static final String SCHEDULED = "SCHEDULED";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "trigger_source", nullable = false)
    private String triggerSource;

    @Column(name = "drafts_requested", nullable = false)
    private boolean draftsRequested;

    @Column(name = "charges_created", nullable = false)
    private int chargesCreated;

    @Column(name = "already_billed", nullable = false)
    private int alreadyBilled;

    @Column(nullable = false)
    private int exceptions;

    @Column(name = "invoices_created", nullable = false)
    private int invoicesCreated;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static EarthBillingRun of(TenantId tenantId, YearMonth month, String trigger, boolean drafts, int charges, int billed,
                                     int exceptions, int invoices, UUID actor) {
        EarthBillingRun r = new EarthBillingRun();
        r.tenantId = tenantId;
        r.periodMonth = month.atDay(1);
        r.triggerSource = trigger;
        r.draftsRequested = drafts;
        r.chargesCreated = charges;
        r.alreadyBilled = billed;
        r.exceptions = exceptions;
        r.invoicesCreated = invoices;
        r.createdBy = actor;
        r.createdAt = Instant.now();
        return r;
    }

    /** Fills in what the run did. The row is written first on a scheduled run so a second server cannot start the same month. */
    public void finish(int charges, int billed, int exceptions, int invoices) {
        this.chargesCreated = charges;
        this.alreadyBilled = billed;
        this.exceptions = exceptions;
        this.invoicesCreated = invoices;
    }
}
