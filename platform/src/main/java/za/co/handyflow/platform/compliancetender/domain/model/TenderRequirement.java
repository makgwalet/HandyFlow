package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per line in a tender's requirement matrix (source material's
 * own framing: "CIPC | Compliance | ✓", "Key personnel | HR | ✓", etc.).
 * {@code complianceRequirementId} links back to an existing
 * {@link ComplianceRequirement} when this line is a compliance item
 * already tracked there — reusing Phase 1's work rather than duplicating
 * requirement definitions. {@code source} stays a plain string for Phase
 * 2 ("COMPLIANCE", "PROJECTS", "HR", "FLEET", "ACCOUNTING", "MANUAL")
 * rather than a closed set tied to real module integrations, since those
 * integrations don't exist yet — this only tracks what a requirement
 * line WOULD be sourced from, not an actual live reference to another
 * module's record.
 */
@Entity
@Table(name = "tender_requirements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderRequirement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Column(name = "compliance_requirement_id")
    private UUID complianceRequirementId; // nullable — not every requirement line maps to a tracked ComplianceRequirement

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String source = "MANUAL"; // COMPLIANCE, PROJECTS, HR, FLEET, ACCOUNTING, MANUAL

    @Column(nullable = false)
    private String status = "PENDING_REVIEW"; // MET, MISSING, NOT_APPLICABLE, PENDING_REVIEW

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static TenderRequirement create(TenantId tenantId, UUID tenderId, UUID complianceRequirementId,
                                           String description, String source, UUID createdBy) {
        TenderRequirement r = new TenderRequirement();
        r.tenantId = tenantId;
        r.tenderId = tenderId;
        r.complianceRequirementId = complianceRequirementId;
        r.description = description;
        r.source = source != null ? source.toUpperCase() : "MANUAL";
        r.createdAt = Instant.now();
        r.createdBy = createdBy;
        r.updatedAt = Instant.now();
        r.updatedBy = createdBy;
        return r;
    }

    public void setStatus(String status, UUID updatedBy) {
        this.status = status;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
