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
import java.util.Map;
import java.util.UUID;

/** A saved group of customers: a name and conditions. Who is in it is worked out when it is read, so it is never out of date. */
@Entity
@Table(name = "crm_segments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Segment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Each entry has the keys field, op and value. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, String>> conditions;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Segment create(TenantId tenantId, String name, List<Map<String, String>> conditions, UUID createdBy) {
        var s = new Segment();
        s.tenantId = tenantId;
        s.name = name.strip();
        s.conditions = List.copyOf(conditions);
        s.createdBy = createdBy;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    public void update(String name, List<Map<String, String>> conditions) {
        this.name = name.strip();
        this.conditions = List.copyOf(conditions);
        this.updatedAt = Instant.now();
    }
}
