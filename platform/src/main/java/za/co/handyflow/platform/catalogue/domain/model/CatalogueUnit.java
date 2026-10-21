package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A unit of measure of one tenant ("each", "m²", "hour"). Items hold the unit's code. */
@Getter
@Entity
@Table(name = "catalogue_units")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueUnit {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UnitKind kind = UnitKind.OTHER;

    @Column(nullable = false)
    private int decimals = 2;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private Long version;

    public static CatalogueUnit create(TenantId tenantId, String code, String name, UnitKind kind, int decimals) {
        CatalogueUnit u = new CatalogueUnit();
        u.tenantId = tenantId;
        u.code = code;
        u.name = name;
        u.kind = kind == null ? UnitKind.OTHER : kind;
        u.decimals = decimals;
        u.createdAt = Instant.now();
        u.updatedAt = u.createdAt;
        return u;
    }

    /** The code is not editable: items and other modules hold it as text. */
    public void update(String name, UnitKind kind, int decimals, boolean active) {
        this.name = name;
        this.kind = kind == null ? UnitKind.OTHER : kind;
        this.decimals = decimals;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID by) {
        this.deletedAt = Instant.now();
        this.deletedBy = by;
        this.updatedAt = this.deletedAt;
    }
}
