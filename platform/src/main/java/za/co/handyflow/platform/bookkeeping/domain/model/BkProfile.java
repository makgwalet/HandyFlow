package za.co.handyflow.platform.bookkeeping.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * The bookkeeping practice's own profile — mirrors {@code FmProfile}/
 * {@code AccountantProfile}'s own shape exactly (one per tenant).
 */
@Entity
@Table(name = "bk_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BkProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "practice_name", nullable = false)
    private String practiceName;

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

    public static BkProfile create(TenantId tenantId, String practiceName, String registrationNumber,
                                    String contactEmail, String contactPhone, String notes) {
        if (practiceName == null || practiceName.isBlank())
            throw new IllegalArgumentException("practiceName is required");
        BkProfile p = new BkProfile();
        p.tenantId = tenantId;
        p.practiceName = practiceName;
        p.registrationNumber = registrationNumber;
        p.contactEmail = contactEmail;
        p.contactPhone = contactPhone;
        p.notes = notes;
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String practiceName, String registrationNumber, String contactEmail,
                        String contactPhone, String notes) {
        if (practiceName != null) this.practiceName = practiceName;
        this.registrationNumber = registrationNumber;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
