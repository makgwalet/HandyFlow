package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Overtime worked on one day by one employee, and what it is worth. The rate and amount are fixed when the claim is made. */
@Entity
@Table(name = "hr_overtime_claims")
@Getter
@NoArgsConstructor
public class HrOvertimeClaim {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID tenantId;
    @Column(name = "employee_id")   UUID employeeId;
    @Column(name = "work_date")     LocalDate workDate;
    String kind;
    int minutes;
    BigDecimal multiplier;
    @Column(name = "hourly_rate")   BigDecimal hourlyRate;
    BigDecimal amount;
    String status = OvertimeRules.PENDING;
    @Column(name = "requested_by")  UUID requestedBy;
    @Column(name = "decided_by")    UUID decidedBy;
    @Column(name = "decided_at")    Instant decidedAt;
    @Column(name = "decision_note") String decisionNote;
    @Column(name = "pay_run_id")    UUID payRunId;
    @Column(name = "created_at")    Instant createdAt;
    @Column(name = "updated_at")    Instant updatedAt;
    @Version long version;

    public static HrOvertimeClaim of(UUID tenantId, UUID employeeId, LocalDate date, String kind, int minutes, BigDecimal multiplier,
                                     BigDecimal hourlyRate, BigDecimal amount, UUID by) {
        HrOvertimeClaim c = new HrOvertimeClaim();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId;
        c.employeeId = employeeId;
        c.workDate = date;
        c.createdAt = Instant.now();
        c.requestedBy = by;
        c.reprice(kind, minutes, multiplier, hourlyRate, amount);
        return c;
    }

    /** Takes the new hours and value and goes back to waiting. */
    public void reprice(String kind, int minutes, BigDecimal multiplier, BigDecimal hourlyRate, BigDecimal amount) {
        this.kind = kind;
        this.minutes = minutes;
        this.multiplier = multiplier;
        this.hourlyRate = hourlyRate;
        this.amount = amount;
        this.status = OvertimeRules.PENDING;
        this.decidedBy = null;
        this.decidedAt = null;
        this.decisionNote = null;
        this.updatedAt = Instant.now();
    }

    public void approve(UUID by, String note) { decide(OvertimeRules.APPROVED, by, note); }
    public void reject(UUID by, String note)  { decide(OvertimeRules.REJECTED, by, note); }
    public void withdraw()                    { this.status = OvertimeRules.WITHDRAWN; this.updatedAt = Instant.now(); }

    public void linkTo(UUID payRunId) { this.payRunId = payRunId; this.updatedAt = Instant.now(); }

    private void decide(String status, UUID by, String note) {
        this.status = status;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note == null || note.isBlank() ? null : note.trim();
        this.updatedAt = Instant.now();
    }
}
