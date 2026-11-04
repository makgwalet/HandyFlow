package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A half-finished employee registration. Belongs to the person who started it; not an employee. */
@Entity
@Table(name = "hr_employee_drafts")
@Getter
@NoArgsConstructor
public class HrEmployeeDraft {

    @Id UUID id;
    @Column(name = "tenant_id")  UUID tenantId;
    @Column(name = "owner_id")   UUID ownerId;
    String label;
    int step;
    String payload;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    public static HrEmployeeDraft create(TenantId tenantId, UUID ownerId, String label, int step, String payload) {
        HrEmployeeDraft d = new HrEmployeeDraft();
        d.id = UUID.randomUUID();
        d.tenantId = tenantId.getValue();
        d.ownerId = ownerId;
        d.createdAt = Instant.now();
        d.replace(label, step, payload);
        return d;
    }

    public void replace(String label, int step, String payload) {
        this.label = label;
        this.step = step;
        this.payload = payload;
        this.updatedAt = Instant.now();
    }
}
