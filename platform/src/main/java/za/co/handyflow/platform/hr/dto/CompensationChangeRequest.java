package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CompensationChangeRequest(
        BigDecimal grossSalary, BigDecimal travelAllowance, BigDecimal medicalAidContribution,
        BigDecimal pensionContribution, String payFrequency, LocalDate effectiveFrom, String reason
) {}
