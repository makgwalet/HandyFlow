package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * The client company — resolves the open design question left in this
 * module's own package-info.java. A dedicated entity that OPTIONALLY
 * references an existing CRM customer via {@code crmCustomerId} (a
 * reference, not a copy — enriched with live CRM data at read time, the
 * same pattern {@code TenderPersonnel} already established for HR
 * employee references), rather than extending {@code crm.Customer}
 * itself. Chosen because it doesn't force a schema change onto CRM's own
 * domain model for a still-unproven second module, and a compliance
 * client carries data a sales customer never would — a mandate/
 * authority-to-act, compliance-specific contacts that may differ from
 * whoever CRM's own sales contact is.
 * <p>
 * {@code tenantId} here is the SERVICE PROVIDER's own tenant (the
 * accounting practice, the consultancy) — not the client company's own
 * tenant, if the client company even has a HandyFlow account at all.
 * This entity exists entirely within the service provider's own data,
 * the same way a CRM customer record does.
 */
@Entity
@Table(name = "compliance_service_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "crm_customer_id")
    private UUID crmCustomerId; // nullable — a client company doesn't need to already be a CRM customer

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "mandate_notes", columnDefinition = "TEXT")
    private String mandateNotes;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static ComplianceClient create(TenantId tenantId, String name, UUID crmCustomerId,
                                          String contactEmail, String contactPhone, String mandateNotes, UUID createdBy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        ComplianceClient c = new ComplianceClient();
        c.tenantId = tenantId;
        c.name = name;
        c.crmCustomerId = crmCustomerId;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.mandateNotes = mandateNotes;
        c.createdAt = Instant.now();
        c.createdBy = createdBy;
        c.updatedAt = Instant.now();
        c.updatedBy = createdBy;
        return c;
    }

    public void update(String name, String contactEmail, String contactPhone, String mandateNotes, UUID updatedBy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        this.name = name;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.mandateNotes = mandateNotes;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    public void deactivate(UUID updatedBy) {
        this.status = "INACTIVE";
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    public void reactivate(UUID updatedBy) {
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
