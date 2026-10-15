package za.co.handyflow.platform.earthmoving.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A recurring task on one machine, by operating hours or by days. A daily check raises a work order when one is due. */
@Entity
@Table(name = "earthmoving_maintenance_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EarthMaintenancePlan {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(nullable = false)
    private String name;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(name = "interval_value", nullable = false)
    private int intervalValue;

    @Column(name = "lead_value", nullable = false)
    private int leadValue;

    @Column(name = "last_done_hours", precision = 10, scale = 1)
    private BigDecimal lastDoneHours;

    @Column(name = "last_done_date")
    private LocalDate lastDoneDate;

    @Column(nullable = false)
    private String priority = "NORMAL";

    @Column(name = "blocks_release", nullable = false)
    private boolean blocksRelease = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static EarthMaintenancePlan create(TenantId tenantId, UUID assetId, String name, String taskType, String trigger,
                                              int interval, int lead, BigDecimal baselineHours, LocalDate baselineDate,
                                              String priority, boolean blocksRelease) {
        WorkOrderRules.checkPlan(name, taskType, trigger, interval, lead);
        EarthmovingRules.oneOf(priority == null || priority.isBlank() ? "NORMAL" : priority, WorkOrderRules.PRIORITIES, "Priority");
        EarthMaintenancePlan p = new EarthMaintenancePlan();
        p.tenantId = tenantId;
        p.assetId = assetId;
        p.name = name.trim();
        p.taskType = taskType.trim().toUpperCase();
        p.triggerType = trigger.trim().toUpperCase();
        p.intervalValue = interval;
        p.leadValue = lead;
        p.lastDoneHours = baselineHours;
        p.lastDoneDate = baselineDate;
        p.priority = priority == null || priority.isBlank() ? "NORMAL" : priority.trim().toUpperCase();
        p.blocksRelease = blocksRelease;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public void update(String name, int interval, int lead, String priority, boolean blocksRelease, boolean active) {
        WorkOrderRules.checkPlan(name, taskType, triggerType, interval, lead);
        EarthmovingRules.oneOf(priority, WorkOrderRules.PRIORITIES, "Priority");
        this.name = name.trim();
        this.intervalValue = interval;
        this.leadValue = lead;
        this.priority = priority.trim().toUpperCase();
        this.blocksRelease = blocksRelease;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    /** The task was done: counting starts again from here. */
    public void markDone(BigDecimal hours, LocalDate date) {
        if (hours != null) this.lastDoneHours = hours;
        this.lastDoneDate = date;
        this.updatedAt = Instant.now();
    }

    public WorkOrderRules.PlanStatus status(BigDecimal currentHours, LocalDate today) {
        LocalDate baseDate = lastDoneDate != null ? lastDoneDate : createdAt.atZone(java.time.ZoneId.of("Africa/Johannesburg")).toLocalDate();
        return WorkOrderRules.evaluate(triggerType, intervalValue, leadValue, lastDoneHours, baseDate, currentHours, today);
    }
}
