package za.co.handyflow.platform.legalpractice.domain.model;

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
 * A client-level standing retainer — an ongoing monthly fee independent
 * of any single matter, confirmed alongside per-matter billing via
 * AskUserQuestion ("Both"). Shaped directly on
 * {@code bookkeeping.BkServiceAgreement}: a start/end date pair and a
 * {@code coversDate(LocalDate)} resolution method, rather than
 * reinventing a new billing-period shape.
 */
@Entity
@Table(name = "lp_retainer_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpRetainerAgreement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "monthly_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate; // null = ongoing, no fixed end

    @Column(nullable = false, length = 20)
    private String status; // ACTIVE | CANCELLED

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpRetainerAgreement create(TenantId tenantId, UUID clientId, BigDecimal monthlyFee,
                                               LocalDate startDate, LocalDate endDate, String notes) {
        LpRetainerAgreement r = new LpRetainerAgreement();
        r.tenantId = tenantId;
        r.clientId = clientId;
        r.monthlyFee = monthlyFee;
        r.startDate = startDate != null ? startDate : LocalDate.now();
        r.endDate = endDate;
        r.notes = notes;
        r.status = "ACTIVE";
        r.createdAt = Instant.now();
        r.updatedAt = Instant.now();
        return r;
    }

    public void update(BigDecimal monthlyFee, LocalDate endDate, String notes) {
        this.monthlyFee = monthlyFee;
        this.endDate = endDate;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void cancel(LocalDate endDate) {
        this.status = "CANCELLED";
        this.endDate = endDate != null ? endDate : LocalDate.now();
        this.updatedAt = Instant.now();
    }

    /** Whether this agreement was active on the given date — mirrors FmServiceAgreement.coversDate(). */
    public boolean coversDate(LocalDate date) {
        if (!"ACTIVE".equals(this.status)) {
            return false;
        }
        if (date.isBefore(this.startDate)) {
            return false;
        }
        return this.endDate == null || !date.isAfter(this.endDate);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
