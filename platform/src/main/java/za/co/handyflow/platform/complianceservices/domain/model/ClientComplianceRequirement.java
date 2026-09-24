package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * The client-scoped counterpart to compliancetender.ComplianceRequirement
 * — same reasoning as ClientComplianceRegistration's own Javadoc for the
 * parallel-table design, and the same versioning discipline: a past
 * readiness check for this CLIENT should be judged against the rules
 * that were actually in force when it ran, not silently reinterpreted
 * against today's. Requirements are versioned rather than edited in
 * place, and there is deliberately no DELETE for this entity, for
 * exactly the same reason compliancetender.ComplianceRequirement has
 * none — see ComplianceRequirementService's own Javadoc.
 */
@Entity
@Table(name = "client_compliance_requirements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientComplianceRequirement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "applies_to")
    private String appliesTo;

    @Column(name = "evidence_type")
    private String evidenceType;

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

    public static ClientComplianceRequirement create(TenantId tenantId, UUID clientId, String code, String name,
                                                      String appliesTo, String evidenceType, boolean required,
                                                      UUID createdBy) {
        ClientComplianceRequirement r = new ClientComplianceRequirement();
        r.tenantId = tenantId;
        r.clientId = clientId;
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

    public ClientComplianceRequirement newVersion(String name, String appliesTo, String evidenceType,
                                                   boolean required, UUID createdBy) {
        ClientComplianceRequirement next = new ClientComplianceRequirement();
        next.tenantId = this.tenantId;
        next.clientId = this.clientId;
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
