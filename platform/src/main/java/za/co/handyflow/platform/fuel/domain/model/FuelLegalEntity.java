package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A company inside the tenant's group that trades in fuel. Licences, stock ownership and contracts attach to an entity, never to the tenant as a whole. */
@Entity
@Table(name = "fuel_legal_entities")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelLegalEntity {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 160) private String name;
    @Column(name = "registration_no", length = 40) private String registrationNo;
    @Column(name = "tax_no", length = 40) private String taxNo;
    @Column(name = "vat_no", length = 40) private String vatNo;
    @Column(length = 300) private String address;
    @Column(nullable = false, length = 12) private String status = "ACTIVE";
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private Long version;

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    public static FuelLegalEntity create(TenantId tenantId, String name, String reg, String tax, String vat, String address, UUID by) {
        FuelLegalEntity e = new FuelLegalEntity();
        e.tenantId = tenantId; e.createdBy = by;
        e.update(name, reg, tax, vat, address);
        return e;
    }

    public void update(String name, String reg, String tax, String vat, String address) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A legal entity needs a name.");
        this.name = name.trim(); this.registrationNo = clean(reg); this.taxNo = clean(tax); this.vatNo = clean(vat); this.address = clean(address);
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) { this.status = active ? "ACTIVE" : "INACTIVE"; this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.status = "INACTIVE"; this.updatedAt = Instant.now(); }
    public boolean isActive() { return "ACTIVE".equals(status); }

    @PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }
}
