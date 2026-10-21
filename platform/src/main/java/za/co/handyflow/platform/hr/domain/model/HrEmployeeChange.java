package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * A request to change an employee's pay or bank details. The employee row changes only when someone other than the person
 * who asked approves it and its effective date has come. Old and new values are kept as asked, as the record of the change.
 */
@Entity
@Table(name = "hr_employee_changes")
@Getter
@NoArgsConstructor
public class HrEmployeeChange {

    @Id UUID id;
    @Column(name = "tenant_id")   UUID tenantId;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "change_type") String changeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    Map<String, String> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    Map<String, String> newValues;

    @Column(name = "effective_from") LocalDate effectiveFrom;
    String reason;
    String status = EmployeeChangeRules.PENDING;
    @Column(name = "requested_by")  UUID requestedBy;
    @Column(name = "requested_at")  Instant requestedAt;
    @Column(name = "decided_by")    UUID decidedBy;
    @Column(name = "decided_at")    Instant decidedAt;
    @Column(name = "decision_note") String decisionNote;
    @Column(name = "self_approved") boolean selfApproved;
    @Column(name = "applied_at")    Instant appliedAt;

    public static HrEmployeeChange request(UUID tenantId, UUID employeeId, String type, Map<String, String> oldValues,
                                           Map<String, String> newValues, LocalDate effectiveFrom, String reason, UUID actor) {
        HrEmployeeChange c = new HrEmployeeChange();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.employeeId = employeeId;
        c.changeType = type;
        c.oldValues = oldValues;
        c.newValues = newValues;
        c.effectiveFrom = effectiveFrom;
        c.reason = reason.trim();
        c.status = EmployeeChangeRules.PENDING;
        c.requestedBy = actor;
        c.requestedAt = Instant.now();
        return c;
    }

    public void approve(UUID actor, String note, boolean self) {
        this.status = EmployeeChangeRules.APPROVED;
        this.decidedBy = actor;
        this.decidedAt = Instant.now();
        this.decisionNote = note == null || note.isBlank() ? null : note.trim();
        this.selfApproved = self;
    }

    public void reject(UUID actor, String note) {
        this.status = EmployeeChangeRules.REJECTED;
        this.decidedBy = actor;
        this.decidedAt = Instant.now();
        this.decisionNote = note.trim();
    }

    public void cancel(UUID actor) {
        this.status = EmployeeChangeRules.CANCELLED;
        this.decidedBy = actor;
        this.decidedAt = Instant.now();
    }

    public void markApplied() {
        this.status = EmployeeChangeRules.APPLIED;
        this.appliedAt = Instant.now();
    }
}
