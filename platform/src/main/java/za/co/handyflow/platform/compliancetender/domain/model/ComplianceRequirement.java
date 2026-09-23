package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A versioned, reusable requirement definition (e.g. {@code CSD_ACTIVE},
 * {@code CIDB_GRADE}, {@code TAX_COMPLIANCE}) — the building block for a
 * later phase's applicability/readiness engine, not itself a readiness
 * score.
 * <p>
 * Tenant-scoped for Phase 1, deliberately: a shared, cross-tenant default
 * catalogue (so every tenant doesn't have to define "valid CSD
 * registration" themselves) is a real, separate design question — a
 * two-tier global/tenant-override model — left for a later phase rather
 * than half-built here. See the strategic roadmap backlog, Part 6.
 */
@Entity
@Table(name = "compliance_requirements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceRequirement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String code; // e.g. "CSD_ACTIVE", "CIDB_GRADE", "TAX_COMPLIANCE"

    @Column(nullable = false)
    private String name;

    @Column(name = "applies_to")
    private String appliesTo; // free text for Phase 1, e.g. "Government Tender", "Construction"

    @Column(name = "evidence_type")
    private String evidenceType; // expected ComplianceDocument.documentType this requirement is satisfied by

    @Column(nullable = false)
    private boolean required = true;

    @Column(name = "requirement_version", nullable = false)
    private int requirementVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static ComplianceRequirement create(TenantId tenantId, String code, String name, String appliesTo,
                                                String evidenceType, boolean required, UUID createdBy) {
        ComplianceRequirement r = new ComplianceRequirement();
        r.tenantId = tenantId;
        r.code = code != null ? code.toUpperCase() : null;
        r.name = name;
        r.appliesTo = appliesTo;
        r.evidenceType = evidenceType;
        r.required = required;
        r.requirementVersion = 1;
        r.createdAt = Instant.now();
        r.createdBy = createdBy;
        r.updatedAt = Instant.now();
        r.updatedBy = createdBy;
        return r;
    }

    /**
     * Requirements are versioned rather than edited in place — requirement
     * definitions can change (e.g. PSIRA's document list changes), and a
     * past application's readiness check should be judged against the
     * requirement version that was actually in force when it ran, not
     * silently reinterpreted against today's rules. Returns a new row; the
     * original is left untouched.
     */
    public ComplianceRequirement newVersion(String name, String appliesTo, String evidenceType,
                                            boolean required, UUID createdBy) {
        ComplianceRequirement next = new ComplianceRequirement();
        next.tenantId = this.tenantId;
        next.code = this.code;
        next.name = name;
        next.appliesTo = appliesTo;
        next.evidenceType = evidenceType;
        next.required = required;
        next.requirementVersion = this.requirementVersion + 1;
        next.createdAt = Instant.now();
        next.createdBy = createdBy;
        next.updatedAt = Instant.now();
        next.updatedBy = createdBy;
        return next;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
