package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "catalogue_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueCategory {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

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


    public static CatalogueCategory create(TenantId tenantId,
                                           String name,
                                           String description) {
        CatalogueCategory cat = new CatalogueCategory();
        cat.tenantId = tenantId;
        cat.name = name.trim();
        cat.description = description;
        cat.createdAt = Instant.now();
        cat.updatedAt = Instant.now();
        return cat;
    }

    public void rename(String newName) {
        this.name = newName.trim();
        this.updatedAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId){
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
