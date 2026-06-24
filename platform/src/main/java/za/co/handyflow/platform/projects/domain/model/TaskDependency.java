package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "task_dependencies")
@Getter
@NoArgsConstructor
public class TaskDependency {

    @Id UUID id;
    @Column(name = "tenant_id",     nullable = false) UUID   tenantId;
    @Column(name = "predecessor_id", nullable = false) UUID  predecessorId;
    @Column(name = "successor_id",   nullable = false) UUID  successorId;
    // dependency_type: FS (Finish-Start) | SS | FF | SF
    @Column(name = "dependency_type", nullable = false, length = 3) String dependencyType = "FS";
    @Column(name = "lag_days",        nullable = false) int lagDays = 0;

    public static TaskDependency create(UUID tenantId, UUID predecessorId,
                                        UUID successorId, String dependencyType, int lagDays) {
        if (predecessorId.equals(successorId))
            throw new IllegalArgumentException("A task cannot depend on itself");
        TaskDependency d  = new TaskDependency();
        d.id              = UUID.randomUUID();
        d.tenantId        = tenantId;
        d.predecessorId   = predecessorId;
        d.successorId     = successorId;
        d.dependencyType  = dependencyType != null ? dependencyType : "FS";
        d.lagDays         = Math.max(0, lagDays);
        return d;
    }

    public void setLagDays(int v)          { this.lagDays        = v; }
    public void setDependencyType(String v){ this.dependencyType = v; }
}
