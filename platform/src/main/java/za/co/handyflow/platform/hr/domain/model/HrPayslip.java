package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hr_payslips")
@Getter
@NoArgsConstructor
public class HrPayslip {

    @Id UUID id;
    @Column(name = "tenant_id")    UUID tenantId;
    @Column(name = "pay_run_id")   UUID payRunId;
    @Column(name = "employee_id")  UUID employeeId;
    @Column(name = "gross_salary")      BigDecimal grossSalary;
    @Column(name = "overtime_amount")   BigDecimal overtimeAmount  = BigDecimal.ZERO;
    @Column(name = "bonus_amount")      BigDecimal bonusAmount     = BigDecimal.ZERO;
    @Column(name = "travel_allowance")  BigDecimal travelAllowance = BigDecimal.ZERO;
    @Column(name = "other_earnings")    BigDecimal otherEarnings   = BigDecimal.ZERO;
    @Column(name = "total_earnings")    BigDecimal totalEarnings;
    @Column(name = "paye_amount")       BigDecimal payeAmount      = BigDecimal.ZERO;
    @Column(name = "uif_employee")      BigDecimal uifEmployee     = BigDecimal.ZERO;
    @Column(name = "medical_aid")       BigDecimal medicalAid      = BigDecimal.ZERO;
    @Column(name = "pension")           BigDecimal pension         = BigDecimal.ZERO;
    @Column(name = "other_deductions")  BigDecimal otherDeductions = BigDecimal.ZERO;
    @Column(name = "total_deductions")  BigDecimal totalDeductions;
    @Column(name = "uif_employer")      BigDecimal uifEmployer     = BigDecimal.ZERO;
    @Column(name = "sdl_amount")        BigDecimal sdlAmount       = BigDecimal.ZERO;
    @Column(name = "net_pay")           BigDecimal netPay;
    @Column(name = "ytd_gross")         BigDecimal ytdGross        = BigDecimal.ZERO;
    @Column(name = "ytd_paye")          BigDecimal ytdPaye         = BigDecimal.ZERO;
    @Column(name = "ytd_uif")           BigDecimal ytdUif          = BigDecimal.ZERO;
    @Column(name = "taxable_income")    BigDecimal taxableIncome;
    @Column(name = "tax_before_rebate") BigDecimal taxBeforeRebate;
    @Column(name = "primary_rebate")    BigDecimal primaryRebate;
    @Column(name = "tax_year")          Integer taxYear;
    @Column(name = "created_at")        Instant createdAt;

    public static HrPayslip create(TenantId tenantId, UUID payRunId,
                                   UUID employeeId, BigDecimal grossSalary,
                                   BigDecimal travelAllowance,
                                   BigDecimal medicalAid, BigDecimal pension) {
        HrPayslip p = new HrPayslip();
        p.id              = UUID.randomUUID();
        p.tenantId        = tenantId.getValue();
        p.payRunId        = payRunId;
        p.employeeId      = employeeId;
        p.grossSalary     = grossSalary;
        p.overtimeAmount  = BigDecimal.ZERO;
        p.bonusAmount     = BigDecimal.ZERO;
        p.travelAllowance = travelAllowance != null ? travelAllowance : BigDecimal.ZERO;
        p.otherEarnings   = BigDecimal.ZERO;
        p.medicalAid      = medicalAid != null ? medicalAid : BigDecimal.ZERO;
        p.pension         = pension    != null ? pension    : BigDecimal.ZERO;
        p.otherDeductions = BigDecimal.ZERO;
        p.ytdGross        = BigDecimal.ZERO;
        p.ytdPaye         = BigDecimal.ZERO;
        p.ytdUif          = BigDecimal.ZERO;
        p.createdAt       = Instant.now();
        return p;
    }

    public void applyCalculations(BigDecimal payeAmount, BigDecimal uifEmployee,
                                  BigDecimal uifEmployer, BigDecimal sdlAmount,
                                  BigDecimal taxableIncome, BigDecimal taxBeforeRebate,
                                  BigDecimal primaryRebate, int taxYear) {
        this.payeAmount      = payeAmount;
        this.uifEmployee     = uifEmployee;
        this.uifEmployer     = uifEmployer;
        this.sdlAmount       = sdlAmount;
        this.taxableIncome   = taxableIncome;
        this.taxBeforeRebate = taxBeforeRebate;
        this.primaryRebate   = primaryRebate;
        this.taxYear         = taxYear;

        this.totalEarnings = grossSalary
                .add(overtimeAmount).add(bonusAmount)
                .add(travelAllowance).add(otherEarnings);

        this.totalDeductions = payeAmount
                .add(uifEmployee).add(medicalAid)
                .add(pension).add(otherDeductions);

        this.netPay = totalEarnings.subtract(totalDeductions);
    }

    // ── YTD setters — required by PayrollService.processPayRun() ─────────────
    // These replace the reflection-based field setting that was silently failing.
    // @Getter is class-level; setters must be explicit for the fields that need them.

    public void setYtdGross(BigDecimal ytdGross) { this.ytdGross = ytdGross; }
    public void setYtdPaye(BigDecimal ytdPaye)   { this.ytdPaye  = ytdPaye;  }
    public void setYtdUif(BigDecimal ytdUif)     { this.ytdUif   = ytdUif;   }
}
