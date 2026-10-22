package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A taxable benefit the employer provides: what it is worth each month, from when, and until when. Never edited; a change is a new line. */
@Entity
@Table(name = "hr_employee_benefits")
@Getter
@NoArgsConstructor
public class HrEmployeeBenefit {

    @Id UUID id;
    @Column(name = "tenant_id")      UUID tenantId;
    @Column(name = "employee_id")    UUID employeeId;
    String kind;
    @Column(name = "monthly_amount") BigDecimal monthlyAmount;
    @Column(name = "effective_from") LocalDate effectiveFrom;
    @Column(name = "effective_to")   LocalDate effectiveTo;
    String description;
    String reason;
    @Column(name = "created_by")     UUID createdBy;
    @Column(name = "created_at")     Instant createdAt;
    @Column(name = "ended_by")       UUID endedBy;
    @Column(name = "ended_at")       Instant endedAt;

    public static HrEmployeeBenefit of(UUID tenantId, UUID employeeId, String kind, BigDecimal amount, LocalDate from, LocalDate to,
                                       String description, String reason, UUID by) {
        HrEmployeeBenefit b = new HrEmployeeBenefit();
        b.id = UUID.randomUUID();
        b.tenantId = tenantId;
        b.employeeId = employeeId;
        b.kind = kind;
        b.monthlyAmount = amount;
        b.effectiveFrom = from;
        b.effectiveTo = to;
        b.description = description == null || description.isBlank() ? null : description.trim();
        b.reason = reason.trim();
        b.createdBy = by;
        b.createdAt = Instant.now();
        return b;
    }

    public void end(LocalDate lastDay, UUID by) {
        this.effectiveTo = lastDay;
        this.endedBy = by;
        this.endedAt = Instant.now();
    }

    public BenefitRules.Line asLine() { return new BenefitRules.Line(kind, monthlyAmount, effectiveFrom, effectiveTo); }
}
