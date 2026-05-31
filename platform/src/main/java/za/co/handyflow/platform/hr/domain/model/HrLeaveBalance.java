package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hr_leave_balances")
@Getter
@NoArgsConstructor
public class HrLeaveBalance {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "leave_year")  int leaveYear;
    @Column(name = "leave_type")  String leaveType;
    @Column(name = "entitled_days") BigDecimal entitledDays;
    @Column(name = "taken_days")    BigDecimal takenDays    = BigDecimal.ZERO;
    @Column(name = "pending_days")  BigDecimal pendingDays  = BigDecimal.ZERO;
    @Column(name = "created_at")    Instant createdAt;
    @Column(name = "updated_at")    Instant updatedAt;

    public static HrLeaveBalance create(TenantId tenantId, UUID employeeId,
                                        int leaveYear, String leaveType,
                                        BigDecimal entitledDays) {
        HrLeaveBalance b = new HrLeaveBalance();
        b.id           = UUID.randomUUID();
        b.tenantId     = tenantId.getValue();
        b.employeeId   = employeeId;
        b.leaveYear    = leaveYear;
        b.leaveType    = leaveType;
        b.entitledDays = entitledDays;
        b.takenDays    = BigDecimal.ZERO;
        b.pendingDays  = BigDecimal.ZERO;
        b.createdAt    = Instant.now();
        b.updatedAt    = Instant.now();
        return b;
    }

    public BigDecimal getAvailableDays() {
        return entitledDays.subtract(takenDays).subtract(pendingDays);
    }

    public void addPending(BigDecimal days) {
        this.pendingDays = this.pendingDays.add(days);
        this.updatedAt   = Instant.now();
    }

    public void approvePending(BigDecimal days) {
        this.pendingDays = this.pendingDays.subtract(days);
        this.takenDays   = this.takenDays.add(days);
        this.updatedAt   = Instant.now();
    }

    public void rejectPending(BigDecimal days) {
        this.pendingDays = this.pendingDays.subtract(days);
        this.updatedAt   = Instant.now();
    }
}