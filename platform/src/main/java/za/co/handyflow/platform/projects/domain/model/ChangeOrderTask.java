package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A task a change order delays, and by how many WORKING days. Dependents are pushed out on approval. */
@Entity
@Table(name = "change_order_tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeOrderTask {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)       private UUID tenantId;
    @Column(name = "change_order_id", nullable = false) private UUID changeOrderId;
    @Column(name = "task_id", nullable = false)         private UUID taskId;
    @Column(name = "delay_days", nullable = false)      private int delayDays;

    public static ChangeOrderTask create(UUID tenantId, UUID changeOrderId, UUID taskId, int delayDays) {
        ChangeOrderTask t = new ChangeOrderTask();
        t.id            = UUID.randomUUID();
        t.tenantId      = tenantId;
        t.changeOrderId = changeOrderId;
        t.taskId        = taskId;
        t.delayDays     = delayDays;
        return t;
    }
}