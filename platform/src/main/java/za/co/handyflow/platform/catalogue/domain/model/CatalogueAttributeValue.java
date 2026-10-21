package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One attribute value of one item, stored as text in the form {@link StructureRules} writes. */
@Getter
@Entity
@Table(name = "catalogue_item_attribute_values")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueAttributeValue {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "definition_id", nullable = false, updatable = false)
    private UUID definitionId;

    @Column(name = "value_text", nullable = false, length = 500)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CatalogueAttributeValue of(TenantId tenantId, UUID itemId, UUID definitionId, String value) {
        CatalogueAttributeValue v = new CatalogueAttributeValue();
        v.tenantId = tenantId;
        v.itemId = itemId;
        v.definitionId = definitionId;
        v.value = value;
        v.updatedAt = Instant.now();
        return v;
    }

    public void setValue(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }
}
