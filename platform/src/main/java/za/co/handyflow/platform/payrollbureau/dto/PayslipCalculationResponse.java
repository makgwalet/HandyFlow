package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** What a payslip was calculated from. All the rules and input fields are null for payslips made before the record was kept. */
public record PayslipCalculationResponse(
        UUID payslipId, int taxYear, boolean recorded,
        LocalDate rulesEffectiveFrom, LocalDate rulesEffectiveTo, String rulesFingerprint, String formulaVersion,
        BigDecimal grossSalary, BigDecimal travelAllowance, BigDecimal pension, BigDecimal medicalAid,
        LocalDate dateOfBirth, Integer medicalMembers, BigDecimal annualPayroll
) {}
