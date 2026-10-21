package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A field an account has defined for its own customers. Fields are switched off, never deleted, so stored values are not lost. */
@Entity
@Table(name = "crm_custom_fields")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomField {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    /** Stable machine name made from the first label. Never changes, so renaming the label keeps the stored values. */
    @Column(name = "field_key", nullable = false, length = 40, updatable = false)
    private String key;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20, updatable = false)
    private CustomFieldType type;

    @Column(name = "required", nullable = false)
    private boolean required;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private List<String> options;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CustomField create(TenantId tenantId, String key, String label, CustomFieldType type, boolean required, List<String> options, int sortOrder) {
        var f = new CustomField();
        f.tenantId = tenantId;
        f.key = key;
        f.label = label.strip();
        f.type = type;
        f.required = required;
        f.options = type == CustomFieldType.CHOICE ? List.copyOf(options) : null;
        f.sortOrder = sortOrder;
        f.createdAt = Instant.now();
        f.updatedAt = f.createdAt;
        return f;
    }

    /** The type and key cannot change. Options can only be changed on a choice field. */
    public void update(String label, boolean required, List<String> options, int sortOrder, boolean active) {
        this.label = label.strip();
        this.required = required;
        if (type == CustomFieldType.CHOICE) this.options = List.copyOf(options);
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = Instant.now();
    }
}
