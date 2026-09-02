package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * The FM company's own business profile — one row per tenant, matching
 * every sibling provider module's own "practice shell" entity
 * (CollAgencyProfile, WhseProfile, TrainProvProfile).
 */
@Entity
@Table(name = "fm_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "contact_email")
    private String contactEmail;
    @Column(name = "contact_phone")
    private String contactPhone;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FmProfile create(TenantId tenantId, String companyName, String registrationNumber,
                                    String contactEmail, String contactPhone, String notes) {
        FmProfile p = new FmProfile();
        p.tenantId = tenantId;
        p.companyName = companyName;
        p.registrationNumber = registrationNumber;
        p.contactEmail = contactEmail;
        p.contactPhone = contactPhone;
        p.notes = notes;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String companyName, String registrationNumber, String contactEmail,
                        String contactPhone, String notes) {
        if (companyName != null) this.companyName = companyName;
        this.registrationNumber = registrationNumber;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
