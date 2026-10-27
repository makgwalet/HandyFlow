package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    /** Money owed for an earlier period, paid this month. Part of the total earnings. */
    @Column(name = "back_pay_amount")   BigDecimal backPayAmount   = BigDecimal.ZERO;
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
    /** The medical tax credit actually taken off the month's PAYE. Null on payslips made before it was recorded. */
    @Column(name = "medical_tax_credit") BigDecimal medicalTaxCredit;
    /** Working days in the pay period and the days paid. Empty on payslips made before part-period pay existed, which were paid in full. */
    @Column(name = "days_in_period") @JdbcTypeCode(SqlTypes.SMALLINT) Integer daysInPeriod;
    @Column(name = "days_paid")            BigDecimal daysPaid;
    /** Salary not paid because the employee joined or left part way through the period. Already taken off the gross salary. */
    @Column(name = "proration_amount")     BigDecimal prorationAmount    = BigDecimal.ZERO;
    @Column(name = "unpaid_leave_days")    BigDecimal unpaidLeaveDays    = BigDecimal.ZERO;
    /** Salary not paid for approved unpaid leave. Already taken off the gross salary. */
    @Column(name = "unpaid_leave_amount")  BigDecimal unpaidLeaveAmount  = BigDecimal.ZERO;
    @Column(name = "leave_payout_days")    BigDecimal leavePayoutDays    = BigDecimal.ZERO;
    /** Annual leave paid out on the last pay. Part of the total earnings. */
    @Column(name = "leave_payout_amount")  BigDecimal leavePayoutAmount  = BigDecimal.ZERO;
    @Column(name = "final_pay")            boolean finalPay;
    /** Taxable benefits for the month that were not paid in cash. They raise the income PAYE is worked out on; they are not in the earnings. */
    @Column(name = "employer_medical")     BigDecimal employerMedical    = BigDecimal.ZERO;
    @Column(name = "employer_retirement")  BigDecimal employerRetirement = BigDecimal.ZERO;
    @Column(name = "other_fringe")         BigDecimal otherFringe        = BigDecimal.ZERO;
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
        p.backPayAmount   = BigDecimal.ZERO;
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

    /** What the gross salary was worked out from. Set before the calculations are applied. */
    public void setPeriodPay(int daysInPeriod, BigDecimal daysPaid, BigDecimal prorationAmount, BigDecimal unpaidLeaveDays, BigDecimal unpaidLeaveAmount) {
        this.daysInPeriod = daysInPeriod;
        this.daysPaid = daysPaid;
        this.prorationAmount = prorationAmount;
        this.unpaidLeaveDays = unpaidLeaveDays;
        this.unpaidLeaveAmount = unpaidLeaveAmount;
    }

    /** Annual leave paid out on the last pay. Set before the calculations are applied, which add it to the earnings. */
    public void setLeavePayout(BigDecimal days, BigDecimal amount) {
        this.leavePayoutDays = days == null ? BigDecimal.ZERO : days;
        this.leavePayoutAmount = amount == null ? BigDecimal.ZERO : amount;
    }

    public void markFinalPay() { this.finalPay = true; }

    /** What the employer paid towards medical aid and a retirement fund this month, and other taxable benefits. Set before the calculations are applied. */
    public void setBenefits(BigDecimal employerMedical, BigDecimal employerRetirement, BigDecimal otherFringe) {
        this.employerMedical = employerMedical == null ? BigDecimal.ZERO : employerMedical;
        this.employerRetirement = employerRetirement == null ? BigDecimal.ZERO : employerRetirement;
        this.otherFringe = otherFringe == null ? BigDecimal.ZERO : otherFringe;
    }

    public void setMedicalTaxCredit(BigDecimal credit) { this.medicalTaxCredit = credit; }

    /** A bonus and back pay paid this month. Set before the calculations are applied, which add them to the earnings. */
    public void setBonusAndBackPay(BigDecimal bonus, BigDecimal backPay) {
        this.bonusAmount = bonus == null ? BigDecimal.ZERO : bonus;
        this.backPayAmount = backPay == null ? BigDecimal.ZERO : backPay;
    }

    /** Approved overtime for the month. Set before the calculations are applied, which add it to the earnings. */
    public void setOvertimeAmount(BigDecimal amount) { this.overtimeAmount = amount == null ? BigDecimal.ZERO : amount; }

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
                .add(overtimeAmount).add(bonusAmount).add(backPayAmount)
                .add(travelAllowance).add(otherEarnings).add(leavePayoutAmount);

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
