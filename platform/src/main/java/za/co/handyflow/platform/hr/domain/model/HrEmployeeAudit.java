package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One thing that happened to an employee record. Holds field names, never the values of personal or pay data. */
@Entity
@Table(name = "hr_employee_audit")
@Getter
@NoArgsConstructor
public class HrEmployeeAudit {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    @Column(name = "employee_id") UUID employeeId;
    String action;
    String detail;
    @Column(name = "actor_id")    UUID actorId;
    @Column(name = "created_at")  Instant createdAt;

    public static HrEmployeeAudit of(UUID tenantId, UUID employeeId, String action, String detail, UUID actor) {
        HrEmployeeAudit a = new HrEmployeeAudit();
        a.id = UUID.randomUUID();
        a.tenantId = tenantId;
        a.employeeId = employeeId;
        a.action = action;
        a.detail = detail == null || detail.isBlank() ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail);
        a.actorId = actor;
        a.createdAt = Instant.now();
        return a;
    }
}
