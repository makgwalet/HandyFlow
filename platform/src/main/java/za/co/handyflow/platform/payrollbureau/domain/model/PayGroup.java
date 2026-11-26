package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A set of a client's employees paid on the same frequency and period calendar. */
@Entity
@Table(name = "pay_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayGroup {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_client_id", nullable = false)
    private UUID payClientId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "frequency", nullable = false)
    private String frequency; // MONTHLY | FORTNIGHTLY | WEEKLY

    @Column(name = "anchor_date")
    private LocalDate anchorDate;

    @Column(name = "pay_lag_days", nullable = false)
    private int payLagDays;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE | ARCHIVED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    public static PayGroup create(UUID tenantId, UUID payClientId, String name, String frequency,
                                  LocalDate anchorDate, int payLagDays) {
        PayGroup g = new PayGroup();
        g.tenantId = tenantId;
        g.payClientId = payClientId;
        g.name = name.trim();
        g.frequency = frequency;
        g.anchorDate = anchorDate;
        g.payLagDays = payLagDays;
        return g;
    }

    public void archive() {
        this.status = "ARCHIVED";
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
