package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.AuditRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A checklist the provider wrote for its own audits. Its items live in versions that never change. Retired with a reason, never deleted. */
@Entity(name = "TrainProvAuditTemplate")
@Table(name = "trainprov_audit_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAuditTemplate {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "template_no", nullable = false, updatable = false)
    private String templateNo;
    @Column(nullable = false)
    private String name;
    @Column(name = "scope_type", nullable = false, updatable = false)
    private String scopeType;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "retired_reason")
    private String retiredReason;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvAuditTemplate create(TenantId t, String no, String name, String scope, UUID by) {
        TrainProvAuditTemplate x = new TrainProvAuditTemplate();
        x.tenantId = t.getValue();
        x.templateNo = no;
        x.name = name.trim();
        x.scopeType = scope;
        x.createdBy = by;
        x.createdAt = Instant.now();
        x.updatedAt = x.createdAt;
        return x;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public void retire(String reason) {
        if (!isActive()) {
            throw new IllegalStateException("This checklist is already retired");
        }
        String p = AuditRules.noteProblem(reason);
        if (p != null) {
            throw new IllegalArgumentException(p);
        }
        status = "RETIRED";
        retiredReason = reason.trim();
        updatedAt = Instant.now();
    }
}
