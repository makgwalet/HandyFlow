package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** One option of a parent item (Size) and the values offered for it. Values never contain '|' (see VariantRules). */
@Getter
@Entity
@Table(name = "catalogue_variant_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueVariantOption {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "parent_item_id", nullable = false, updatable = false)
    private UUID parentItemId;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "option_values", nullable = false, length = 1100)
    private String valuesText;

    public static CatalogueVariantOption create(TenantId tenantId, UUID parentItemId, String name, int sortOrder, List<String> values) {
        CatalogueVariantOption o = new CatalogueVariantOption();
        o.tenantId = tenantId;
        o.parentItemId = parentItemId;
        o.name = name;
        o.sortOrder = sortOrder;
        o.valuesText = String.join("|", values);
        return o;
    }

    public List<String> values() { return valuesText.isEmpty() ? List.of() : Arrays.asList(valuesText.split("\\|")); }

    public void setValues(List<String> values) { this.valuesText = String.join("|", values); }
}
