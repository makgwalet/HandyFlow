package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** The client-scoped counterpart to compliancetender.TenderRequirement — same reasoning throughout. */
@Entity
@Table(name = "client_tender_requirements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientTenderRequirement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_tender_id", nullable = false)
    private UUID clientTenderId;

    @Column(name = "client_requirement_id")
    private UUID clientRequirementId; // nullable — references ClientComplianceRequirement

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String source = "MANUAL";

    @Column(nullable = false)
    private String status = "PENDING_REVIEW";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static ClientTenderRequirement create(TenantId tenantId, UUID clientTenderId, UUID clientRequirementId,
                                                  String description, String source, UUID createdBy) {
        ClientTenderRequirement r = new ClientTenderRequirement();
        r.tenantId = tenantId;
        r.clientTenderId = clientTenderId;
        r.clientRequirementId = clientRequirementId;
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
