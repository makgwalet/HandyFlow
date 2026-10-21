package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** Points added to a lead's score when it matches the condition. */
@Entity
@Table(name = "crm_scoring_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoringRule {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 20)
    private ConditionField field;

    @Enumerated(EnumType.STRING)
    @Column(name = "op", nullable = false, length = 10)
    private ConditionOp op;

    @Column(name = "value", nullable = false, length = 100)
    private String value;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static ScoringRule create(TenantId tenantId, String name, ConditionField field, ConditionOp op, String value, int points, boolean active) {
        var r = new ScoringRule();
        r.tenantId = tenantId;
        r.createdAt = Instant.now();
        r.change(name, field, op, value, points, active);
        return r;
    }

    public void change(String name, ConditionField field, ConditionOp op, String value, int points, boolean active) {
        this.name = name.strip();
        this.field = field;
        this.op = op;
        this.value = value.strip();
        this.points = points;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = this.deletedAt; }
}
