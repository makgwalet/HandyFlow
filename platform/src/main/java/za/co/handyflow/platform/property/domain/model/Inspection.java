// property/domain/model/Inspection.java

package za.co.handyflow.platform.property.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "property_inspections")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Inspection {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "lease_id")
    private UUID leaseId;

    @Column(nullable = false)
    private String type;

    @Column(name = "inspected_at", nullable = false)
    private Instant inspectedAt;

    @Column(name = "inspected_by")
    private String inspectedBy;

    @Column(name = "overall_condition")
    private String overallCondition = "GOOD";

    private String notes;

    // WHY JSONB items? Room-by-room condition:
    // [{"room":"Kitchen","condition":"GOOD","notes":"Oven working"},...]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, String>> items;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photo_urls", columnDefinition = "jsonb")
    private List<String> photoUrls;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Inspection create(TenantId tenantId, UUID unitId, UUID leaseId,
                                    String type, Instant inspectedAt,
                                    String inspectedBy, String overallCondition,
                                    String notes, List<Map<String, String>> items) {
        Inspection i = new Inspection();
        i.tenantId         = tenantId;
        i.unitId           = unitId;
        i.leaseId          = leaseId;
        i.type             = type.toUpperCase();
        i.inspectedAt      = inspectedAt;
        i.inspectedBy      = inspectedBy;
        i.overallCondition = overallCondition != null
                ? overallCondition.toUpperCase() : "GOOD";
        i.notes            = notes;
        i.items            = items;
        i.createdAt        = Instant.now();
        i.updatedAt        = Instant.now();
        return i;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}