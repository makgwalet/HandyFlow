package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** The schedule an employee works. */
@Entity
@Table(name = "hr_employee_schedules")
@Getter
@NoArgsConstructor
public class HrEmployeeSchedule {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "schedule_id") UUID scheduleId;
    @Column(name = "assigned_by") UUID assignedBy;
    @Column(name = "assigned_at") Instant assignedAt;
    @Version long version;

    public static HrEmployeeSchedule of(UUID tenantId, UUID employeeId, UUID scheduleId, UUID by) {
        HrEmployeeSchedule a = new HrEmployeeSchedule();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId;
        a.employeeId = employeeId;
        a.scheduleId = scheduleId;
        a.assignedBy = by;
        a.assignedAt = Instant.now();
        return a;
    }

    public void reassign(UUID scheduleId, UUID by) { this.scheduleId = scheduleId; this.assignedBy = by; this.assignedAt = Instant.now(); }
}
