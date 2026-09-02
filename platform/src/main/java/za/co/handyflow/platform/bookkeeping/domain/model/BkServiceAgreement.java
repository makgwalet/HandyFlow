package za.co.handyflow.platform.bookkeeping.domain.model;

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
 * The commercial agreement between the bookkeeping practice and one
 * client, one per client — direct reuse of the {@code
 * FmServiceAgreement} shape established for Module 5b (confirmed with
 * you as the right billing model for this module too): RETAINER (flat
 * {@code monthlyFee}) or TIME_AND_MATERIALS ({@code hourlyRate}, billed
 * from {@link BkTimeEntry} records).
 */
@Entity
@Table(name = "bk_service_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkServiceAgreement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "billing_type", nullable = false)
    private String billingType; // RETAINER, TIME_AND_MATERIALS

    @Column(name = "monthly_fee", precision = 15, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "hourly_rate", precision = 15, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, ENDED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static BkServiceAgreement create(TenantId tenantId, UUID clientId, String billingType,
                                              BigDecimal monthlyFee, BigDecimal hourlyRate,
                                              LocalDate startDate, LocalDate endDate) {
        if (clientId == null) throw new IllegalArgumentException("clientId is required");
        if (startDate == null) throw new IllegalArgumentException("startDate is required");
        String type = billingType != null ? billingType.toUpperCase() : "TIME_AND_MATERIALS";
        if (!"RETAINER".equals(type) && !"TIME_AND_MATERIALS".equals(type))
            throw new IllegalArgumentException("billingType must be RETAINER or TIME_AND_MATERIALS");
        if ("RETAINER".equals(type) && (monthlyFee == null || monthlyFee.signum() <= 0))
            throw new IllegalArgumentException("A RETAINER agreement requires a positive monthlyFee");
        if (endDate != null && !endDate.isAfter(startDate))
            throw new IllegalArgumentException("endDate must be after startDate");

        BkServiceAgreement a = new BkServiceAgreement();
        a.tenantId = tenantId;
        a.clientId = clientId;
        a.billingType = type;
        a.monthlyFee = monthlyFee;
        a.hourlyRate = hourlyRate;
        a.startDate = startDate;
        a.endDate = endDate;
        a.status = "ACTIVE";
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public void update(BigDecimal monthlyFee, BigDecimal hourlyRate, LocalDate endDate) {
        if ("RETAINER".equals(billingType) && monthlyFee != null && monthlyFee.signum() <= 0)
            throw new IllegalArgumentException("monthlyFee must be positive");
        if (endDate != null && !endDate.isAfter(startDate))
            throw new IllegalArgumentException("endDate must be after startDate");
        this.monthlyFee = monthlyFee;
        this.hourlyRate = hourlyRate;
        this.endDate = endDate;
        this.updatedAt = Instant.now();
    }

    public void end() {
        this.status = "ENDED";
        if (this.endDate == null) this.endDate = LocalDate.now();
        this.updatedAt = Instant.now();
    }

    public boolean isActive() { return "ACTIVE".equals(status); }
    public boolean isRetainer() { return "RETAINER".equals(billingType); }

    public boolean coversDate(LocalDate date) {
        if (!isActive()) return false;
        if (date.isBefore(startDate)) return false;
        return endDate == null || !date.isAfter(endDate);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
