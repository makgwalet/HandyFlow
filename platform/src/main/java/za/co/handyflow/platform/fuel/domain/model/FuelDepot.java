package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_depots")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelDepot {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(length = 300)
    private String address;

    @Column(name = "legal_entity_id")
    private UUID legalEntityId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FuelDepot create(TenantId tenantId, String name, String code, String address) {
        FuelDepot d = new FuelDepot();
        d.tenantId  = tenantId;
        d.name      = name.trim();
        d.code      = code.trim().toUpperCase();
        d.address   = address == null || address.isBlank() ? null : address.trim();
        d.createdAt = Instant.now();
        d.updatedAt = Instant.now();
        return d;
    }

    public void update(String name, String code, String address) {
        this.name    = name.trim();
        this.code    = code.trim().toUpperCase();
        this.address = address == null || address.isBlank() ? null : address.trim();
        this.updatedAt = Instant.now();
    }

    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }
    public void activate()   { this.active = true;  this.updatedAt = Instant.now(); }
    public void softDelete() { this.deletedAt = Instant.now(); this.active = false; this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
