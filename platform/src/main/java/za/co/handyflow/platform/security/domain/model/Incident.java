package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;

/**
 * Aggregate root for a security incident reported at a site.
 *
 * Lifecycle:  OPEN → ACKNOWLEDGED → RESOLVED
 */
@Entity
@Table(name = "security_incidents")
@Getter
@NoArgsConstructor
public class Incident {

    @Id
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id"))
    private TenantId tenantId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "guard_id")
    private UUID guardId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * LOW | MEDIUM | HIGH | CRITICAL
     */
    @Column(nullable = false)
    private String severity;

    /**
     * OPEN | ACKNOWLEDGED | RESOLVED
     */
    @Column(nullable = false)
    private String status;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Incident create(TenantId tenantId, UUID siteId, UUID shiftId, UUID guardId,
                                  String title, String description, String severity,
                                  BigDecimal latitude, BigDecimal longitude) {
        validateSeverity(severity);
        Incident i = new Incident();
        i.id          = UUID.randomUUID();
        i.tenantId    = tenantId;
        i.siteId      = siteId;
        i.shiftId     = shiftId;
        i.guardId     = guardId;
        i.title       = title;
        i.description = description;
        i.severity    = severity.toUpperCase();
        i.status      = "OPEN";
        i.latitude    = latitude;
        i.longitude   = longitude;
        i.createdAt   = Instant.now();
        i.updatedAt   = Instant.now();
        return i;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void acknowledge() {
        if ("RESOLVED".equals(this.status)) {
            throw new HandyFlowException(
                    "Cannot acknowledge a resolved incident", HttpStatus.BAD_REQUEST, "INVALID_TRANSITION");
        }
        this.status          = "ACKNOWLEDGED";
        this.acknowledgedAt  = Instant.now();
        this.updatedAt       = Instant.now();
    }

    public void resolve() {
        this.status     = "RESOLVED";
        this.resolvedAt = Instant.now();
        this.updatedAt  = Instant.now();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private static void validateSeverity(String severity) {
        if (!java.util.Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL")
                .contains(severity.toUpperCase())) {
            throw new HandyFlowException(
                    "Invalid severity: " + severity, HttpStatus.BAD_REQUEST, "INVALID_SEVERITY");
        }
    }
}
