package za.co.handyflow.platform.bookkeeping.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A client's monthly bookkeeping period — mirrors {@code
 * accountant.AccPeriod} exactly (materializes on first use via
 * resolve-or-create in {@code BkJournalService}, not pre-created).
 * OPEN -> CLOSED, with CLOSED blocking new/edited journal entries in
 * that period (a real bookkeeping-practice control: once management
 * accounts are prepared and sent, the underlying period shouldn't keep
 * moving).
 */
@Entity
@Table(name = "bk_periods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkPeriod {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(nullable = false)
    private String status = "OPEN"; // OPEN, CLOSED

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public static BkPeriod create(TenantId tenantId, UUID clientId, int periodYear, int periodMonth) {
        if (periodMonth < 1 || periodMonth > 12)
            throw new IllegalArgumentException("periodMonth must be 1-12");
        BkPeriod p = new BkPeriod();
        p.tenantId = tenantId;
        p.clientId = clientId;
        p.periodYear = periodYear;
        p.periodMonth = periodMonth;
        p.status = "OPEN";
        p.createdAt = Instant.now();
        return p;
    }

    public void close(UUID closedBy) {
        if ("CLOSED".equals(status)) throw new IllegalStateException("Period is already closed");
        this.status = "CLOSED";
        this.closedBy = closedBy;
        this.closedAt = Instant.now();
    }

    public void reopen() {
        if (!"CLOSED".equals(status)) throw new IllegalStateException("Period is not closed");
        this.status = "OPEN";
        this.closedBy = null;
        this.closedAt = null;
    }

    public boolean isOpen() { return "OPEN".equals(status); }
}
