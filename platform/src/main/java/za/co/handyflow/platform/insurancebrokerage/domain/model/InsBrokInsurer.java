package za.co.handyflow.platform.insurancebrokerage.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The insurer(s)/underwriters this brokerage places business with —
 * master data, not a transactional record. Plain-entity, raw-UUID
 * provider-module convention (see package-info), same shape as
 * {@code CollAgencyClient} but simpler — an insurer has no commission
 * rate of its own (that lives on {@code InsBrokClient}/{@code
 * InsBrokPolicy} — the brokerage earns commission from the INSURER on
 * business placed, but the rate is negotiated per client/policy in this
 * MVP, not modelled per-insurer here; a per-insurer default rate is a
 * plausible real follow-up, flagged not built).
 */
@Entity
@Table(name = "insbrok_insurers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsBrokInsurer {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static InsBrokInsurer create(UUID tenantId, String name, String contactName, String contactEmail,
                                         String contactPhone, String notes) {
        InsBrokInsurer i = new InsBrokInsurer();
        i.tenantId = tenantId;
        i.name = name;
        i.contactName = contactName;
        i.contactEmail = contactEmail;
        i.contactPhone = contactPhone;
        i.notes = notes;
        Instant now = Instant.now();
        i.createdAt = now;
        i.updatedAt = now;
        return i;
    }

    public void update(String name, String contactName, String contactEmail, String contactPhone, String notes) {
        this.name = name;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
