package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PayslipResponse(
        UUID id, UUID payEmployeeId, String employeeName, String employeeNumber,
        BigDecimal grossSalary, BigDecimal travelAllowance, BigDecimal totalEarnings,
        BigDecimal payeAmount, BigDecimal uifEmployee, BigDecimal uifEmployer, BigDecimal sdlAmount,
        BigDecimal medicalAid, BigDecimal pension, BigDecimal totalDeductions, BigDecimal netPay,
        BigDecimal taxableIncome, int taxYear
) {}