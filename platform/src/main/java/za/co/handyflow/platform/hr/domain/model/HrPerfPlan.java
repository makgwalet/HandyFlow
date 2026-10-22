package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An improvement plan: the shortfall, the standard required, the support the company gives and the time allowed. Once the employee has been
 * told, the database refuses to rewrite those (V495); the time allowed can be lengthened but not shortened.
 */
@Entity
@Table(name = "hr_perf_plans")
@Getter
@NoArgsConstructor
public class HrPerfPlan {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "owner_id") UUID ownerId;
    @Column(name = "case_id") UUID caseId;
    @Column(name = "review_id") UUID reviewId;
    String title;
    String shortfall;
    String standard;
    String support;
    @Column(name = "start_date") LocalDate startDate;
    @Column(name = "end_date") LocalDate endDate;
    String status = PerfRules.PLAN_DRAFT;
    @Column(name = "communicated_on") LocalDate communicatedOn;
    @Column(name = "closed_on") LocalDate closedOn;
    @Column(name = "outcome_note") String outcomeNote;
    @Column(name = "ack_at") Instant ackAt;
    @Column(name = "ack_by") UUID ackBy;
    @Column(name = "employee_comment") String employeeComment;
    @Column(name = "created_by") UUID createdBy;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;

    public static HrPerfPlan draft(UUID tenantId, UUID employeeId, UUID ownerId, UUID caseId, UUID reviewId, String title, String shortfall, String standard,
                                   String support, LocalDate start, LocalDate end, UUID by) {
        HrPerfPlan p = new HrPerfPlan();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.employeeId = employeeId;
        p.createdBy = by;
        p.createdAt = Instant.now();
        p.edit(ownerId, caseId, reviewId, title, shortfall, standard, support, start, end);
        return p;
    }

    public void edit(UUID ownerId, UUID caseId, UUID reviewId, String title, String shortfall, String standard, String support, LocalDate start, LocalDate end) {
        this.ownerId = ownerId;
        this.caseId = caseId;
        this.reviewId = reviewId;
        this.title = title.trim();
        this.shortfall = shortfall.trim();
        this.standard = standard.trim();
        this.support = support.trim();
        this.startDate = start;
        this.endDate = end;
        this.updatedAt = Instant.now();
    }

    public void start(LocalDate communicatedOn) { this.status = PerfRules.PLAN_ACTIVE; this.communicatedOn = communicatedOn; this.updatedAt = Instant.now(); }
    public void extend(LocalDate newEnd) { this.endDate = newEnd; this.updatedAt = Instant.now(); }
    public void linkCase(UUID caseId) { this.caseId = caseId; this.updatedAt = Instant.now(); }

    public void close(String outcome, String note, LocalDate on) { status = outcome; outcomeNote = note.trim(); closedOn = on; updatedAt = Instant.now(); }

    public void cancel(String reason, LocalDate on) { status = PerfRules.PLAN_CANCELLED; outcomeNote = reason.trim(); closedOn = on; updatedAt = Instant.now(); }

    public void acknowledge(UUID by, String comment) {
        ackAt = Instant.now();
        ackBy = by;
        employeeComment = comment == null || comment.isBlank() ? null : comment.trim();
        updatedAt = ackAt;
    }
}
