package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A custom field for catalogue items. Tied to a category (and so to everything below it) or, with no category,
 * to every item. Code and type are fixed once created so stored values keep their meaning.
 */
@Getter
@Entity
@Table(name = "catalogue_attribute_definitions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueAttributeDefinition {

    /** Everything an edit may change. */
    public record Settings(String label, boolean required, List<String> options, BigDecimal min, BigDecimal max,
                           Integer maxLength, int sortOrder, boolean active) {}

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(nullable = false, updatable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 60)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, updatable = false, length = 10)
    private AttributeType type;

    @Column(nullable = false)
    private boolean required;

    @Column(columnDefinition = "TEXT")
    private String options;

    @Column(name = "min_value", precision = 19, scale = 6)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 19, scale = 6)
    private BigDecimal maxValue;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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

    public static CatalogueAttributeDefinition create(TenantId tenantId, UUID categoryId, String code, AttributeType type, Settings s) {
        CatalogueAttributeDefinition d = new CatalogueAttributeDefinition();
        d.tenantId = tenantId;
        d.categoryId = categoryId;
        d.code = code;
        d.type = type;
        d.createdAt = Instant.now();
        d.apply(s);
        return d;
    }

    public void update(Settings s) { apply(s); }

    private void apply(Settings s) {
        this.label = s.label();
        this.required = s.required();
        this.options = s.options() == null || s.options().isEmpty() ? null : String.join("\n", s.options());
        this.minValue = s.min();
        this.maxValue = s.max();
        this.maxLength = s.maxLength();
        this.sortOrder = s.sortOrder();
        this.active = s.active();
        this.updatedAt = Instant.now();
    }

    public List<String> optionList() {
        return options == null || options.isBlank() ? List.of() : Arrays.asList(options.split("\n"));
    }

    public void softDelete(UUID by) {
        this.deletedAt = Instant.now();
        this.deletedBy = by;
        this.updatedAt = this.deletedAt;
    }

    /** The view the pure rules work on. */
    public StructureRules.AttrSpec spec() {
        return new StructureRules.AttrSpec(id, code, label, type, required, optionList(), minValue, maxValue, maxLength);
    }
}
