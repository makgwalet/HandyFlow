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
        Instant createdAt,
        Integer daysInPeriod,
        BigDecimal daysPaid,
        BigDecimal prorationAmount,
        BigDecimal unpaidLeaveDays,
        BigDecimal unpaidLeaveAmount,
        BigDecimal leavePayoutDays,
        BigDecimal leavePayoutAmount,
        boolean finalPay,
        BigDecimal employerMedical,
        BigDecimal employerRetirement,
        BigDecimal otherFringe,
        BigDecimal backPayAmount,
        BigDecimal noticePayAmount,
        BigDecimal etiAmount,
        Integer etiMonth
) {
    /** Without the employment tax incentive. */
    public PayslipResponse(UUID id, UUID employeeId, String employeeName, String employeeNumber, UUID payRunId, String payRunNumber, BigDecimal grossSalary, BigDecimal overtimeAmount, BigDecimal bonusAmount, BigDecimal travelAllowance, BigDecimal totalEarnings, BigDecimal payeAmount, BigDecimal uifEmployee, BigDecimal medicalAid, BigDecimal pension, BigDecimal totalDeductions, BigDecimal uifEmployer, BigDecimal sdlAmount, BigDecimal netPay, BigDecimal ytdGross, BigDecimal ytdPaye, BigDecimal taxableIncome, int taxYear, Instant createdAt, Integer daysInPeriod, BigDecimal daysPaid, BigDecimal prorationAmount, BigDecimal unpaidLeaveDays, BigDecimal unpaidLeaveAmount, BigDecimal leavePayoutDays, BigDecimal leavePayoutAmount, boolean finalPay, BigDecimal employerMedical, BigDecimal employerRetirement, BigDecimal otherFringe, BigDecimal backPayAmount, BigDecimal noticePayAmount) {
        this(id, employeeId, employeeName, employeeNumber, payRunId, payRunNumber, grossSalary, overtimeAmount, bonusAmount, travelAllowance, totalEarnings, payeAmount, uifEmployee, medicalAid, pension, totalDeductions, uifEmployer, sdlAmount, netPay, ytdGross, ytdPaye, taxableIncome, taxYear, createdAt, daysInPeriod, daysPaid, prorationAmount, unpaidLeaveDays, unpaidLeaveAmount, leavePayoutDays, leavePayoutAmount, finalPay, employerMedical, employerRetirement, otherFringe, backPayAmount, noticePayAmount, BigDecimal.ZERO, null);
    }
}
