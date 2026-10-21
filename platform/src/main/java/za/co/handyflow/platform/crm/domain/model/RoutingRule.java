package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** Hands matching leads to a group of people in turn. A rule without a condition matches every lead. */
@Entity
@Table(name = "crm_routing_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutingRule {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "position", nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", length = 20)
    private ConditionField field;

    @Enumerated(EnumType.STRING)
    @Column(name = "op", length = 10)
    private ConditionOp op;

    @Column(name = "value", length = 100)
    private String value;

    /** Comma-separated user ids. */
    @Column(name = "assignee_ids", nullable = false, columnDefinition = "text")
    private String assigneeIds;

    @Column(name = "next_index", nullable = false)
    private int nextIndex;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static RoutingRule create(TenantId tenantId, int position, String name, ConditionField field, ConditionOp op, String value, String assigneeIds, boolean active) {
        var r = new RoutingRule();
        r.tenantId = tenantId;
        r.position = position;
        r.createdAt = Instant.now();
        r.change(name, field, op, value, assigneeIds, active);
        return r;
    }

    public void change(String name, ConditionField field, ConditionOp op, String value, String assigneeIds, boolean active) {
        this.name = name.strip();
        this.field = field;
        this.op = field == null ? null : op;
        this.value = field == null ? null : value.strip();
        if (!assigneeIds.equals(this.assigneeIds)) this.nextIndex = 0;   // a changed team starts again from the first person
        this.assigneeIds = assigneeIds;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void moveTo(int position) { this.position = position; this.updatedAt = Instant.now(); }

    public void advanceTo(int nextIndex) { this.nextIndex = nextIndex; }

    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = this.deletedAt; }
}
