package za.co.handyflow.platform.payrollbureau.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayEmployeeChangeResponse(
        UUID id, UUID payEmployeeId, String employeeName, String status,
        BigDecimal prevGrossSalary, BigDecimal newGrossSalary,
        BigDecimal prevTravelAllowance, BigDecimal newTravelAllowance,
        BigDecimal prevPension, BigDecimal newPension,
        BigDecimal prevMedicalAid, BigDecimal newMedicalAid,
        String prevBankName, String newBankName,
        String prevBankAccount, String newBankAccount,
        String prevBankBranch, String newBankBranch,
        UUID requestedBy, Instant requestedAt, UUID decidedBy, Instant decidedAt, String decisionNote
) {}
