package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A review cycle: the period being reviewed, when the reviews are due, and whether 360 feedback is part of it. */
@Entity
@Table(name = "hr_perf_cycles")
@Getter
@NoArgsConstructor
public class HrPerfCycle {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    String name;
    @Column(name = "period_start") LocalDate periodStart;
    @Column(name = "period_end") LocalDate periodEnd;
    @Column(name = "self_due") LocalDate selfDue;
    @Column(name = "manager_due") LocalDate managerDue;
    @Column(name = "include_360") boolean include360;
    String status = PerfRules.DRAFT;
    @Column(name = "launched_at") Instant launchedAt;
    @Column(name = "launched_by") UUID launchedBy;
    @Column(name = "closed_at") Instant closedAt;
    @Column(name = "closed_by") UUID closedBy;
    @Column(name = "created_by") UUID createdBy;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;

    public static HrPerfCycle of(UUID tenantId, String name, LocalDate start, LocalDate end, LocalDate selfDue, LocalDate managerDue, boolean include360, UUID by) {
        HrPerfCycle c = new HrPerfCycle();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.createdBy = by;
        c.createdAt = Instant.now();
        c.edit(name, start, end, selfDue, managerDue, include360);
        return c;
    }

    public void edit(String name, LocalDate start, LocalDate end, LocalDate selfDue, LocalDate managerDue, boolean include360) {
        this.name = name.trim();
        this.periodStart = start;
        this.periodEnd = end;
        this.selfDue = selfDue;
        this.managerDue = managerDue;
        this.include360 = include360;
        this.updatedAt = Instant.now();
    }

    public void launch(UUID by) { status = PerfRules.OPEN; launchedAt = Instant.now(); launchedBy = by; updatedAt = launchedAt; }
    public void close(UUID by) { status = PerfRules.CLOSED; closedAt = Instant.now(); closedBy = by; updatedAt = closedAt; }
}
