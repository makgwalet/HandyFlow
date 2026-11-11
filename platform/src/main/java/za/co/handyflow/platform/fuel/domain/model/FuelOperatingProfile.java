package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * One row per tenant. NOT_STARTED/IN_PROGRESS tenants are still setting up; ACTIVE ones have chosen their divisions.
 * {@code legacyMode} marks a tenant that used Fuel before divisions existed: nothing is switched off for it until setup is completed.
 */
@Entity
@Table(name = "fuel_operating_profiles")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelOperatingProfile {

    public static final Set<String> SIZES = Set.of("SMALL", "MEDIUM", "LARGE", "ENTERPRISE");
    public static final Set<String> ORG = Set.of("ONE_ENTITY", "MULTIPLE_ENTITIES", "NOT_CONFIGURED");
    public static final int MAX_ANSWERS = 8000;

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 16) private String status = "NOT_STARTED";
    @Column(name = "legacy_mode", nullable = false) private boolean legacyMode;
    @Column(name = "business_size", length = 16) private String businessSize;
    @Column(name = "org_structure", length = 24) private String orgStructure;
    @Column(name = "wizard_step", nullable = false) private int wizardStep;
    @Column(name = "wizard_answers", columnDefinition = "TEXT") private String wizardAnswers;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "activated_by") private UUID activatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Version private Long version;

    public static FuelOperatingProfile forTenant(TenantId tenantId) {
        FuelOperatingProfile p = new FuelOperatingProfile();
        p.tenantId = tenantId;
        return p;
    }

    /** Saves wizard progress. Does not activate anything, and never moves an ACTIVE profile back. */
    public void saveProgress(String size, String org, int step, String answers) {
        if (size != null && !size.isBlank() && !SIZES.contains(size)) throw new IllegalArgumentException("Business size must be one of " + SIZES + ".");
        if (org != null && !org.isBlank() && !ORG.contains(org)) throw new IllegalArgumentException("Organisation structure must be one of " + ORG + ".");
        if (step < 0 || step > 20) throw new IllegalArgumentException("Unknown wizard step.");
        if (answers != null && answers.length() > MAX_ANSWERS) throw new IllegalArgumentException("Setup answers are too long.");
        this.businessSize = size == null || size.isBlank() ? null : size;
        this.orgStructure = org == null || org.isBlank() ? null : org;
        this.wizardStep = step;
        this.wizardAnswers = answers == null || answers.isBlank() ? null : answers;
        if (!"ACTIVE".equals(status)) this.status = "IN_PROGRESS";
        this.updatedAt = Instant.now();
    }

    public void activate(UUID by) {
        this.status = "ACTIVE"; this.legacyMode = false; this.activatedAt = Instant.now(); this.activatedBy = by; this.updatedAt = Instant.now();
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    @PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }
}
