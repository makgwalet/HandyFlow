package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What a recalculation under the approved statutory rules would change in payslips already issued. Read-only: nothing
 * in the payroll data is changed by producing it.
 */
public record PayrollImpactResponse(
        Instant generatedAt,
        List<Run> runs,
        int runsWithDifferences,
        List<String> notes
) {
    public record Run(
            UUID runId,
            String runNumber,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate payDate,
            Integer recordedTaxYear,
            int correctTaxYear,
            String status,
            String emp201Status,
            int payslips,
            int noDifference,
            int potentialDifference,
            int confirmedDifference,
            int reviewRequired,
            BigDecimal recordedPaye,
            BigDecimal recalculatedPaye,
            BigDecimal payeDifference,
            String verdict,
            String action,
            /** Filled only when one run was asked for. */
            List<Line> lines
    ) {}

    public record Line(
            UUID payslipId,
            UUID employeeId,
            String employeeName,
            String employeeNumber,
            String verdict,
            BigDecimal recordedPaye,
            BigDecimal recalculatedPaye,
            BigDecimal payeDifference,
            BigDecimal uifDifference,
            BigDecimal sdlDifference,
            Integer medicalMembers,
            boolean medicalAssumed,
            List<String> reasons
    ) {}
}
