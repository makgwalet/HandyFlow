package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A goal for one employee. Proposed by the employee and approved by the manager, or set by the manager. */
@Entity
@Table(name = "hr_perf_goals")
@Getter
@NoArgsConstructor
public class HrPerfGoal {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "cycle_id") UUID cycleId;
    String title;
    String description;
    String measure;
    @Column(name = "due_date") LocalDate dueDate;
    String status = PerfRules.PROPOSED;
    @JdbcTypeCode(SqlTypes.SMALLINT) int progress;
    @Column(name = "set_by") UUID setBy;
    @Column(name = "approved_by") UUID approvedBy;
    @Column(name = "approved_at") Instant approvedAt;
    @Column(name = "closed_by") UUID closedBy;
    @Column(name = "closed_at") Instant closedAt;
    @Column(name = "outcome_note") String outcomeNote;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;

    public static HrPerfGoal of(UUID tenantId, UUID employeeId, UUID cycleId, String title, String description, String measure, LocalDate due, String status, UUID by) {
        HrPerfGoal g = new HrPerfGoal();
        g.id = UUID.randomUUID();
        g.tenantId = tenantId;
        g.employeeId = employeeId;
        g.cycleId = cycleId;
        g.status = status;
        g.setBy = by;
        g.createdAt = Instant.now();
        if (PerfRules.ACTIVE.equals(status)) { g.approvedBy = by; g.approvedAt = g.createdAt; }
        g.edit(title, description, measure, due);
        return g;
    }

    public void edit(String title, String description, String measure, LocalDate due) {
        this.title = title.trim();
        this.description = blank(description);
        this.measure = blank(measure);
        this.dueDate = due;
        this.updatedAt = Instant.now();
    }

    public void approve(UUID by) { status = PerfRules.ACTIVE; approvedBy = by; approvedAt = Instant.now(); updatedAt = approvedAt; }
    public void progress(int percent) { this.progress = percent; this.updatedAt = Instant.now(); }

    public void close(String outcome, String note, UUID by) {
        status = outcome;
        outcomeNote = blank(note);
        closedBy = by;
        closedAt = Instant.now();
        updatedAt = closedAt;
        if (PerfRules.ACHIEVED.equals(outcome)) progress = 100;
    }

    public void cancel(String reason, UUID by) { status = PerfRules.CANCELLED; outcomeNote = reason.trim(); closedBy = by; closedAt = Instant.now(); updatedAt = closedAt; }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
