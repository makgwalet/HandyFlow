package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayslipResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        String employeeNumber,
        UUID payRunId,
        String payRunNumber,
        BigDecimal grossSalary,
        BigDecimal overtimeAmount,
        BigDecimal bonusAmount,
        BigDecimal travelAllowance,
        BigDecimal totalEarnings,
        BigDecimal payeAmount,
        BigDecimal uifEmployee,
        BigDecimal medicalAid,
        BigDecimal pension,
        BigDecimal totalDeductions,
        BigDecimal uifEmployer,
        BigDecimal sdlAmount,
        BigDecimal netPay,
        BigDecimal ytdGross,
        BigDecimal ytdPaye,
        BigDecimal taxableIncome,
        int taxYear,
        Instant createdAt
) {}