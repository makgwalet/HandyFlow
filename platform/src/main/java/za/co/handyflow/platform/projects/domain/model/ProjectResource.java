package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_resources")
@Getter
@NoArgsConstructor
public class ProjectResource {

    @Id UUID id;
    @Column(name = "tenant_id",   nullable = false) UUID       tenantId;
    @Column(name = "project_id",  nullable = false) UUID       projectId;
    @Column(name = "task_id")                        UUID       taskId;
    @Column(name = "resource_type", nullable = false, length = 20) String resourceType = "HUMAN";
    // resource_type: HUMAN | EQUIPMENT | VEHICLE | SUBCONTRACTOR
    @Column(name = "resource_id") UUID   resourceId;   // FK to user/asset/vehicle — nullable (cross-module)
    @Column(name = "resource_name", nullable = false, length = 200) String resourceName;
    @Column(length = 100) String role;
    @Column(name = "allocation_pct", nullable = false) BigDecimal allocationPct = BigDecimal.valueOf(100);
    @Column(name = "start_date")  LocalDate startDate;
    @Column(name = "end_date")    LocalDate endDate;
    @Column(name = "hourly_rate") BigDecimal hourlyRate;
    @Column(name = "daily_rate")  BigDecimal dailyRate;
    @Column(name = "planned_hours") BigDecimal plannedHours;
    @Column(name = "actual_hours",  nullable = false) BigDecimal actualHours = BigDecimal.ZERO;
    @Column(name = "created_at",  nullable = false) Instant createdAt;

    public static ProjectResource create(UUID tenantId, UUID projectId, UUID taskId,
                                         String resourceType, UUID resourceId, String resourceName,
                                         String role, BigDecimal allocationPct,
                                         LocalDate startDate, LocalDate endDate,
                                         BigDecimal hourlyRate, BigDecimal dailyRate,
                                         BigDecimal plannedHours) {
        ProjectResource r  = new ProjectResource();
        r.id               = UUID.randomUUID();
        r.tenantId         = tenantId;
        r.projectId        = projectId;
        r.taskId           = taskId;
        r.resourceType     = resourceType != null ? resourceType : "HUMAN";
        r.resourceId       = resourceId;
        r.resourceName     = resourceName;
        r.role             = role;
        r.allocationPct    = allocationPct != null ? allocationPct : BigDecimal.valueOf(100);
        r.startDate        = startDate;
        r.endDate          = endDate;
        r.hourlyRate       = hourlyRate;
        r.dailyRate        = dailyRate;
        r.plannedHours     = plannedHours;
        r.createdAt        = Instant.now();
        return r;
    }

    public void logHours(BigDecimal hours) {
        this.actualHours = this.actualHours.add(hours);
    }

    public void setRole(String v)            { this.role = v; }
    public void setAllocationPct(BigDecimal v){ this.allocationPct = v; }
    public void setHourlyRate(BigDecimal v)  { this.hourlyRate = v; }
    public void setDailyRate(BigDecimal v)   { this.dailyRate = v; }
    public void setPlannedHours(BigDecimal v){ this.plannedHours = v; }
    public void setEndDate(LocalDate v)      { this.endDate = v; }
}
