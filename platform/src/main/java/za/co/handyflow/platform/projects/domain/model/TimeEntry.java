package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "time_entries")
@Getter
@NoArgsConstructor
public class TimeEntry {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID      tenantId;
    @Column(name = "project_id", nullable = false) UUID      projectId;
    @Column(name = "task_id")                       UUID      taskId;
    @Column(name = "user_id",    nullable = false) UUID      userId;
    @Column(name = "user_name",  nullable = false) String    userName;
    @Column(name = "entry_date", nullable = false) LocalDate entryDate;
    @Column(nullable = false)                      BigDecimal hours;
    String description;
    BigDecimal latitude;
    BigDecimal longitude;
    @Column(nullable = false) String status = "SUBMITTED";
    @Column(name = "approved_by")   UUID    approvedBy;
    @Column(name = "approved_at")   Instant approvedAt;
    @Column(name = "payroll_run_id") UUID   payrollRunId;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static TimeEntry create(UUID tenantId, UUID projectId, UUID taskId,
                                   UUID userId, String userName, LocalDate entryDate,
                                   BigDecimal hours, String description,
                                   BigDecimal lat, BigDecimal lng) {
        TimeEntry t    = new TimeEntry();
        t.id           = UUID.randomUUID();
        t.tenantId     = tenantId;
        t.projectId    = projectId;
        t.taskId       = taskId;
        t.userId       = userId;
        t.userName     = userName;
        t.entryDate    = entryDate;
        t.hours        = hours;
        t.description  = description;
        t.latitude     = lat;
        t.longitude    = lng;
        t.status       = "SUBMITTED";
        t.createdAt    = Instant.now();
        t.updatedAt    = Instant.now();
        return t;
    }

    public void approve(UUID approverId) {
        this.status     = "APPROVED";
        this.approvedBy = approverId;
        this.approvedAt = Instant.now();
        this.updatedAt  = Instant.now();
    }

    public void reject() { this.status = "REJECTED"; this.updatedAt = Instant.now(); }

    public void setPayrollRunId(UUID v) { this.payrollRunId = v; }
}
