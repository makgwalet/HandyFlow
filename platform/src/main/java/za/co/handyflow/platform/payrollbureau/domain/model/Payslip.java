package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One employee's payslip within a PayRun. Mirrors HrPayslip's fields. */
@Entity
@Table(name = "pay_payslips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payslip {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_run_id", nullable = false)
    private UUID payRunId;

    @Column(name = "pay_employee_id", nullable = false)
    private UUID payEmployeeId;

    @Column(name = "gross_salary", precision = 15, scale = 2)
    private BigDecimal grossSalary;

    @Column(name = "travel_allowance", precision = 15, scale = 2)
    private BigDecimal travelAllowance;

    @Column(name = "total_earnings", precision = 15, scale = 2)
    private BigDecimal totalEarnings;

    @Column(name = "paye_amount", precision = 15, scale = 2)
    private BigDecimal payeAmount;

    @Column(name = "uif_employee", precision = 15, scale = 2)
    private BigDecimal uifEmployee;

    @Column(name = "uif_employer", precision = 15, scale = 2)
    private BigDecimal uifEmployer;

    @Column(name = "sdl_amount", precision = 15, scale = 2)
    private BigDecimal sdlAmount;

    @Column(name = "medical_aid", precision = 15, scale = 2)
    private BigDecimal medicalAid;

    @Column(name = "pension", precision = 15, scale = 2)
    private BigDecimal pension;

    @Column(name = "total_deductions", precision = 15, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "net_pay", precision = 15, scale = 2)
    private BigDecimal netPay;

    @Column(name = "taxable_income", precision = 15, scale = 2)
    private BigDecimal taxableIncome;

    @Column(name = "tax_year")
    private int taxYear;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Payslip create(UUID tenantId, UUID payRunId, UUID payEmployeeId,
                                 BigDecimal grossSalary, BigDecimal travelAllowance,
                                 BigDecimal payeAmount, BigDecimal uifEmployee, BigDecimal uifEmployer,
                                 BigDecimal sdlAmount, BigDecimal medicalAid, BigDecimal pension,
                                 BigDecimal taxableIncome, int taxYear) {
        Payslip p = new Payslip();
        p.tenantId = tenantId;
        p.payRunId = payRunId;
        p.payEmployeeId = payEmployeeId;
        p.grossSalary = grossSalary;
        p.travelAllowance = travelAllowance;
        p.totalEarnings = grossSalary.add(travelAllowance);
        p.payeAmount = payeAmount;
        p.uifEmployee = uifEmployee;
        p.uifEmployer = uifEmployer;
        p.sdlAmount = sdlAmount;
        p.medicalAid = medicalAid;
        p.pension = pension;
        p.totalDeductions = payeAmount.add(uifEmployee).add(medicalAid).add(pension);
        p.netPay = p.totalEarnings.subtract(p.totalDeductions);
        p.taxableIncome = taxableIncome;
        p.taxYear = taxYear;
        p.createdAt = Instant.now();
        return p;
    }
}