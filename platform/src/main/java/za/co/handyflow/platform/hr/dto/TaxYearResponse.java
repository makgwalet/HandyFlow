package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One tax year's statutory rules and whether payroll may use them. {@code taxYear} is the year it ends, as SARS names it. */
public record TaxYearResponse(
        int taxYear,
        String label,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        boolean usable,
        List<String> problems,
        int bracketCount,
        BigDecimal primaryRebate,
        BigDecimal secondaryRebate,
        BigDecimal tertiaryRebate,
        BigDecimal mtcTaxpayer,
        BigDecimal mtcFirstDependant,
        BigDecimal mtcAdditional,
        BigDecimal uifMonthlyCeiling,
        BigDecimal retirementCapAmount,
        BigDecimal retirementCapPct,
        String sourceNote,
        Instant approvedAt,
        String approvalNote
) {}
