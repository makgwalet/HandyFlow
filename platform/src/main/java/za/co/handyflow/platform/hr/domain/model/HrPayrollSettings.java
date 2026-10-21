package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Payroll choices a tenant makes for itself. No row means every default: nobody approves their own work. */
@Entity
@Table(name = "hr_payroll_settings")
@Getter
@NoArgsConstructor
public class HrPayrollSettings {

    @Id @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "allow_self_approval") boolean allowSelfApproval;
    @Column(name = "updated_by") UUID updatedBy;
    @Column(name = "updated_at") Instant updatedAt;

    public static HrPayrollSettings create(UUID tenantId) {
        HrPayrollSettings s = new HrPayrollSettings();
        s.tenantId = tenantId;
        return s;
    }

    public void setAllowSelfApproval(boolean allow, UUID actor) {
        this.allowSelfApproval = allow;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
