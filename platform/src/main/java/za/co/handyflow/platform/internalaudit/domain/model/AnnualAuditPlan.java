package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_annual_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnualAuditPlan {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "plan_year", nullable = false) private int planYear;
    @Column(name = "status", nullable = false) private String status = "DRAFT"; // DRAFT | APPROVED | ACTIVE | CLOSED
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static AnnualAuditPlan create(UUID tenantId, int planYear, UUID createdBy) {
        AnnualAuditPlan p = new AnnualAuditPlan();
        p.tenantId = tenantId;
        p.planYear = planYear;
        p.createdBy = createdBy;
        p.createdAt = Instant.now();
        return p;
    }

    /** AUDIT_ADMIN-gated at the service/controller layer — a Head of Internal Audit action, matching the agreed role model. */
    public void approve(UUID approvedBy) {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("Only a DRAFT plan can be approved. Current status: " + status);
        }
        this.status = "APPROVED";
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
    }

    public void activate() {
        if (!"APPROVED".equals(status)) {
            throw new IllegalStateException("Only an APPROVED plan can be activated. Current status: " + status);
        }
        this.status = "ACTIVE";
    }

    public void close() {
        this.status = "CLOSED";
    }
}
