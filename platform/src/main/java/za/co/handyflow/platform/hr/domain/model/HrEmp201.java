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
@Table(name = "hr_emp201")
@Getter
@NoArgsConstructor
public class HrEmp201 {

    @Id UUID id;
    @Column(name = "tenant_id")    UUID tenantId;
    @Column(name = "pay_run_id")   UUID payRunId;
    @Column(name = "period_start") LocalDate periodStart;
    @Column(name = "period_end")   LocalDate periodEnd;
    @Column(name = "due_date")     LocalDate dueDate;
    @Column(name = "total_paye")    BigDecimal totalPaye;
    @Column(name = "total_uif")     BigDecimal totalUif;
    @Column(name = "total_sdl")     BigDecimal totalSdl;
    @Column(name = "total_payable") BigDecimal totalPayable;
    String status = "DRAFT";
    @Column(name = "submitted_at") Instant submittedAt;
    @Column(name = "payment_ref")  String paymentRef;
    @Column(name = "submitted_by") UUID submittedBy;
    @Column(name = "paid_on")      LocalDate paidOn;
    @Column(name = "paid_amount")  BigDecimal paidAmount;
    @Column(name = "paid_by")      UUID paidBy;
    @Column(name = "paid_at")      Instant paidAt;
    @Column(name = "created_at")   Instant createdAt;
    @Column(name = "updated_at")   Instant updatedAt;

    public static HrEmp201 create(TenantId tenantId, UUID payRunId,
                                  LocalDate periodStart, LocalDate periodEnd,
                                  BigDecimal totalPaye, BigDecimal totalUif,
                                  BigDecimal totalSdl) {
        HrEmp201 e = new HrEmp201();
        e.id           = UUID.randomUUID();
        e.tenantId     = tenantId.getValue();
        e.payRunId     = payRunId;
        e.periodStart  = periodStart;
        e.periodEnd    = periodEnd;
        e.dueDate      = Emp201Rules.dueDate(periodEnd);
        e.totalPaye    = totalPaye;
        e.totalUif     = totalUif;
        e.totalSdl     = totalSdl;
        e.totalPayable = totalPaye.add(totalUif).add(totalSdl);
        e.status       = "DRAFT";
        e.createdAt    = Instant.now();
        e.updatedAt    = Instant.now();
        return e;
    }

    public void markSubmitted(String paymentRef, UUID by) {
        this.status      = Emp201Rules.SUBMITTED;
        this.paymentRef  = Emp201Rules.cleanReference(paymentRef);
        this.submittedAt = Instant.now();
        this.submittedBy = by;
        this.updatedAt   = Instant.now();
    }

    public void markPaid(LocalDate paidOn, BigDecimal amount, UUID by) {
        this.status     = Emp201Rules.PAID;
        this.paidOn     = paidOn;
        this.paidAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        this.paidBy     = by;
        this.paidAt     = Instant.now();
        this.updatedAt  = Instant.now();
    }
}
