package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A reusable place where machines work: a depot, a customer's premises or a project site. It can exist without a
 * project or a customer. Customer and project are references to the CRM and Projects modules, checked when saved.
 * Renaming a site does not change past deployments, which keep the name they were made with.
 */
@Entity
@Table(name = "earthmoving_sites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthSite {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "supervisor_user_id")
    private UUID supervisorUserId;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static EarthSite create(TenantId tenantId, String name, String address, BigDecimal latitude, BigDecimal longitude,
                                   UUID customerId, UUID projectId, UUID supervisorUserId, String notes) {
        SiteRules.checkSite(name, latitude, longitude);
        EarthSite s = new EarthSite();
        s.tenantId = tenantId;
        s.createdAt = Instant.now();
        s.apply(name, address, latitude, longitude, customerId, projectId, supervisorUserId, notes);
        return s;
    }

    public void update(String name, String address, BigDecimal latitude, BigDecimal longitude,
                       UUID customerId, UUID projectId, UUID supervisorUserId, String notes) {
        SiteRules.checkSite(name, latitude, longitude);
        apply(name, address, latitude, longitude, customerId, projectId, supervisorUserId, notes);
    }

    private void apply(String name, String address, BigDecimal latitude, BigDecimal longitude,
                       UUID customerId, UUID projectId, UUID supervisorUserId, String notes) {
        this.name = name.trim();
        this.address = address == null || address.isBlank() ? null : address.trim();
        this.latitude = latitude;
        this.longitude = longitude;
        this.customerId = customerId;
        this.projectId = projectId;
        this.supervisorUserId = supervisorUserId;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        if (!active) throw new IllegalStateException("This site is already inactive.");
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
