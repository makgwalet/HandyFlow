package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A place a client operates from. */
@Entity
@Table(name = "compliance_client_sites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceClientSite {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String name;

    @Column(name = "site_type", nullable = false)
    private String siteType = "BRANCH";

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(nullable = false)
    private boolean active = true;

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

    public static ComplianceClientSite create(TenantId tenantId, UUID clientId, String name, String siteType,
                                              String address, Integer employeeCount, UUID by) {
        ComplianceClientSite s = new ComplianceClientSite();
        s.tenantId = tenantId;
        s.clientId = clientId;
        s.createdAt = Instant.now();
        s.createdBy = by;
        s.apply(name, siteType, address, employeeCount, by);
        return s;
    }

    public void apply(String name, String siteType, String address, Integer employeeCount, UUID by) {
        String n = ClientProfileRules.clean(name, 200);
        if (n == null) throw new IllegalArgumentException("name is required");
        String type = ClientProfileRules.requireOneOf("siteType", siteType == null ? "BRANCH" : siteType, ClientPartyRules.SITE_TYPES);
        this.name = n;
        this.siteType = type;
        this.address = ClientProfileRules.clean(address, 1000);
        this.employeeCount = ClientProfileRules.employeeCount(employeeCount);
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }

    public void setActive(boolean active, UUID by) {
        this.active = active;
        this.updatedAt = Instant.now();
        this.updatedBy = by;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
