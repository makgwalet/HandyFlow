package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hr_leave_requests")
@Getter
@NoArgsConstructor
public class HrLeaveRequest {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID tenantId;
    @Column(name = "employee_id")   UUID employeeId;
    @Column(name = "leave_type")    String leaveType;
    @Column(name = "start_date")    LocalDate startDate;
    @Column(name = "end_date")      LocalDate endDate;
    @Column(name = "days_requested") BigDecimal daysRequested;
    String reason;
    String status = "PENDING";
    @Column(name = "approved_by")     UUID approvedBy;
    @Column(name = "approved_at")     Instant approvedAt;
    @Column(name = "rejection_reason") String rejectionReason;
    @Column(name = "created_at")      Instant createdAt;
    @Column(name = "updated_at")      Instant updatedAt;

    public static HrLeaveRequest create(TenantId tenantId, UUID employeeId,
                                        String leaveType, LocalDate startDate,
                                        LocalDate endDate, BigDecimal daysRequested,
                                        String reason) {
        HrLeaveRequest r = new HrLeaveRequest();
        r.id             = UUID.randomUUID();
        r.tenantId       = tenantId.getValue();
        r.employeeId     = employeeId;
        r.leaveType      = leaveType;
        r.startDate      = startDate;
        r.endDate        = endDate;
        r.daysRequested  = daysRequested;
        r.reason         = reason;
        r.status         = "PENDING";
        r.createdAt      = Instant.now();
        r.updatedAt      = Instant.now();
        return r;
    }

    public void approve(UUID approvedBy) {
        this.status     = "APPROVED";
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
        this.updatedAt  = Instant.now();
    }

    public void reject(UUID approvedBy, String reason) {
        this.status           = "REJECTED";
        this.approvedBy       = approvedBy;
        this.approvedAt       = Instant.now();
        this.rejectionReason  = reason;
        this.updatedAt        = Instant.now();
    }

    public void cancel() {
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }
}