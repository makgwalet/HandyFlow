package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One auditable entity in the audit universe — for a GL-focused internal
 * audit function, most naturally a business process or GL account group
 * (Payroll, Procurement, Journal Entries generally, Period-End Close),
 * not an individual system module. processArea is deliberately free
 * text, not an enum — this genuinely varies per tenant's own business,
 * unlike the fixed vocabularies used elsewhere in this codebase.
 */
@Entity
@Table(name = "audit_universe_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditUniverseEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "name", nullable = false) private String name;
    @Column(name = "description") private String description;
    @Column(name = "process_area") private String processArea;
    @Column(name = "gl_account_group") private String glAccountGroup;
    @Column(name = "last_audit_date") private LocalDate lastAuditDate;
    @Column(name = "active", nullable = false) private boolean active = true;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static AuditUniverseEntry create(UUID tenantId, String name, String description,
                                            String processArea, String glAccountGroup, UUID createdBy) {
        AuditUniverseEntry e = new AuditUniverseEntry();
        e.tenantId = tenantId;
        e.name = name;
        e.description = description;
        e.processArea = processArea;
        e.glAccountGroup = glAccountGroup;
        e.createdBy = createdBy;
        e.createdAt = Instant.now();
        return e;
    }

    public void update(String name, String description, String processArea, String glAccountGroup) {
        this.name = name;
        this.description = description;
        this.processArea = processArea;
        this.glAccountGroup = glAccountGroup;
    }

    public void deactivate() { this.active = false; }
    public void reactivate() { this.active = true; }

    public void recordAuditCompleted(LocalDate completedOn) {
        this.lastAuditDate = completedOn;
    }

    /**
     * Feeds RiskAssessment's system-calculated "time since last audit"
     * input — the one factor in the V1 formula that's genuinely
     * derivable from data rather than requiring an auditor's own
     * judgment. Never audited (lastAuditDate null) scores the maximum,
     * matching the intuition that an unaudited area is inherently
     * higher-priority than a recently-audited one.
     */
    public int timeSinceLastAuditScore() {
        if (lastAuditDate == null) return 5;
        long years = ChronoUnit.YEARS.between(lastAuditDate, LocalDate.now());
        if (years < 1) return 1;
        if (years < 2) return 2;
        if (years < 3) return 3;
        if (years < 4) return 4;
        return 5;
    }
}
