package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Append-only audit entry for a work item (raised, assigned, snoozed,
 * priority-overridden, completed, cancelled). Same immutability reasoning as
 * CollectionContactLog: no update/delete methods; corrections are new entries.
 * actorUserId is null for system-driven events (scheduler).
 */
@Entity
@Table(name = "debtcollection_work_item_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkItemEvent extends AggregateRoot<WorkItemEvent> {

    @Column(name = "work_item_id", nullable = false)
    private UUID workItemId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_user_name", length = 255)
    private String actorUserName;

    public static WorkItemEvent of(TenantId tenantId, UUID workItemId, UUID caseId, String eventType, String detail,
                                   UUID actorUserId, String actorUserName) {
        WorkItemEvent e = new WorkItemEvent();
        e.initTenantId(tenantId);
        e.workItemId = workItemId;
        e.caseId = caseId;
        e.eventType = eventType;
        e.detail = detail;
        e.actorUserId = actorUserId;
        e.actorUserName = actorUserName;
        return e;
    }
}
